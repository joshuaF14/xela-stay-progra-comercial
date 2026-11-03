package com.xelastay.booking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import com.xelastay.booking.dto.BookingResponse;
import com.xelastay.booking.dto.BookingResult;
import com.xelastay.booking.dto.CreateBookingRequest;
import com.xelastay.common.PageResponse;
import com.xelastay.events.BookingEventPayload;
import com.xelastay.events.EventTypes;
import com.xelastay.events.OutboxService;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Reservas: creación con bloqueo transaccional de la propiedad, transiciones de estado y consultas.
 * Cada cambio de estado escribe su evento en el outbox dentro de la misma transacción.
 */
@Service
public class BookingService {

    /** Zona para decidir qué es "hoy" al validar fechas de entrada. */
    static final ZoneId ZONE = ZoneId.of("America/Guatemala");
    static final int MAX_PAGE_SIZE = 100;

    private final BookingRepository bookings;
    private final AvailabilityBlockRepository blocks;
    private final PropertyReader properties;
    private final OutboxService outbox;

    public BookingService(BookingRepository bookings, AvailabilityBlockRepository blocks, PropertyReader properties,
            OutboxService outbox) {
        this.bookings = bookings;
        this.blocks = blocks;
        this.properties = properties;
        this.outbox = outbox;
    }

    @Transactional
    public BookingResult create(UUID guestId, CreateBookingRequest request) {
        LocalDate checkIn = request.checkIn();
        LocalDate checkOut = request.checkOut();
        if (!checkOut.isAfter(checkIn)) {
            throw error(HttpStatus.BAD_REQUEST, "La fecha de salida debe ser posterior a la de entrada");
        }

        // Bloqueo de la propiedad: las reservas simultáneas del mismo alojamiento se procesan en fila.
        PropertyInfo property = properties.lockPublished(request.propertyId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Alojamiento no encontrado o no disponible"));

        String idempotencyKey = blankToNull(request.idempotencyKey());
        if (idempotencyKey != null) {
            Optional<Booking> previous = bookings.findByGuestIdAndIdempotencyKey(guestId, idempotencyKey);
            if (previous.isPresent()) {
                Booking existing = previous.get();
                if (!existing.sameRequest(request.propertyId(), checkIn, checkOut, request.guestsCount())) {
                    throw error(HttpStatus.CONFLICT, "La clave de idempotencia ya se usó para otra reserva");
                }
                return new BookingResult(BookingResponse.from(existing, property), false);
            }
        }

        if (checkIn.isBefore(LocalDate.now(ZONE))) {
            throw error(HttpStatus.BAD_REQUEST, "La fecha de entrada no puede ser anterior a hoy");
        }
        if (property.hostId().equals(guestId)) {
            throw error(HttpStatus.FORBIDDEN, "No puedes reservar tu propio alojamiento");
        }
        if (request.guestsCount() > property.capacity()) {
            throw error(HttpStatus.BAD_REQUEST,
                    "El alojamiento admite máximo " + property.capacity() + " huéspedes");
        }
        if (bookings.countOverlapping(property.id(), BookingStatus.ACTIVE, checkIn, checkOut) > 0) {
            throw error(HttpStatus.CONFLICT, "Las fechas elegidas ya están reservadas");
        }
        if (!blocks.findOverlapping(property.id(), checkIn, checkOut).isEmpty()) {
            throw error(HttpStatus.CONFLICT, "El anfitrión bloqueó algunas de las fechas elegidas");
        }

        BigDecimal nightlyPrice = property.pricePerNight().setScale(2, RoundingMode.HALF_UP);
        long nights = checkOut.toEpochDay() - checkIn.toEpochDay();
        BigDecimal subtotal = nightlyPrice.multiply(BigDecimal.valueOf(nights)).setScale(2, RoundingMode.HALF_UP);
        BigDecimal fees = BigDecimal.ZERO.setScale(2);

        Booking booking = Booking.pending(property.id(), guestId, checkIn, checkOut, request.guestsCount(),
                nightlyPrice, subtotal, fees, property.currency(), idempotencyKey);
        try {
            bookings.saveAndFlush(booking);
        } catch (DataIntegrityViolationException ex) {
            // Clave de idempotencia repetida en una carrera, o huésped sin fila en profiles (FK).
            throw error(HttpStatus.CONFLICT,
                    "No se pudo registrar la reserva: la clave de idempotencia ya se usó o tu perfil aún no existe");
        }
        publish(booking, property, "CREATED", EventTypes.BOOKING_CREATED);
        return new BookingResult(BookingResponse.from(booking, property), true);
    }

    @Transactional
    public BookingResponse confirm(UUID userId, UUID bookingId) {
        Booking booking = lock(bookingId);
        PropertyInfo property = propertyOf(booking);
        requireHost(property, userId, "Solo el anfitrión puede confirmar la reserva");
        requireStatus(booking, Set.of(BookingStatus.PENDING), "Solo se pueden confirmar reservas pendientes");
        booking.confirm();
        publish(booking, property, "CONFIRMED", EventTypes.BOOKING_CONFIRMED);
        return BookingResponse.from(booking, property);
    }

    @Transactional
    public BookingResponse reject(UUID userId, UUID bookingId, String reason) {
        Booking booking = lock(bookingId);
        PropertyInfo property = propertyOf(booking);
        requireHost(property, userId, "Solo el anfitrión puede rechazar la reserva");
        requireStatus(booking, Set.of(BookingStatus.PENDING), "Solo se pueden rechazar reservas pendientes");
        booking.reject(blankToNull(reason));
        publish(booking, property, "REJECTED", EventTypes.BOOKING_REJECTED);
        return BookingResponse.from(booking, property);
    }

    @Transactional
    public BookingResponse cancel(UUID userId, UUID bookingId, String reason) {
        Booking booking = lock(bookingId);
        PropertyInfo property = propertyOf(booking);
        if (!booking.getGuestId().equals(userId) && !property.hostId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "Solo el huésped o el anfitrión pueden cancelar la reserva");
        }
        requireStatus(booking, BookingStatus.ACTIVE, "Solo se pueden cancelar reservas pendientes o confirmadas");
        String cleanReason = blankToNull(reason);
        if (cleanReason == null) {
            throw error(HttpStatus.BAD_REQUEST, "El motivo de cancelación es obligatorio");
        }
        booking.cancel(cleanReason);
        publish(booking, property, "CANCELLED", EventTypes.BOOKING_CANCELLED);
        return BookingResponse.from(booking, property);
    }

    @Transactional
    public BookingResponse complete(UUID userId, UUID bookingId) {
        Booking booking = lock(bookingId);
        PropertyInfo property = propertyOf(booking);
        requireHost(property, userId, "Solo el anfitrión puede marcar la reserva como completada");
        requireStatus(booking, Set.of(BookingStatus.CONFIRMED), "Solo se pueden completar reservas confirmadas");
        booking.complete();
        publish(booking, property, "COMPLETED", EventTypes.BOOKING_COMPLETED);
        return BookingResponse.from(booking, property);
    }

    @Transactional(readOnly = true)
    public BookingResponse get(UUID userId, UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
        PropertyInfo property = propertyOf(booking);
        if (!booking.getGuestId().equals(userId) && !property.hostId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "No tienes acceso a esta reserva");
        }
        return BookingResponse.from(booking, property);
    }

    /** Reservas que hizo el usuario como huésped, de la más reciente a la más antigua. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listAsGuest(UUID guestId, BookingStatus status, int page, int size) {
        Pageable pageable = pageable(page, size);
        Page<Booking> result = status == null
                ? bookings.findByGuestId(guestId, pageable)
                : bookings.findByGuestIdAndStatus(guestId, status, pageable);
        return toPage(result);
    }

    /** Reservas recibidas en los alojamientos del usuario como anfitrión. */
    @Transactional(readOnly = true)
    public PageResponse<BookingResponse> listAsHost(UUID hostId, BookingStatus status, int page, int size) {
        Pageable pageable = pageable(page, size);
        List<UUID> propertyIds = properties.findIdsByHost(hostId);
        if (propertyIds.isEmpty()) {
            return new PageResponse<>(List.of(), pageable.getPageNumber(), pageable.getPageSize(), 0, 0);
        }
        Page<Booking> result = status == null
                ? bookings.findByPropertyIdIn(propertyIds, pageable)
                : bookings.findByPropertyIdInAndStatus(propertyIds, status, pageable);
        return toPage(result);
    }

    private PageResponse<BookingResponse> toPage(Page<Booking> result) {
        Map<UUID, PropertyInfo> byId = properties.findByIds(
                result.getContent().stream().map(Booking::getPropertyId).collect(Collectors.toSet()));
        List<BookingResponse> content = result.getContent().stream()
                .map(b -> BookingResponse.from(b, byId.get(b.getPropertyId())))
                .toList();
        return PageResponse.of(result, content);
    }

    private Booking lock(UUID bookingId) {
        return bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
    }

    private PropertyInfo propertyOf(Booking booking) {
        return properties.findById(booking.getPropertyId())
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Alojamiento de la reserva no encontrado"));
    }

    private void publish(Booking booking, PropertyInfo property, String action, String eventType) {
        BookingEventPayload payload = new BookingEventPayload(booking.getId(), booking.getPropertyId(),
                property.title(), booking.getGuestId(), property.hostId(), booking.getCheckIn(),
                booking.getCheckOut(), booking.getStatus().name(), booking.getCancellationReason());
        outbox.enqueue(OutboxService.eventKey(EventTypes.BOOKING_AGGREGATE, booking.getId(), action),
                EventTypes.BOOKING_AGGREGATE, booking.getId(), eventType, payload);
    }

    private static void requireHost(PropertyInfo property, UUID userId, String message) {
        if (!property.hostId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, message);
        }
    }

    private static void requireStatus(Booking booking, Set<BookingStatus> allowed, String message) {
        if (!allowed.contains(booking.getStatus())) {
            throw error(HttpStatus.CONFLICT, message + " (estado actual: " + booking.getStatus() + ")");
        }
    }

    static Pageable pageable(int page, int size) {
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        return PageRequest.of(Math.max(page, 0), safeSize, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}

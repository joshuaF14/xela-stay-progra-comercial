package com.xelastay.booking;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.TreeSet;
import java.util.UUID;

import com.xelastay.booking.dto.AvailabilityBlockRequest;
import com.xelastay.booking.dto.AvailabilityBlockResponse;
import com.xelastay.booking.dto.AvailabilityResponse;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Calendario público de fechas ocupadas y bloqueos de disponibilidad del anfitrión. */
@Service
public class AvailabilityService {

    static final int DEFAULT_RANGE_DAYS = 90;
    static final int MAX_RANGE_DAYS = 366;

    private final BookingRepository bookings;
    private final AvailabilityBlockRepository blocks;
    private final PropertyReader properties;

    public AvailabilityService(BookingRepository bookings, AvailabilityBlockRepository blocks,
            PropertyReader properties) {
        this.bookings = bookings;
        this.blocks = blocks;
        this.properties = properties;
    }

    /**
     * Noches ocupadas (reservas PENDING/CONFIRMED y bloqueos) entre {@code from} y {@code to}, ambas incluidas.
     * Sin {@code from} se usa hoy; sin {@code to}, {@value #DEFAULT_RANGE_DAYS} días después de {@code from}.
     */
    @Transactional(readOnly = true)
    public AvailabilityResponse occupiedDates(UUID propertyId, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : LocalDate.now(BookingService.ZONE);
        LocalDate end = to != null ? to : start.plusDays(DEFAULT_RANGE_DAYS);
        if (end.isBefore(start)) {
            throw BookingService.error(HttpStatus.BAD_REQUEST, "La fecha 'to' no puede ser anterior a 'from'");
        }
        if (ChronoUnit.DAYS.between(start, end) > MAX_RANGE_DAYS) {
            throw BookingService.error(HttpStatus.BAD_REQUEST,
                    "El rango máximo es de " + MAX_RANGE_DAYS + " días");
        }
        properties.findPublished(propertyId)
                .orElseThrow(() -> BookingService.error(HttpStatus.NOT_FOUND, "Alojamiento no encontrado"));

        LocalDate endExclusive = end.plusDays(1);
        TreeSet<LocalDate> occupied = new TreeSet<>();
        for (Booking booking : bookings.findOverlapping(propertyId, BookingStatus.ACTIVE, start, endExclusive)) {
            addNights(occupied, booking.getCheckIn(), booking.getCheckOut(), start, endExclusive);
        }
        for (AvailabilityBlock block : blocks.findOverlapping(propertyId, start, endExclusive)) {
            addNights(occupied, block.getStartDate(), block.getEndDate(), start, endExclusive);
        }
        return new AvailabilityResponse(propertyId, start, end, List.copyOf(occupied));
    }

    @Transactional(readOnly = true)
    public List<AvailabilityBlockResponse> listBlocks(UUID hostId, UUID propertyId) {
        PropertyInfo property = properties.findActive(propertyId)
                .orElseThrow(() -> BookingService.error(HttpStatus.NOT_FOUND, "Alojamiento no encontrado"));
        requireOwner(property, hostId);
        return blocks.findByPropertyIdOrderByStartDateAsc(propertyId).stream()
                .map(AvailabilityBlockResponse::from)
                .toList();
    }

    @Transactional
    public AvailabilityBlockResponse createBlock(UUID hostId, UUID propertyId, AvailabilityBlockRequest request) {
        PropertyInfo property = lockOwned(hostId, propertyId);
        validateRange(property, request, null);
        AvailabilityBlock block = AvailabilityBlock.create(propertyId, request.startDate(), request.endDate(),
                blankToNull(request.reason()), hostId);
        return AvailabilityBlockResponse.from(blocks.save(block));
    }

    @Transactional
    public AvailabilityBlockResponse updateBlock(UUID hostId, UUID propertyId, UUID blockId,
            AvailabilityBlockRequest request) {
        PropertyInfo property = lockOwned(hostId, propertyId);
        AvailabilityBlock block = findBlock(propertyId, blockId);
        validateRange(property, request, blockId);
        block.update(request.startDate(), request.endDate(), blankToNull(request.reason()));
        return AvailabilityBlockResponse.from(block);
    }

    @Transactional
    public void deleteBlock(UUID hostId, UUID propertyId, UUID blockId) {
        lockOwned(hostId, propertyId);
        blocks.delete(findBlock(propertyId, blockId));
    }

    /** Bloquea la propiedad (FOR UPDATE) para que no entre una reserva mientras cambian los bloqueos. */
    private PropertyInfo lockOwned(UUID hostId, UUID propertyId) {
        PropertyInfo property = properties.lockActive(propertyId)
                .orElseThrow(() -> BookingService.error(HttpStatus.NOT_FOUND, "Alojamiento no encontrado"));
        requireOwner(property, hostId);
        return property;
    }

    private AvailabilityBlock findBlock(UUID propertyId, UUID blockId) {
        return blocks.findByIdAndPropertyId(blockId, propertyId)
                .orElseThrow(() -> BookingService.error(HttpStatus.NOT_FOUND, "Bloqueo no encontrado"));
    }

    private void validateRange(PropertyInfo property, AvailabilityBlockRequest request, UUID ignoredBlockId) {
        if (!request.endDate().isAfter(request.startDate())) {
            throw BookingService.error(HttpStatus.BAD_REQUEST, "La fecha de fin debe ser posterior a la de inicio");
        }
        if (bookings.countOverlapping(property.id(), BookingStatus.ACTIVE, request.startDate(),
                request.endDate()) > 0) {
            throw BookingService.error(HttpStatus.CONFLICT,
                    "Hay reservas pendientes o confirmadas en esas fechas; cancélalas antes de bloquear");
        }
        boolean overlapsOtherBlock = blocks.findOverlapping(property.id(), request.startDate(), request.endDate())
                .stream()
                .anyMatch(other -> !other.getId().equals(ignoredBlockId));
        if (overlapsOtherBlock) {
            throw BookingService.error(HttpStatus.CONFLICT, "Ya existe un bloqueo que cubre parte de esas fechas");
        }
    }

    private static void requireOwner(PropertyInfo property, UUID hostId) {
        if (!property.hostId().equals(hostId)) {
            throw BookingService.error(HttpStatus.FORBIDDEN,
                    "Solo el anfitrión del alojamiento puede gestionar su disponibilidad");
        }
    }

    private static void addNights(TreeSet<LocalDate> occupied, LocalDate rangeStart, LocalDate rangeEndExclusive,
            LocalDate windowStart, LocalDate windowEndExclusive) {
        LocalDate day = rangeStart.isBefore(windowStart) ? windowStart : rangeStart;
        LocalDate stop = rangeEndExclusive.isAfter(windowEndExclusive) ? windowEndExclusive : rangeEndExclusive;
        for (; day.isBefore(stop); day = day.plusDays(1)) {
            occupied.add(day);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

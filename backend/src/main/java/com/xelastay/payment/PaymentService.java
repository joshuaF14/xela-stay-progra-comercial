package com.xelastay.payment;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.xelastay.audit.AuditService;
import com.xelastay.booking.Booking;
import com.xelastay.booking.BookingRepository;
import com.xelastay.booking.BookingStatus;
import com.xelastay.booking.PropertyInfo;
import com.xelastay.booking.PropertyReader;
import com.xelastay.common.PageResponse;
import com.xelastay.common.Paging;
import com.xelastay.payment.dto.CreatePaymentRequest;
import com.xelastay.payment.dto.PaymentResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Pagos simulados. Lee la reserva con el repositorio y el lector de propiedades del módulo de reservas, sin
 * modificarla: un pago aprobado no confirma la reserva (la confirma el anfitrión).
 */
@Service
public class PaymentService {

    static final ZoneId GUATEMALA = ZoneId.of("America/Guatemala");
    private static final Set<BookingStatus> PAYABLE = Set.of(BookingStatus.PENDING, BookingStatus.CONFIRMED);
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

    private final PaymentRepository payments;
    private final BookingRepository bookings;
    private final PropertyReader properties;
    private final AuditService audit;

    public PaymentService(PaymentRepository payments, BookingRepository bookings, PropertyReader properties,
            AuditService audit) {
        this.payments = payments;
        this.bookings = bookings;
        this.properties = properties;
        this.audit = audit;
    }

    /**
     * Solo el huésped paga, por el total de la reserva. La reserva se bloquea (FOR UPDATE) para que dos pagos
     * simultáneos no queden aprobados a la vez. Aprobado salvo {@code simulateFailure = true}.
     */
    @Transactional
    public PaymentResponse pay(UUID userId, UUID bookingId, CreatePaymentRequest request) {
        Booking booking = bookings.findByIdForUpdate(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
        if (!booking.getGuestId().equals(userId)) {
            throw error(HttpStatus.FORBIDDEN, "Solo el huésped de la reserva puede pagarla");
        }
        if (!PAYABLE.contains(booking.getStatus())) {
            throw error(HttpStatus.CONFLICT, "Solo se pueden pagar reservas pendientes o confirmadas");
        }
        if (payments.existsByBookingIdAndStatus(bookingId, PaymentStatus.APPROVED)) {
            throw error(HttpStatus.CONFLICT, "La reserva ya tiene un pago aprobado");
        }
        boolean approved = !Boolean.TRUE.equals(request.simulateFailure());
        Payment payment = Payment.simulated(bookingId, booking.getTotal(), booking.getCurrency(),
                request.paymentMethod(), approved);
        PaymentResponse created = toResponse(payments.save(payment));
        audit.recordAction("PAYMENT_CREATED", "payment", created.id(), null, created, userId);
        return created;
    }

    /** Pagos de una reserva: su huésped, el anfitrión del alojamiento o un ADMIN. Más recientes primero. */
    @Transactional(readOnly = true)
    public List<PaymentResponse> listForBooking(UUID userId, boolean admin, UUID bookingId) {
        Booking booking = bookings.findById(bookingId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Reserva no encontrada"));
        boolean guest = booking.getGuestId().equals(userId);
        boolean host = properties.findById(booking.getPropertyId())
                .map(PropertyInfo::hostId)
                .filter(userId::equals)
                .isPresent();
        if (!admin && !guest && !host) {
            throw error(HttpStatus.FORBIDDEN, "No participa en esta reserva");
        }
        return payments.findByBookingIdOrderByCreatedAtDesc(bookingId).stream()
                .map(PaymentService::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> mine(UUID userId, int page, int size) {
        Page<Payment> result = payments.findByGuestId(userId, Paging.of(page, size, NEWEST_FIRST));
        return PageResponse.of(result, result.getContent().stream().map(PaymentService::toResponse).toList());
    }

    /** ADMIN: filtros opcionales de estado y de días de creación [from, to] (hora de Guatemala, ambos incluidos). */
    @Transactional(readOnly = true)
    public PageResponse<PaymentResponse> search(PaymentStatus status, LocalDate from, LocalDate to, int page,
            int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw error(HttpStatus.BAD_REQUEST, "from no puede ser posterior a to");
        }
        List<Specification<Payment>> filters = new ArrayList<>();
        if (status != null) {
            filters.add(PaymentRepository.hasStatus(status));
        }
        if (from != null) {
            filters.add(PaymentRepository.createdFrom(startOfDay(from)));
        }
        if (to != null) {
            filters.add(PaymentRepository.createdBefore(startOfDay(to.plusDays(1))));
        }
        Page<Payment> result = payments.findAll(Specification.allOf(filters), Paging.of(page, size, NEWEST_FIRST));
        return PageResponse.of(result, result.getContent().stream().map(PaymentService::toResponse).toList());
    }

    /** ADMIN: solo un pago APPROVED se reembolsa. La reserva no cambia. */
    @Transactional
    public PaymentResponse refund(UUID paymentId) {
        Payment payment = payments.findById(paymentId)
                .orElseThrow(() -> error(HttpStatus.NOT_FOUND, "Pago no encontrado"));
        if (payment.getStatus() != PaymentStatus.APPROVED) {
            throw error(HttpStatus.CONFLICT, "Solo se reembolsan pagos aprobados");
        }
        payment.refund();
        PaymentResponse refunded = toResponse(payments.save(payment));
        audit.recordAction("PAYMENT_REFUNDED", "payment", paymentId, Map.of("status", PaymentStatus.APPROVED.name()),
                Map.of("status", PaymentStatus.REFUNDED.name()), null);
        return refunded;
    }

    private static OffsetDateTime startOfDay(LocalDate day) {
        return day.atStartOfDay(GUATEMALA).toOffsetDateTime();
    }

    static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(payment.getId(), payment.getBookingId(), payment.getAmount(),
                payment.getCurrency(), payment.getPaymentMethod(), payment.getStatus(),
                payment.getTransactionReference(), payment.getPaidAt(), payment.getCreatedAt());
    }

    private static ResponseStatusException error(HttpStatus status, String message) {
        return new ResponseStatusException(status, message);
    }
}

package com.xelastay.booking;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * Reserva (tabla bookings de V1). property_id y guest_id son columnas UUID simples, sin relaciones JPA
 * con Property o Profile (entidades de Joshua). Las fechas son un intervalo [checkIn, checkOut): el día de
 * salida queda libre para otra entrada.
 */
@Entity
@Table(name = "bookings")
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "property_id", nullable = false)
    private UUID propertyId;

    @Column(name = "guest_id", nullable = false)
    private UUID guestId;

    @Column(name = "check_in", nullable = false)
    private LocalDate checkIn;

    @Column(name = "check_out", nullable = false)
    private LocalDate checkOut;

    @Column(name = "guests_count", nullable = false)
    private int guestsCount;

    @Column(name = "nightly_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal nightlyPrice;

    @Column(name = "subtotal", nullable = false, precision = 12, scale = 2)
    private BigDecimal subtotal;

    @Column(name = "fees", nullable = false, precision = 12, scale = 2)
    private BigDecimal fees;

    @Column(name = "total", nullable = false, precision = 12, scale = 2)
    private BigDecimal total;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private BookingStatus status;

    @Column(name = "cancellation_reason", columnDefinition = "text")
    private String cancellationReason;

    @Column(name = "idempotency_key", length = 100)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, columnDefinition = "timestamptz")
    private Instant updatedAt;

    protected Booking() {
    }

    /** Nueva reserva PENDING con los montos ya calculados en el servidor. */
    public static Booking pending(UUID propertyId, UUID guestId, LocalDate checkIn, LocalDate checkOut,
            int guestsCount, BigDecimal nightlyPrice, BigDecimal subtotal, BigDecimal fees, String currency,
            String idempotencyKey) {
        Booking booking = new Booking();
        booking.propertyId = propertyId;
        booking.guestId = guestId;
        booking.checkIn = checkIn;
        booking.checkOut = checkOut;
        booking.guestsCount = guestsCount;
        booking.nightlyPrice = nightlyPrice;
        booking.subtotal = subtotal;
        booking.fees = fees;
        booking.total = subtotal.add(fees);
        booking.currency = currency;
        booking.status = BookingStatus.PENDING;
        booking.idempotencyKey = idempotencyKey;
        booking.createdAt = Instant.now();
        booking.updatedAt = booking.createdAt;
        return booking;
    }

    @PreUpdate
    void touch() {
        updatedAt = Instant.now();
    }

    public void confirm() {
        status = BookingStatus.CONFIRMED;
    }

    public void reject(String reason) {
        status = BookingStatus.REJECTED;
        cancellationReason = reason;
    }

    public void cancel(String reason) {
        status = BookingStatus.CANCELLED;
        cancellationReason = reason;
    }

    public void complete() {
        status = BookingStatus.COMPLETED;
    }

    public long nights() {
        return ChronoUnit.DAYS.between(checkIn, checkOut);
    }

    /** true si otra solicitud con la misma clave de idempotencia pide exactamente lo mismo. */
    public boolean sameRequest(UUID propertyId, LocalDate checkIn, LocalDate checkOut, int guestsCount) {
        return this.propertyId.equals(propertyId) && this.checkIn.equals(checkIn) && this.checkOut.equals(checkOut)
                && this.guestsCount == guestsCount;
    }

    public UUID getId() {
        return id;
    }

    public UUID getPropertyId() {
        return propertyId;
    }

    public UUID getGuestId() {
        return guestId;
    }

    public LocalDate getCheckIn() {
        return checkIn;
    }

    public LocalDate getCheckOut() {
        return checkOut;
    }

    public int getGuestsCount() {
        return guestsCount;
    }

    public BigDecimal getNightlyPrice() {
        return nightlyPrice;
    }

    public BigDecimal getSubtotal() {
        return subtotal;
    }

    public BigDecimal getFees() {
        return fees;
    }

    public BigDecimal getTotal() {
        return total;
    }

    public String getCurrency() {
        return currency;
    }

    public BookingStatus getStatus() {
        return status;
    }

    public String getCancellationReason() {
        return cancellationReason;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

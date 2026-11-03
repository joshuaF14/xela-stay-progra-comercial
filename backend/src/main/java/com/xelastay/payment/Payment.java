package com.xelastay.payment;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Pago simulado de una reserva (tabla payments de V1). El monto y la moneda se copian de la reserva en el servidor.
 * Un pago aprobado no cambia el estado de la reserva: la confirma el anfitrión (decisión del equipo).
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "booking_id", nullable = false, updatable = false)
    private UUID bookingId;

    @Column(name = "amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3, columnDefinition = "bpchar(3)")
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 40)
    private PaymentMethod paymentMethod;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private PaymentStatus status;

    @Column(name = "transaction_reference", length = 150)
    private String transactionReference;

    @Column(name = "paid_at", columnDefinition = "timestamptz")
    private OffsetDateTime paidAt;

    @Column(name = "created_at", nullable = false, updatable = false, columnDefinition = "timestamptz")
    private OffsetDateTime createdAt;

    protected Payment() {
    }

    /** Resultado de la pasarela simulada: aprobado (con paid_at) o rechazado. */
    public static Payment simulated(UUID bookingId, BigDecimal amount, String currency, PaymentMethod method,
            boolean approved) {
        Payment payment = new Payment();
        payment.bookingId = bookingId;
        payment.amount = amount;
        payment.currency = currency;
        payment.paymentMethod = method;
        payment.transactionReference = "SIM-" + UUID.randomUUID();
        payment.createdAt = OffsetDateTime.now(ZoneOffset.UTC);
        payment.status = approved ? PaymentStatus.APPROVED : PaymentStatus.REJECTED;
        payment.paidAt = approved ? payment.createdAt : null;
        return payment;
    }

    public void refund() {
        this.status = PaymentStatus.REFUNDED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public String getCurrency() {
        return currency;
    }

    public PaymentMethod getPaymentMethod() {
        return paymentMethod;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public String getTransactionReference() {
        return transactionReference;
    }

    public OffsetDateTime getPaidAt() {
        return paidAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}

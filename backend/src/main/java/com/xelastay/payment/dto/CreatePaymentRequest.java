package com.xelastay.payment.dto;

import jakarta.validation.constraints.NotNull;

import com.xelastay.payment.PaymentMethod;

/**
 * Pago simulado. El monto no se envía: es el total de la reserva. {@code simulateFailure: true} produce un pago
 * REJECTED (para probar el flujo de error).
 */
public record CreatePaymentRequest(
        @NotNull(message = "El método de pago es obligatorio (CARD, TRANSFER o CASH)") PaymentMethod paymentMethod,
        Boolean simulateFailure) {
}

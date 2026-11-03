package com.xelastay.payment.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.xelastay.payment.PaymentMethod;
import com.xelastay.payment.PaymentStatus;

/** @param paidAt momento de la aprobación; null si fue rechazado */
public record PaymentResponse(
        UUID id,
        UUID bookingId,
        BigDecimal amount,
        String currency,
        PaymentMethod paymentMethod,
        PaymentStatus status,
        String transactionReference,
        OffsetDateTime paidAt,
        OffsetDateTime createdAt) {
}

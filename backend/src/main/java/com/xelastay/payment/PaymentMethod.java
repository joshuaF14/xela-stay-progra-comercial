package com.xelastay.payment;

/** Métodos de pago simulados (payments.payment_method es VARCHAR(40) sin CHECK en V1). */
public enum PaymentMethod {
    CARD,
    TRANSFER,
    CASH
}

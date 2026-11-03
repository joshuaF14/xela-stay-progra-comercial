package com.xelastay.booking.dto;

/**
 * Resultado de crear una reserva.
 *
 * @param created false si la solicitud repetía una clave de idempotencia y se devolvió la reserva existente
 */
public record BookingResult(BookingResponse booking, boolean created) {
}

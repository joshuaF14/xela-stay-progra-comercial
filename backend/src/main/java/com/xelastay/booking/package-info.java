/**
 * Módulo de reservas y disponibilidad (tablas bookings, availability_blocks).
 * <p>
 * Responsable: Giovanni.
 * <p>
 * Los datos de properties (precio, capacidad, dueño) se leen con SQL nativo en {@link com.xelastay.booking.PropertyReader},
 * sin la entidad Property. Cada cambio de estado de una reserva escribe su evento en el outbox (módulo events).
 */
package com.xelastay.booking;

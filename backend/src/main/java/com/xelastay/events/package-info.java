/**
 * Módulo de eventos: outbox transaccional (tabla outbox_events) y publicación en RabbitMQ.
 * <p>
 * Responsable: Giovanni.
 * <p>
 * Flujo: el servicio de negocio llama a {@link com.xelastay.events.OutboxService} dentro de su transacción;
 * {@link com.xelastay.events.OutboxPublisher} publica los eventos pendientes en el exchange
 * {@value com.xelastay.events.EventTopology#EXCHANGE} y los consumidores reaccionan desde sus colas.
 */
package com.xelastay.events;

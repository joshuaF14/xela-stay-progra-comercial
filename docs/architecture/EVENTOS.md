# Eventos: outbox → RabbitMQ → notificaciones

Cada cambio de estado de una reserva genera notificaciones para el huésped y el anfitrión de forma asíncrona. Se
usa el patrón **outbox**: el evento se guarda en la base de datos en la misma transacción que el cambio de negocio
y después se publica en RabbitMQ. Así nunca se publica un evento de algo que no se guardó, y nunca se pierde uno de
algo que sí se guardó.

## Flujo

```text
 BookingService (una sola transacción)
 ------------------------------------------------------------
  1. SELECT ... FROM properties WHERE id = ? FOR UPDATE
  2. INSERT / UPDATE bookings
  3. OutboxService.enqueue  -> INSERT outbox_events (PENDING)
 ------------------------------------------------------------
                    |  COMMIT (reserva y evento juntos)
                    v
 OutboxPublisher (@Scheduled, cada 5 s)
  - SELECT ... FROM outbox_events
      WHERE status IN ('PENDING','FAILED') AND attempts < 10
      ORDER BY created_at LIMIT 20 FOR UPDATE SKIP LOCKED
  - publica cada evento y espera la confirmación del broker
      confirmado        -> status = PUBLISHED, published_at = now()
      error del broker  -> attempts + 1, status = FAILED (se reintenta)
      RabbitMQ caído    -> se corta el lote sin gastar intentos
                    |
                    |  routing key = event_type (p. ej. booking.confirmed)
                    v
 exchange xela.events (topic, durable)
                    |
                    |  binding booking.#
                    v
 cola booking.notifications ----(falla 3 veces)----> xela.events.dlx
                    |                                     |
                    v                                     v
 BookingNotificationListener                 cola booking.notifications.dlq
  NotificationService.notifyBookingEvent      (mensajes para revisar a mano)
                    |
                    v
 INSERT notifications (huésped y anfitrión)
   id = UUID v3 de (event_key | profile_id)
   ON CONFLICT (id) DO NOTHING   <- idempotente ante mensajes repetidos
```

## Eventos

| event_type (routing key) | event_key | Origen | Notificación (`type`) |
|---|---|---|---|
| `booking.created` | `booking:<id>:CREATED` | `POST /bookings` | `BOOKING_CREATED` |
| `booking.confirmed` | `booking:<id>:CONFIRMED` | `PATCH …/confirm` | `BOOKING_CONFIRMED` |
| `booking.rejected` | `booking:<id>:REJECTED` | `PATCH …/reject` | `BOOKING_REJECTED` |
| `booking.cancelled` | `booking:<id>:CANCELLED` | `PATCH …/cancel` | `BOOKING_CANCELLED` |
| `booking.cancelled` | `booking:<id>:EXPIRED` | vencimiento automático de una reserva `PENDING` | `BOOKING_CANCELLED` |
| `booking.completed` | `booking:<id>:COMPLETED` | `PATCH …/complete` | `BOOKING_COMPLETED` |

`event_key` es único en `outbox_events`: cada evento concreto se registra una sola vez.

Mensaje que viaja por RabbitMQ (JSON):

```json
{
  "eventId": "…", "eventKey": "booking:…:CONFIRMED", "eventType": "booking.confirmed",
  "aggregateType": "booking", "aggregateId": "…", "occurredAt": "2026-11-04T15:30:00Z",
  "payload": {
    "bookingId": "…", "propertyId": "…", "propertyTitle": "Casa en Xela",
    "guestId": "…", "hostId": "…", "checkIn": "2026-11-20", "checkOut": "2026-11-22",
    "status": "CONFIRMED", "reason": null
  }
}
```

## Garantías

- **Atomicidad:** `OutboxService` usa `Propagation.MANDATORY`; si se llama fuera de una transacción, falla.
- **Varias instancias:** `FOR UPDATE SKIP LOCKED` evita que dos backends publiquen el mismo evento.
- **Entrega al menos una vez:** si el commit del publicador falla después de publicar, el evento se reenvía. Por eso
  el consumidor es idempotente (ID determinista + `ON CONFLICT DO NOTHING`), sin columnas ni migraciones nuevas.
- **Errores del consumidor:** 3 intentos con 1 s de espera; luego el mensaje pasa a `booking.notifications.dlq`.
- `PROCESSING` existe en el CHECK de V1 pero no se usa: el bloqueo con `SKIP LOCKED` cumple esa función.

## Configuración

| Variable | Uso |
|---|---|
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | conexión (Docker local: `docker compose up -d rabbitmq`) |
| `RABBITMQ_MANAGEMENT_PORT` | consola web, por defecto http://localhost:15672 |
| `EVENTS_ENABLED` | `false` apaga publicador y consumidor; los eventos se acumulan en `PENDING` y salen al volver a `true` |

Spring declara exchanges, colas y bindings al conectarse (`RabbitConfig`, paquete `events`). En el perfil `test`
RabbitMQ está desactivado, así que las pruebas y el CI no lo necesitan.

## Cómo verificarlo

1. Con RabbitMQ y el backend corriendo, crear y confirmar una reserva.
2. En la consola de RabbitMQ: exchange `xela.events` y colas `booking.notifications` y `booking.notifications.dlq`.
3. Consultas de solo lectura:

```sql
SELECT event_key, status, attempts, published_at FROM outbox_events ORDER BY created_at DESC LIMIT 10;
SELECT profile_id, type, title, read_at FROM notifications ORDER BY created_at DESC LIMIT 10;
```

4. `GET /api/v1/me/notifications` con el token del huésped o del anfitrión.

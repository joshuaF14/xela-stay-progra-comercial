# Reservas, eventos, notificaciones, reseñas, chat e IA

Módulos a cargo de Giovanni. Los contratos HTTP están en [ENDPOINTS.md](../api/ENDPOINTS.md) y el flujo de eventos
en [EVENTOS.md](../architecture/EVENTOS.md); aquí se explica cómo están construidos, por qué y cómo operarlos.

## Paquetes y tablas

| Paquete (`com.xelastay.*`) | Responsabilidad | Tablas de V1 |
|---|---|---|
| `booking` | Reservas, transiciones, calendario y bloqueos | `bookings`, `availability_blocks` (lee `properties`) |
| `events` | Outbox y publicación en RabbitMQ | `outbox_events` |
| `notification` | Consumidor de eventos y bandeja del usuario | `notifications` |
| `review` | Reseñas de estancias completadas | `reviews` |
| `messaging` | Chat entre huésped y anfitrión | `conversations`, `conversation_participants`, `messages` |
| `ai` | Ingesta, búsqueda y chat RAG | `knowledge_documents`, `knowledge_chunks`, `ai_interactions` |
| `common` | `PageResponse` y `ProfileNameReader` (nombres de `profiles`) | — |

Cada paquete sigue la misma forma: entidad, repositorio, servicio, controlador y `dto/` con records.

## Decisiones de diseño

1. **Sin relaciones JPA hacia otros módulos.** `property_id`, `guest_id`, `reviewer_id`, `sender_id` y `profile_id`
   son columnas UUID simples. Lo que hace falta de `properties`, `locations` o `profiles` se lee con SQL nativo
   (`PropertyReader`, `ProfileNameReader`). Así los módulos no dependen de las entidades de otro integrante y
   Hibernate solo valida (`ddl-auto=validate`) las tablas propias.
2. **Concurrencia en reservas.** V1 no impide en la base dos reservas cruzadas (decisión 12 de PENDIENTES). La
   garantía está en el backend: crear una reserva o cambiar bloqueos hace `SELECT … FROM properties … FOR UPDATE`,
   y el chequeo de solape ocurre dentro de ese bloqueo. Las transiciones bloquean la fila de la reserva
   (`PESSIMISTIC_WRITE`) para que dos clics no la cambien dos veces.
3. **Montos en el servidor.** `nightly_price`, `subtotal`, `fees` (0) y `total` salen del precio del alojamiento;
   el cliente nunca los envía.
4. **Idempotencia.** Una reserva repetida con la misma `idempotencyKey` devuelve la existente (`200`) gracias a
   `UNIQUE (guest_id, idempotency_key)`. El consumidor de eventos es idempotente con IDs deterministas.
5. **Outbox.** Cada cambio de reserva escribe su evento en la misma transacción; un publicador programado lo
   envía a RabbitMQ con confirmación del broker (detalle en EVENTOS.md).
6. **Auditoría.** Cada transición de reserva queda en `audit_logs` mediante `AuditService`, en la misma transacción;
   las preguntas al agente con `source = 'AI'` y la ingesta de documentos con `source = 'ADMIN'`.
7. **Permisos.** Solo la ingesta de documentos exige un rol (`@PreAuthorize("hasRole('ADMIN')")`). El resto se
   decide por pertenencia en el servicio: dueño del alojamiento, huésped de la reserva o participante del chat.
8. **RAG sin pgvector.** Búsqueda de texto completo en español: `plainto_tsquery` con los términos unidos por OR,
   `ts_rank` y más peso al título del documento. Gemini se llama con el `RestClient` de Spring (sin SDK). El modelo
   solo puede responder con las fuentes y citarlas; si no hay fuentes, no se le llama.

## Reglas de negocio

- **Reservas:** nacen `PENDING`. El anfitrión confirma o rechaza; huésped o anfitrión cancelan con motivo; el
  anfitrión completa una `CONFIRMED`. El huésped no puede reservar su propio alojamiento ni superar la capacidad,
  y la entrada no puede ser anterior a hoy (hora de Guatemala). Las fechas son `[checkIn, checkOut)`.
- **Bloqueos:** solo el dueño; no pueden cubrir reservas activas ni cruzarse con otro bloqueo.
- **Reseñas:** solo el huésped de una reserva `COMPLETED`, una por reserva, calificación de 1 a 5.
- **Chat:** el huésped abre la conversación sobre un alojamiento publicado; si ya existía, se reutiliza.
- **Notificaciones:** una para el huésped y otra para el anfitrión por cada evento de reserva.

## Configuración

| Variable o propiedad | Valor por defecto | Uso |
|---|---|---|
| `EVENTS_ENABLED` | `true` | Apaga publicador y consumidor (los eventos esperan en `PENDING`). |
| `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD` | `localhost`, `5672` | Conexión al broker. |
| `RABBITMQ_VHOST`, `RABBITMQ_SSL` | `/`, `false` | Para RabbitMQ en la nube (CloudAMQP: vhost propio, `5671` y `true`). |
| `xelastay.events.publish-interval-ms` | `5000` | Cada cuánto corre el publicador. |
| `xelastay.events.batch-size` / `max-attempts` | `20` / `10` | Eventos por ciclo e intentos antes de dejarlo `FAILED`. |
| `AI_API_KEY` | vacía | Key de Google AI Studio. Sin ella, `/ai/chat` responde `503`. |
| `AI_MODEL` | `gemini-3.8-flash` | Alternativa con más cuota: `gemini-3.5-flash-lite`. |
| `xelastay.ai.max-chunks`, `chunk-words`, `chunk-overlap-words` | `5`, `800`, `100` | Recuperación y fragmentación. |

## Pruebas

`cd backend && ./mvnw test`. El perfil `test` no usa base de datos ni RabbitMQ (`spring.main.lazy-initialization`
y RabbitMQ excluido), así que los servicios se prueban con Mockito y los controladores con MockMvc y JWT simulado.

| Módulo | Clases de prueba |
|---|---|
| `booking` | `BookingServiceTest`, `AvailabilityServiceTest`, `BookingControllerTest`, `AvailabilityControllerTest` |
| `events` | `OutboxServiceTest`, `OutboxPublisherTest`, `RabbitConfigTest` |
| `notification` | `NotificationServiceTest`, `NotificationInboxTest`, `NotificationControllerTest` |
| `review` | `ReviewServiceTest`, `ReviewControllerTest` |
| `messaging` | `ConversationServiceTest`, `ConversationControllerTest` |
| `ai` | `TextChunkerTest`, `KnowledgeServiceTest`, `AiChatServiceTest`, `GeminiClientTest`, `AiControllerTest` |

Lo que las pruebas no cubren, porque necesita PostgreSQL real: el SQL nativo y la validación de entidades. Se
verifica arrancando el backend contra Supabase (`./mvnw spring-boot:run` y `GET /actuator/health` en `UP`).

## Solución de problemas

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| Al arrancar: `Unable to determine Dialect without JDBC metadata` | El backend no se conecta a la base | Revisar `DB_URL`, `DB_USERNAME` y `DB_PASSWORD` en el `.env` (sin marcadores `<…>`). |
| Logs: "RabbitMQ no está disponible" | Broker apagado | `docker compose up -d rabbitmq`, o `EVENTS_ENABLED=false` mientras tanto. |
| Eventos en `FAILED` con `attempts = 10` | El broker los rechazó 10 veces | Revisar la conexión; el publicador ya no los reintenta. |
| Mensajes en `booking.notifications.dlq` | El consumidor falló 3 veces (p. ej. perfil inexistente) | Revisar el mensaje en la consola de RabbitMQ. |
| `POST /ai/chat` responde `503` | Falta `AI_API_KEY` o se agotó la cuota | Configurar la key o cambiar `AI_MODEL`. |
| `POST /ai/chat` responde `502` "no reconoce el modelo" | `AI_MODEL` inválido | Usar un modelo vigente de Gemini. |
| El agente siempre dice "No tengo información…" | No hay documentos `INDEXED` que coincidan | Cargar los documentos de [docs/ai](../ai/README.md). |
| `POST /bookings` responde `409` "No se pudo registrar la reserva" | Clave de idempotencia repetida o el huésped no tiene fila en `profiles` | Usar otra clave; el perfil se crea al primer login. |

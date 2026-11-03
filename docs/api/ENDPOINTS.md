# Endpoints de la API

Contratos de los módulos de reservas, disponibilidad, reseñas, chat, notificaciones e IA (responsable: Giovanni).
Los módulos de perfiles, catálogo y alojamientos los documenta su responsable.

## Convenciones

Mientras se publica `docs/api/CONVENCIONES.md`, estos endpoints siguen lo que ya implementa el backend base:

| Tema | Regla |
|---|---|
| Base | Todas las rutas van bajo `/api/v1`. |
| Autenticación | `Authorization: Bearer <access_token de Supabase>`. El usuario sale siempre del JWT (`CurrentUser`), nunca del cuerpo ni de la URL. |
| Rutas públicas | `GET /api/v1/properties/{id}/availability` y `GET /api/v1/properties/{id}/reviews` (además de `/api/health`). |
| Fechas | `yyyy-MM-dd` para días (`LocalDate`) e ISO-8601 en UTC para momentos (`2026-11-04T15:30:00Z`). |
| Dinero | Decimales con 2 cifras (`350.00`) y moneda ISO (`GTQ`). Los montos los calcula el servidor. |
| Paginación | `?page=0&size=20` (página desde 0, tamaño de 1 a 100). Respuesta: `{content, page, size, totalElements, totalPages}`. |
| Errores | Formato `ApiError`: `{timestamp, status, error, message, path, details[{field, message}]}`. |

Códigos de error usados: `400` datos inválidos, `401` sin token o token inválido, `403` sin permiso sobre el recurso,
`404` no existe (o no es visible para el usuario), `409` conflicto de estado o de fechas, `502/503/504` proveedor de IA.

---

## Reservas

Estados: `PENDING` → `CONFIRMED` → `COMPLETED`, o bien `REJECTED` / `CANCELLED`.

| Transición | Quién | Desde |
|---|---|---|
| `confirm` | anfitrión | `PENDING` |
| `reject` | anfitrión | `PENDING` |
| `cancel` | huésped o anfitrión, con motivo | `PENDING`, `CONFIRMED` |
| `complete` | anfitrión | `CONFIRMED` |

Cada creación y cada transición escribe un evento en el outbox (ver [EVENTOS.md](../architecture/EVENTOS.md)).

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| POST | `/bookings` | autenticado | `201` reserva nueva (`Location`), `200` si se repitió la clave de idempotencia |
| GET | `/bookings/{id}` | huésped o anfitrión de la reserva | `200` |
| PATCH | `/bookings/{id}/confirm` | anfitrión | `200` |
| PATCH | `/bookings/{id}/reject` | anfitrión | `200` (cuerpo opcional `{"reason"}`) |
| PATCH | `/bookings/{id}/cancel` | huésped o anfitrión | `200` (cuerpo `{"reason"}` obligatorio) |
| PATCH | `/bookings/{id}/complete` | anfitrión | `200` |
| GET | `/me/bookings?status=&page=&size=` | huésped | `200` página de reservas propias |
| GET | `/me/hosting/bookings?status=&page=&size=` | anfitrión | `200` página de reservas de sus alojamientos |

### POST /bookings

```json
{
  "propertyId": "8f0c…",
  "checkIn": "2026-11-20",
  "checkOut": "2026-11-22",
  "guestsCount": 2,
  "idempotencyKey": "c1d2…"
}
```

En una sola transacción: bloquea la propiedad (`SELECT … FOR UPDATE`, solo `PUBLISHED` y no borrada), valida y
crea la reserva en `PENDING`. `nightlyPrice`, `subtotal`, `fees` y `total` se calculan en el servidor (`fees = 0`).
Las fechas son un intervalo `[checkIn, checkOut)`: el día de salida queda libre para otra entrada.

| Error | Caso |
|---|---|
| `400` | `checkOut` no es posterior a `checkIn`, `checkIn` anterior a hoy (hora de Guatemala) o más huéspedes que la capacidad |
| `403` | el huésped es el dueño del alojamiento |
| `404` | alojamiento inexistente, no publicado o borrado |
| `409` | fechas cruzadas con una reserva `PENDING`/`CONFIRMED` o con un bloqueo; clave de idempotencia usada para otra reserva |

`idempotencyKey` es opcional (máx. 100). Va en el cuerpo porque CORS solo admite los encabezados `Authorization` y
`Content-Type`. Una misma clave con los mismos datos devuelve la reserva existente.

### Respuesta (BookingResponse)

```json
{
  "id": "…", "propertyId": "…", "propertyTitle": "Casa en Xela", "hostId": "…", "guestId": "…",
  "checkIn": "2026-11-20", "checkOut": "2026-11-22", "nights": 2, "guestsCount": 2,
  "nightlyPrice": 350.00, "subtotal": 700.00, "fees": 0.00, "total": 700.00, "currency": "GTQ",
  "status": "PENDING", "cancellationReason": null,
  "createdAt": "2026-11-04T15:30:00Z", "updatedAt": "2026-11-04T15:30:00Z"
}
```

---

## Disponibilidad

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| GET | `/properties/{id}/availability?from=&to=` | **público** | `200` noches ocupadas |
| GET | `/properties/{id}/availability-blocks` | dueño | `200` lista de bloqueos |
| POST | `/properties/{id}/availability-blocks` | dueño | `201` |
| PUT | `/properties/{id}/availability-blocks/{blockId}` | dueño | `200` |
| DELETE | `/properties/{id}/availability-blocks/{blockId}` | dueño | `204` |

`GET …/availability` devuelve `{propertyId, from, to, occupiedDates: ["2026-11-20", …]}` con las noches
ocupadas por reservas `PENDING`/`CONFIRMED` o por bloqueos, entre `from` y `to` (ambas incluidas). Por defecto
`from` = hoy y `to` = `from` + 90 días; el rango máximo es 366 días. No revela quién reservó.

Cuerpo de un bloqueo: `{"startDate": "2026-12-01", "endDate": "2026-12-05", "reason": "Mantenimiento"}`; el
intervalo es `[startDate, endDate)`. Responde `409` si cubre reservas activas o se cruza con otro bloqueo.

---

## Reseñas

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| POST | `/bookings/{bookingId}/review` | huésped de la reserva | `201` |
| GET | `/properties/{id}/reviews?page=&size=` | **público** | `200` página, más recientes primero |
| PUT | `/reviews/{id}` | autor | `200` |
| DELETE | `/reviews/{id}` | autor | `204` |

Cuerpo: `{"rating": 5, "comment": "Excelente vista"}` (`rating` de 1 a 5, `comment` opcional, máx. 2000).
Solo para reservas `COMPLETED`, una por reserva (`409` si no está completada o ya tiene reseña). `property_id` se
toma de la reserva. Respuesta: `{id, bookingId, propertyId, reviewerId, reviewerName, rating, comment, createdAt,
updatedAt}`.

---

## Chat

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| POST | `/conversations` | huésped | `201` nueva, `200` si ya existía |
| GET | `/me/conversations?page=&size=` | participante | `200` bandeja, actividad más reciente primero |
| GET | `/conversations/{id}/messages?page=&size=50` | participante | `200` mensajes, **más recientes primero** |
| POST | `/conversations/{id}/messages` | participante | `201` |
| PATCH | `/conversations/{id}/read` | participante | `200` `{"markedAsRead": n}` |

- `POST /conversations` recibe `{"propertyId": "…", "message": "Hola"}` (`message` opcional). Participan el usuario
  y el dueño del alojamiento; el dueño no puede abrir una conversación sobre su propio alojamiento (`400`).
- La bandeja trae `{id, propertyId, propertyTitle, otherParticipantId, otherParticipantName, lastMessage,
  unreadCount, createdAt, updatedAt}`.
- Mensaje: `{"content": "…"}` (no vacío, máx. 2000). `PATCH …/read` marca como leídos solo los mensajes recibidos.
- Quien no participa recibe `403`.

---

## Notificaciones

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| GET | `/me/notifications?unreadOnly=false&page=&size=` | destinatario | `200` página, más recientes primero |
| PATCH | `/me/notifications/{id}/read` | destinatario | `200` (`404` si no es suya) |

Respuesta: `{id, type, title, message, readAt, createdAt}`. Con `unreadOnly=true`, `totalElements` sirve como
contador de no leídas. Tipos actuales (los crea el consumidor de RabbitMQ): `BOOKING_CREATED`, `BOOKING_CONFIRMED`,
`BOOKING_REJECTED`, `BOOKING_CANCELLED`, `BOOKING_COMPLETED`.

---

## Agente de IA (RAG)

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| POST | `/ai/documents` (JSON) | ADMIN | `201` |
| POST | `/ai/documents` (multipart) | ADMIN | `201` |
| POST | `/ai/chat` | autenticado | `200` |

### Ingesta

- JSON: `{"title": "…", "documentType": "FAQ", "content": "…", "source": "opcional"}`.
- Multipart: `file` (`.md` o `.txt` en UTF-8), `documentType` y `title` opcional (por defecto, el nombre del archivo).

Divide el texto en fragmentos de ~800 palabras con 100 de solape y guarda `knowledge_documents` (estado `INDEXED`) y
`knowledge_chunks`. Respuesta: `{documentId, title, documentType, source, status, chunkCount}`. Queda en
`audit_logs` con `source = 'ADMIN'`. El rol ADMIN se exige con `@PreAuthorize("hasRole('ADMIN')")` (`403` sin él).

### Chat

Cuerpo `{"question": "¿Hasta cuándo puedo cancelar sin costo?"}` (máx. 1000).

1. Recupera los 5 fragmentos más relevantes con búsqueda de texto completo en español
   (`to_tsvector('spanish')`, `plainto_tsquery` con términos unidos por OR y `ts_rank`), sin pgvector.
2. Agrega hasta 3 alojamientos `PUBLISHED` cuyo título, descripción o ubicación coinciden.
3. Pide a Gemini (`AI_MODEL`, por defecto `gemini-3.8-flash`) responder solo con esas fuentes y citarlas como `[n]`.
   Si no hay fuentes, no llama al modelo y responde que no tiene esa información.
4. Registra la interacción en `ai_interactions` y en `audit_logs` con `source = 'AI'`.

```json
{
  "interactionId": "…",
  "answer": "Puedes cancelar sin costo hasta 48 horas antes del check-in [1].",
  "sources": [
    {"ref": 1, "type": "DOCUMENT", "documentId": "…", "documentTitle": "Políticas de cancelación",
     "chunkId": "…", "chunkIndex": 0, "excerpt": "…"},
    {"ref": 2, "type": "PROPERTY", "propertyId": "…", "propertyTitle": "Casa en Xela",
     "excerpt": "Q350.00 por noche, hasta 4 huéspedes, en Quetzaltenango, Quetzaltenango."}
  ]
}
```

| Error | Caso |
|---|---|
| `503` | falta `AI_API_KEY` o se alcanzó el límite de uso de Gemini |
| `502` | Gemini respondió con error o el modelo configurado no existe |
| `504` | Gemini no respondió en 30 s |

Documentos de ejemplo y comandos para cargarlos: [docs/ai/README.md](../ai/README.md).

# Endpoints de la API

Contratos de los módulos de perfiles, catálogo y alojamientos (responsable: Joshua) y de reservas, disponibilidad,
reseñas, chat, notificaciones e IA (responsable: Giovanni).

## Convenciones

Reglas completas en [CONVENCIONES.md](CONVENCIONES.md). Resumen:

| Tema | Regla |
|---|---|
| Base | Todas las rutas van bajo `/api/v1`. |
| Autenticación | `Authorization: Bearer <access_token de Supabase>`. El usuario sale siempre del JWT (`CurrentUser`), nunca del cuerpo ni de la URL. |
| Rutas públicas (solo `GET`) | `/api/health`, `/properties`, `/properties/{id}`, `/properties/{id}/availability`, `/properties/{id}/reviews`, `/property-categories/**`, `/amenities/**` y `/locations/**`. |
| JSON | Campos en camelCase (`pricePerNight`, `coverImageUrl`). Respuestas con `charset=UTF-8`. |
| Fechas | `yyyy-MM-dd` para días (`LocalDate`) e ISO-8601 en UTC para momentos (`2026-11-04T15:30:00Z`). |
| Dinero | Decimales con 2 cifras (`350.00`) y moneda ISO (`GTQ`). Los montos los calcula el servidor. |
| Paginación | `?page=0&size=20` (página desde 0, tamaño de 1 a 100). Respuesta: `{content, page, size, totalElements, totalPages}`. |
| Errores | Formato `ApiError`: `{timestamp, status, error, message, path, details[{field, message}]}`. |

Códigos de error usados: `400` datos inválidos, `401` sin token o token inválido, `403` sin permiso sobre el recurso,
`404` no existe (o no es visible para el usuario), `409` conflicto de estado o de fechas, `502/503/504` proveedor de IA.

---

## Perfil propio y roles

El perfil se crea solo en la primera petición con token (rol `GUEST`, estado `ACTIVE`). Un perfil `SUSPENDED` o
`INACTIVE` recibe `403` en todo `/api/v1/**` salvo `GET /me`. Detalle en
[AUTENTICACION.md](../architecture/AUTENTICACION.md).

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| GET | `/me` | autenticado (también bloqueado) | `200` perfil |
| PATCH | `/me` | autenticado | `200` perfil |
| POST | `/me/host` | autenticado | `200` perfil con rol `HOST` agregado |
| GET | `/profiles?status=&role=&page=&size=` | ADMIN | `200` página de perfiles, más recientes primero |
| GET | `/profiles/{id}` | ADMIN | `200` |
| PATCH | `/profiles/{id}/status` | ADMIN | `200`; `409` sobre su propio perfil |
| PUT | `/profiles/{id}/roles` | ADMIN | `200`; `400` rol inexistente; `409` si se quita su propio ADMIN |
| GET | `/roles` | ADMIN | `200` lista |
| GET | `/roles/{id}` | ADMIN | `200` |
| POST | `/roles` | ADMIN | `201` (`Location`); `409` nombre repetido |
| PUT | `/roles/{id}` | ADMIN | `200`; `409` al renombrar GUEST/HOST/ADMIN |
| DELETE | `/roles/{id}` | ADMIN | `204`; `409` si es GUEST/HOST/ADMIN o está asignado |

Perfil (`GET /me`):

```json
{
  "id": "…", "email": "ana@correo.com", "fullName": "Ana López", "phone": "+502 5555 5555",
  "avatarUrl": "https://…/ana.png", "status": "ACTIVE", "roles": ["GUEST", "HOST"],
  "createdAt": "2026-11-04T15:30:00Z", "updatedAt": "2026-11-04T15:30:00Z"
}
```

- `PATCH /me`: `{"fullName": "…", "phone": "…", "avatarUrl": "https://…"}`. Todos opcionales: un campo ausente no
  cambia; en `phone` y `avatarUrl`, `""` borra el valor. `avatarUrl` debe empezar con `https://`.
- `PATCH /profiles/{id}/status`: `{"status": "ACTIVE" | "INACTIVE" | "SUSPENDED"}`.
- `PUT /profiles/{id}/roles`: `{"roles": ["GUEST", "HOST"]}` (lista completa, no vacía).
- Rol: `{"name": "SOPORTE", "description": "…"}` (mayúsculas, `_` y números). Respuesta
  `{id, name, description, system, createdAt}`; `system = true` en GUEST, HOST y ADMIN.

---

## Catálogo

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| GET | `/property-categories?includeInactive=false` | **público** | `200` lista por nombre (solo activas; un ADMIN puede pedir también las inactivas) |
| GET | `/property-categories/{id}` | **público** | `200`; `404` si está inactiva (salvo ADMIN) |
| POST | `/property-categories` | ADMIN | `201`; `409` nombre repetido (sin distinguir mayúsculas) |
| PUT | `/property-categories/{id}` | ADMIN | `200`; `409` nombre repetido |
| DELETE | `/property-categories/{id}` | ADMIN | `204`: **desactiva** (`active = false`), no borra |
| GET | `/amenities?includeInactive=false` | **público** | igual que categorías |
| GET | `/amenities/{id}` | **público** | igual que categorías |
| POST / PUT / DELETE | `/amenities`, `/amenities/{id}` | ADMIN | igual que categorías |
| GET | `/locations?department=&municipality=&page=&size=` | **público** | `200` página ordenada por departamento, municipio y dirección |
| GET | `/locations/{id}` | **público** | `200` |
| POST | `/locations` | HOST o ADMIN | `201` |
| PUT | `/locations/{id}` | HOST o ADMIN | `200`; `403` a un HOST si la usan alojamientos de otro anfitrión |

- Categoría o amenidad (cuerpo): `{"name": "Villa", "description": "opcional", "active": true}`. `active` es opcional:
  al crear vale `true`; al editar, si no se envía, se conserva.
- Categoría (respuesta): `{id, name, description, active, createdAt}`. Amenidad: `{id, name, description, active}`.
- Ubicación (cuerpo): `{"department": "Quetzaltenango", "municipality": "Quetzaltenango", "address": "4a calle 12-34,
  zona 1", "latitude": 14.8347, "longitude": -91.518}`. Latitud (-90 a 90) y longitud (-180 a 180) son opcionales pero
  van juntas; se guardan con 7 decimales.
- Ubicación (respuesta): `{id, department, municipality, address, latitude, longitude, createdAt}`.

---

## Alojamientos

Estados: `DRAFT` (al crear) → `PUBLISHED` ⇄ `PAUSED`; `REJECTED` solo lo asigna y lo levanta un ADMIN. Para publicar
se necesita al menos una imagen. El público solo ve alojamientos `PUBLISHED` y no borrados.

| Método | Ruta | Quién | Respuesta |
|---|---|---|---|
| GET | `/properties` | **público** | `200` página de tarjetas (`PropertySummary`) |
| GET | `/properties/{id}` | **público** si está `PUBLISHED`; dueño y ADMIN en cualquier estado | `200` detalle; `404` si no es visible |
| POST | `/properties` | HOST | `201` detalle en `DRAFT` (`Location`) |
| PUT | `/properties/{id}` | dueño o ADMIN | `200` detalle |
| PATCH | `/properties/{id}/status` | dueño o ADMIN | `200` detalle |
| DELETE | `/properties/{id}` | dueño o ADMIN | `204` borrado lógico; `409` con reservas `PENDING`/`CONFIRMED` por venir |
| PUT | `/properties/{id}/amenities` | dueño o ADMIN | `200` lista de amenidades |
| POST | `/properties/{id}/images` | dueño o ADMIN | `201` imagen |
| PATCH | `/properties/{id}/images/{imageId}` | dueño o ADMIN | `200` imagen |
| DELETE | `/properties/{id}/images/{imageId}` | dueño o ADMIN | `204`; `409` si es la última de un alojamiento publicado |
| GET | `/me/properties?page=&size=` | autenticado | `200` página de mis alojamientos en cualquier estado |

### GET /properties

Filtros, todos opcionales:

| Parámetro | Ejemplo | Regla |
|---|---|---|
| `department`, `municipality` | `Quetzaltenango` | igualdad sin distinguir mayúsculas |
| `categoryId` | UUID | |
| `minPrice`, `maxPrice` | `100`, `500` | sobre `pricePerNight`; `400` si `minPrice > maxPrice` |
| `guests` | `3` | `capacity >= guests` |
| `amenityIds` | `?amenityIds=a&amenityIds=b` o `?amenityIds=a,b` | debe tenerlas **todas** |
| `q` | `volcán` | texto en título o descripción |
| `page`, `size` | `0`, `20` | |
| `sort` | `pricePerNight,asc` | `pricePerNight`, `createdAt` o `title`, con `asc` o `desc`; por defecto `createdAt,desc`; otro campo da `400` |

Respuesta (`PageResponse<PropertySummary>`):

```json
{
  "content": [
    {
      "id": "8f0c…",
      "hostId": "1a2b…",
      "title": "Cabaña El Mirador",
      "description": "Cabaña rústica con chimenea…",
      "pricePerNight": 350.00,
      "currency": "GTQ",
      "capacity": 4,
      "bedrooms": 2,
      "bathrooms": 1.5,
      "status": "PUBLISHED",
      "category": { "id": "…", "name": "Cabaña" },
      "location": {
        "id": "…", "department": "Quetzaltenango", "municipality": "Quetzaltenango",
        "address": "Zona 3, Los Altos", "latitude": 14.8347000, "longitude": -91.5180000,
        "createdAt": "2026-11-04T15:30:00Z"
      },
      "coverImageUrl": "https://…/portada.jpg",
      "averageRating": 4.50,
      "reviewCount": 12,
      "createdAt": "2026-11-04T15:30:00Z",
      "updatedAt": "2026-11-04T15:30:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

- `coverImageUrl`: la portada o, si no hay, la primera imagen; `null` sin imágenes.
- `averageRating`: promedio con 2 decimales desde `reporting_property_ratings`; `null` sin reseñas (`reviewCount = 0`).
- `latitude`/`longitude` pueden ser `null` si la ubicación no tiene coordenadas (el mapa debe omitir el marcador).

### GET /properties/{id} (PropertyDetail)

```json
{
  "id": "8f0c…", "title": "Cabaña El Mirador", "description": "…",
  "pricePerNight": 350.00, "currency": "GTQ", "capacity": 4, "bedrooms": 2, "bathrooms": 1.5,
  "status": "PUBLISHED",
  "category": { "id": "…", "name": "Cabaña" },
  "location": { "id": "…", "department": "…", "municipality": "…", "address": "…",
                "latitude": 14.8347000, "longitude": -91.5180000, "createdAt": "…" },
  "host": { "id": "1a2b…", "fullName": "Ana López" },
  "images": [
    { "id": "…", "imageUrl": "https://…/1.jpg", "caption": "Sala", "displayOrder": 0, "cover": true,
      "createdAt": "…" }
  ],
  "amenities": [ { "id": "…", "name": "WiFi", "description": null, "active": true } ],
  "averageRating": 4.50, "reviewCount": 12,
  "createdAt": "…", "updatedAt": "…"
}
```

`images` va ordenado por `displayOrder`; `amenities`, por nombre.

### POST y PUT /properties

```json
{
  "categoryId": "…",
  "locationId": "…",
  "title": "Cabaña El Mirador",
  "description": "Opcional, máx. 5000",
  "pricePerNight": 350.00,
  "currency": "GTQ",
  "capacity": 4,
  "bedrooms": 2,
  "bathrooms": 1.5
}
```

- Ubicación: `locationId` (existente) **o** `location` con el mismo cuerpo de `POST /locations` (se crea en la misma
  operación). Exactamente uno de los dos (`400` en el campo `locationProvidedOnce`).
- `currency` es opcional (`GTQ`). `pricePerNight > 0` con 2 decimales; `capacity` de 1 a 100; `bedrooms >= 0`;
  `bathrooms` de 0 a 99.9 con 1 decimal.
- La categoría debe existir y estar activa (`400`), salvo que sea la que el alojamiento ya tenía.
- `PATCH /properties/{id}/status`: `{"status": "PUBLISHED"}`. `403` si el dueño intenta `REJECTED`; `409` si el dueño
  cambia uno rechazado o publica sin imágenes.
- `PUT /properties/{id}/amenities`: `{"amenityIds": ["…", "…"]}` (lista completa; `[]` las quita). `400` si alguna no
  existe o si se agrega una inactiva.

### Imágenes

- `POST`: `{"imageUrl": "https://…", "caption": "opcional", "displayOrder": 0, "cover": false}`. `imageUrl` debe
  empezar con `https://`. Sin `displayOrder` va al final. La primera imagen se vuelve portada.
- `PATCH`: `{"caption": "…", "displayOrder": 2, "cover": true}` (todos opcionales). `cover: true` la vuelve la única
  portada.
- Al borrar la portada, la siguiente imagen pasa a ser portada.

### Para el frontend (`frontend/src/types/index.ts`)

Los tipos actuales usan snake_case; la API responde en camelCase y con objetos anidados:

| Tipo actual | API |
|---|---|
| `price_per_night` | `pricePerNight` |
| `host_id` | `hostId` (en el detalle: `host.id` y `host.fullName`) |
| `category_id` | `category.id` (y `category.name`) |
| `location_id` | `location.id` |
| `images[0].image_url` (tarjeta) | `coverImageUrl` |
| `PropertyImage.image_url`, `display_order`, `is_cover` | `imageUrl`, `displayOrder`, `cover` |
| `apiFetch<Property[]>('/api/v1/properties')` | `apiFetch<PageResponse<PropertySummary>>(…)` y usar `.content` |
| — | `averageRating`, `reviewCount` (en lugar del 4.95 fijo) |

`POST /bookings` también espera camelCase: `propertyId`, `checkIn`, `checkOut`, `guestsCount` (ver Reservas).

### Datos de demostración

[`scripts/seed-demo.ps1`](../../scripts/seed-demo.ps1) crea 4 alojamientos publicados en Quetzaltenango usando solo
la API (nada en SQL ni en el frontend): una cabaña en San Nicolás, un apartamento junto al Parque Central, una casa en
Zunil cerca de las Fuentes Georginas y un hostal en la zona 3. Cada uno con ubicación y coordenadas aproximadas, 2 o 3
imágenes (una portada) y 3 o 4 amenidades del catálogo.

Requisitos: backend corriendo, V2 aplicada (categorías y amenidades) y un usuario de Supabase Auth con email
confirmado. El script:

1. Pide la publishable/anon key, el email y la contraseña (no los guarda) y obtiene el `access_token` de Supabase.
2. Llama a `POST /me/host` (si ya es HOST, no cambia nada).
3. Lee categorías y amenidades de la API para usar sus ids reales.
4. Por cada alojamiento: `POST /properties` (con `location` nueva), `POST /properties/{id}/images`,
   `PUT /properties/{id}/amenities` y `PATCH /properties/{id}/status` a `PUBLISHED`.
5. Omite los alojamientos propios con el mismo título (se puede ejecutar varias veces) y muestra un resumen.

```powershell
# Desde la raíz del repositorio, con el backend en http://localhost:8080
powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1

# Otro backend
powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1 -BaseUrl http://localhost:8081
```

Si un paso falla, el script se detiene y muestra el `message` y los `details` del `ApiError`. Un alojamiento que quedó
a medias (por ejemplo en `DRAFT` sin imágenes) se omite en la siguiente ejecución por su título: complétalo desde la
API o bórralo con `DELETE /properties/{id}` y vuelve a ejecutar el script.

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

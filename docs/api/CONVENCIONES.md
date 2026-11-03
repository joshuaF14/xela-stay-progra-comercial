# Convenciones de la API

Reglas comunes a todos los módulos del backend. Los contratos concretos de cada endpoint están en
[ENDPOINTS.md](ENDPOINTS.md).

## Rutas

| Regla | Ejemplo |
|---|---|
| Todo bajo `/api/v1` (salvo `/api/health` y `/actuator/health`). | `/api/v1/bookings` |
| Recursos en plural y en kebab-case. | `/api/v1/property-categories` |
| Subrecursos anidados bajo su dueño. | `/api/v1/properties/{id}/images` |
| Recursos del usuario autenticado bajo `/me`. | `/api/v1/me/bookings` |
| IDs como UUID en la ruta. | `/api/v1/bookings/8f0c…` |
| Cambios de estado: `PATCH` sobre el recurso o sobre una acción. | `PATCH /properties/{id}/status`, `PATCH /bookings/{id}/confirm` |

Métodos: `GET` lee, `POST` crea (`201`), `PUT` reemplaza, `PATCH` modifica parcialmente o cambia el estado,
`DELETE` borra (`204`).

## Autenticación y usuario actual

- Encabezado `Authorization: Bearer <access_token de Supabase>`. El backend valida el JWT con el JWKS del proyecto
  ([AUTENTICACION.md](../architecture/AUTENTICACION.md)).
- El ID del usuario sale **siempre** de `CurrentUser` (claim `sub`). Nunca se acepta en el cuerpo, la query ni la
  ruta para identificar a quien hace la petición.
- CORS solo admite los encabezados `Authorization` y `Content-Type`; cualquier otro dato va en el cuerpo.

## Roles y protección de rutas

Roles: `GUEST` (todo usuario registrado), `HOST` (publica alojamientos) y `ADMIN`. En Spring se exponen como
`ROLE_GUEST`, `ROLE_HOST` y `ROLE_ADMIN` y se exigen con `@PreAuthorize("hasRole('…')")`.

| Nivel | Cómo se protege | Rutas |
|---|---|---|
| Público | `permitAll` en `SecurityConfig`, solo `GET` | `/api/health`, `/actuator/health`, `/api/v1/properties/*/availability`, `/api/v1/properties/*/reviews` |
| Autenticado | JWT válido (regla por defecto de `/api/**`) | el resto de `/api/v1/**` |
| Por rol | `@PreAuthorize` en el controlador | operaciones de `HOST` y `ADMIN` |
| Por dueño | comprobación en el servicio con `CurrentUser` (`403` si no corresponde) | reservas, conversaciones, reseñas, alojamientos propios |

Las rutas públicas nuevas se agregan explícitamente en `SecurityConfig` (con método `GET` y patrón exacto). Rutas
como `/api/v1/me/**` nunca son públicas. Cualquier ruta fuera de `/api/**` se rechaza.

## Paginación

- Parámetros `page` (desde 0, por defecto 0), `size` (de 1 a 100, por defecto 20) y, cuando el endpoint lo
  admite, `sort` (`campo,asc|desc`, con una lista cerrada de campos permitidos por endpoint).
- Valores fuera de rango se ajustan al límite (no producen error).
- Respuesta con `com.xelastay.common.PageResponse`:

```json
{ "content": [ … ], "page": 0, "size": 20, "totalElements": 57, "totalPages": 3 }
```

Nunca se serializa `Page`/`PageImpl` de Spring Data.

## Datos

| Tipo | Formato |
|---|---|
| Momento (`TIMESTAMPTZ`) | ISO-8601 en UTC: `2026-11-04T15:30:00Z` |
| Día (`DATE`) | `yyyy-MM-dd`: `2026-11-20` |
| Dinero (`NUMERIC(12,2)`) | número decimal con 2 cifras (`350.00`) acompañado de su moneda ISO 4217 (`"currency": "GTQ"`). Los montos los calcula el servidor. |
| Estados | texto en mayúsculas, igual al `CHECK` de V1 (`PENDING`, `PUBLISHED`…) |
| Nombres de campo JSON | camelCase (`checkIn`, `pricePerNight`) |

## Errores

Toda respuesta de error usa `ApiError`:

```json
{
  "timestamp": "2026-11-04T15:30:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "La solicitud contiene datos inválidos",
  "path": "/api/v1/bookings",
  "details": [ { "field": "guestsCount", "message": "debe ser mayor que 0" } ]
}
```

| Código | Cuándo |
|---|---|
| `400` | validación (`jakarta.validation`, con `details` por campo) o regla de datos |
| `401` | sin token o token inválido (con `WWW-Authenticate`) |
| `403` | autenticado pero sin rol o sin relación con el recurso |
| `404` | no existe o no es visible para el usuario |
| `409` | conflicto de estado, de unicidad o de fechas |
| `500` | error no controlado (mensaje genérico; el detalle solo va al log) |

Los servicios lanzan `ResponseStatusException` con el código y un mensaje en español;
`GlobalExceptionHandler` lo convierte a `ApiError`.

## Implementación por módulo

- Capas: `entity`, `repository`, `service`, `controller` y `dto` (records). Las entidades nunca salen del servicio.
- Entidades JPA idénticas a V1 (Hibernate en `validate`): UUID → `UUID`, `NUMERIC` → `BigDecimal`,
  `TIMESTAMPTZ` → `OffsetDateTime` o `Instant`, `JSONB` → `@JdbcTypeCode(SqlTypes.JSON)`, `CHECK` de estados →
  `enum` con `@Enumerated(EnumType.STRING)`.
- Validación de entrada con `jakarta.validation` en los DTO y `@Valid` en el controlador.
- Pruebas: servicio con mocks; controlador con `@SpringBootTest` + `@AutoConfigureMockMvc`, perfil `test` y
  `jwt()` de `spring-security-test`, con el servicio como `@MockitoBean`.

## Revisión de lo existente (módulos de Giovanni)

Revisado sin cambiar código. Cumple las reglas de rutas, autenticación, `PageResponse`, `ApiError`, fechas y dinero.
Diferencias anotadas:

| # | Diferencia | Dónde | Comentario |
|---|---|---|---|
| 1 | No admite `sort`; cada listado tiene un orden fijo (más recientes primero). | listados de reservas, reseñas, chat, notificaciones | Válido: `sort` es opcional por endpoint. Documentar el orden fijo. |
| 2 | Las transiciones son acciones (`PATCH /bookings/{id}/confirm`) en lugar de `PATCH …/status`. | reservas | Ambos estilos se aceptan; las acciones encajan cuando cada transición tiene reglas propias. |
| 3 | Subrecurso en singular: `POST /bookings/{bookingId}/review`. | reseñas | Aceptado: relación 1 a 1 (una reseña por reserva). |
| 4 | Las entidades usan `Instant` para `TIMESTAMPTZ` en lugar de `OffsetDateTime`. | todas | Equivalente para Hibernate y para el JSON (ISO-8601 UTC). Los módulos nuevos usan `OffsetDateTime`. |
| 5 | `size` por defecto 50 en los mensajes del chat. | chat | Aceptado y documentado en ENDPOINTS.md. |
| 6 | Códigos `502`/`503`/`504` cuando falla el proveedor de IA. | IA | Aceptado: errores del proveedor externo. |
| 7 | ~~El rol ADMIN se validaba con `AdminGuard` (SQL nativo).~~ | IA | Resuelto: `AdminGuard` se eliminó y `AiController` usa `@PreAuthorize("hasRole('ADMIN')")`. |
| 8 | Pruebas de controlador con `@SpringBootTest` + MockMvc, no `@WebMvcTest`. | todas | Es el patrón vigente del repositorio; los módulos nuevos lo siguen. |

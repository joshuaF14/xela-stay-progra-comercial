# Auditoría

Las operaciones que cambian datos de negocio quedan en la tabla `audit_logs` de V1. El registro es de solo
inserción: nunca se modifica ni se borra desde la API.

## Diseño

```text
Servicio de negocio ──(misma transacción)──> AuditService.record(...) ──> audit_logs
                                              │
                                              ├─ actor: CurrentUser (claim sub) o el que indique el llamador
                                              ├─ ip_address: primera IP de X-Forwarded-For o la dirección remota
                                              └─ user_agent: encabezado User-Agent (máx. 500 caracteres)
```

- **`com.xelastay.audit.AuditService`** es el único punto de escritura. Se une a la transacción en curso
  (`@Transactional`, propagación `REQUIRED`): si la operación falla y se revierte, su registro de auditoría también.
- Se eligieron **llamadas explícitas** en los servicios (no un aspecto con `@Audited`): cada llamada dice qué se
  registra y con qué valores anteriores y nuevos, y es fácil de seguir y de probar con mocks.
- `oldValues` y `newValues` se guardan como `jsonb`. Se pasan como `Map` o como un record (se convierte con Jackson).
  Solo se registran campos de negocio; nunca contraseñas, tokens ni el email del perfil.
- La IP solo se guarda si tiene forma de IPv4/IPv6 (si no, queda `null`), para que el `CAST` a `inet` no falle.
- Sin petición HTTP (tareas internas), IP y user agent quedan `null`.

### API de `AuditService`

| Método | Uso |
|---|---|
| `record(actorId, action, entityType, entityId, oldValues, newValues, source)` | Registro completo; IP y user agent de la petición actual. |
| `record(…, source, ipAddress, userAgent)` | Igual, con IP y user agent dados (lo usa `AiLogWriter`). |
| `recordAction(action, entityType, entityId, oldValues, newValues, ownerId)` | Actor = usuario del token. `source = ADMIN` si es ADMIN y el recurso no es suyo (`ownerId` distinto, o `null` en recursos compartidos como el catálogo); `USER` en los demás casos. |
| `recordUserAction(action, entityType, entityId, oldValues, newValues)` | Actor = usuario del token; `source = USER` (reservas). |

### Origen (`source`)

| Valor | Cuándo |
|---|---|
| `USER` | Un usuario actúa sobre lo suyo (su perfil, sus alojamientos, sus pagos, sus reservas) o un HOST crea una ubicación. |
| `ADMIN` | Un ADMIN actúa sobre algo ajeno o compartido (perfiles de otros, roles, catálogo, alojamientos de otros, reembolsos). |
| `AI` | Interacciones del agente (`AI_CHAT`). |
| `SYSTEM` | Reservado para tareas internas (por ejemplo, la expiración de reservas `PENDING`). |

## Operaciones registradas

| `entity_type` | `action` | Valores |
|---|---|---|
| `profile` | `PROFILE_UPDATED` | antes/después de `fullName`, `phone`, `avatarUrl` |
| `profile` | `PROFILE_ROLE_ADDED` | `{role: "HOST"}` (`POST /me/host`) |
| `profile` | `PROFILE_STATUS_CHANGED` | `{status}` antes/después |
| `profile` | `PROFILE_ROLES_REPLACED` | `{roles}` antes/después |
| `role` | `ROLE_CREATED`, `ROLE_UPDATED`, `ROLE_DELETED` | rol antes/después |
| `property_category` | `CATEGORY_CREATED`, `CATEGORY_UPDATED`, `CATEGORY_DEACTIVATED` | categoría antes/después |
| `amenity` | `AMENITY_CREATED`, `AMENITY_UPDATED`, `AMENITY_DEACTIVATED` | amenidad antes/después |
| `location` | `LOCATION_CREATED`, `LOCATION_UPDATED` | ubicación antes/después (también al crearla junto con un alojamiento) |
| `property` | `PROPERTY_CREATED`, `PROPERTY_UPDATED`, `PROPERTY_STATUS_CHANGED`, `PROPERTY_DELETED`, `PROPERTY_AMENITIES_REPLACED` | campos de negocio, `{status}`, `{deletedAt}` o `{amenityIds}` |
| `property_image` | `PROPERTY_IMAGE_ADDED`, `PROPERTY_IMAGE_UPDATED`, `PROPERTY_IMAGE_DELETED` | `propertyId`, `imageUrl`, `caption`, `displayOrder`, `cover` |
| `payment` | `PAYMENT_CREATED`, `PAYMENT_REFUNDED` | pago creado; `{status}` antes/después |
| `booking` | `BOOKING_CREATED`, `BOOKING_CONFIRMED`, `BOOKING_REJECTED`, `BOOKING_CANCELLED`, `BOOKING_COMPLETED` | `{status, cancellationReason}` |
| `knowledge_document` | `AI_DOCUMENT_INGESTED` | título, tipo y cantidad de fragmentos (`source = ADMIN`) |
| `ai_interaction` | `AI_CHAT` | pregunta y cantidad de fuentes (`source = AI`) |

Las reservas se auditan desde `BookingService.publish()`, el mismo punto que escribe el evento del outbox, con un solo
cambio en el módulo de reservas. `AiLogWriter` conserva su firma y sus valores por defecto (`0.0.0.0` y
`desconocido` cuando faltan IP o user agent) y delega la escritura en `AuditService`.

## Consulta

`GET /api/v1/audit-logs` (solo ADMIN), más recientes primero, paginado.

| Parámetro | Ejemplo |
|---|---|
| `entityType` | `property` |
| `entityId` | UUID |
| `actorId` | UUID |
| `source` | `USER`, `ADMIN`, `AI`, `SYSTEM` |
| `from`, `to` | `2026-11-01`, `2026-11-30` (días en hora de Guatemala, ambos incluidos) |
| `page`, `size` | `0`, `20` |

```json
{
  "content": [
    {
      "id": "…", "actorId": "…", "actorName": "Ana López",
      "action": "PROPERTY_STATUS_CHANGED", "entityType": "property", "entityId": "…",
      "oldValues": { "status": "DRAFT" }, "newValues": { "status": "PUBLISHED" },
      "ipAddress": "190.56.1.2", "userAgent": "Mozilla/5.0 …", "source": "USER",
      "createdAt": "2026-11-04T15:30:00Z"
    }
  ],
  "page": 0, "size": 20, "totalElements": 1, "totalPages": 1
}
```

Verificación rápida en el SQL Editor:

```sql
select action, entity_type, source, ip_address, created_at
from audit_logs order by created_at desc limit 20;
```

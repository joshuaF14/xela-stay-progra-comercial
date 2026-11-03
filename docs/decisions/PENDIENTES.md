# Decisiones pendientes

Decisiones abiertas que bloquean o condicionan la implementación. Cada una debe cerrarse con un acuerdo del equipo y,
si afecta al esquema, con una migración Flyway nueva (V3 en adelante; V1 y V2 ya están aplicadas y no se editan).

El "área" indica a quién corresponde según la distribución de [AGENTS.md](../../AGENTS.md).

## Producto y reglas de negocio

| # | Decisión | Área | Estado |
|---|----------|------|--------|
| 1 | **Roles iniciales**: catálogo de `roles` y rol al registrarse. | Joshua | **Resuelta**: V2 crea GUEST, HOST y ADMIN; todo perfil nuevo recibe GUEST y se vuelve HOST con `POST /me/host`. El primer ADMIN se asigna por SQL ([AUTENTICACION.md](../architecture/AUTENTICACION.md#crear-el-primer-admin)). |
| 2 | **Expiración de reservas `PENDING`**: plazo, quién la ejecuta y estado final. | Giovanni | **Resuelta**: vence a las 24 horas de creada o al llegar el día de entrada (lo que pase primero, hora de Guatemala). La ejecuta una tarea programada del backend cada 15 minutos (`PendingBookingExpirer`). Queda `CANCELLED` con el motivo "Venció: el anfitrión no respondió a tiempo" (V1 no tiene estado `EXPIRED`), avisa como cancelación y se audita como `BOOKING_EXPIRED` con `source = SYSTEM`. Si tenía un pago aprobado, el reembolso lo hace un ADMIN, igual que en cualquier cancelación. |
| 3 | **Pagos simulados**: métodos, aprobación y efecto sobre la reserva. | Giovanni / Joshua | **Resuelta**: métodos `CARD`, `TRANSFER` y `CASH`; aprobado salvo `simulateFailure = true`; un solo pago `APPROVED` por reserva; **el pago no cambia la reserva** (la confirma el anfitrión); el reembolso lo hace un ADMIN y tampoco cambia la reserva. |
| 4 | **Borrado de usuarios**: borrado físico, desactivación o anonimización. | Joshua | Abierta. Hoy un ADMIN puede poner el perfil en `INACTIVE` o `SUSPENDED` (queda bloqueado en la API). |
| 5 | **Lista de los 15 reportes**: definición, fuentes y vistas `reporting_*`. | Giovanni (apoyo) / Joshua | Abierta. Ya existen `reporting_bookings` y `reporting_property_ratings` (esta se usa para la calificación de los alojamientos). |
| 6 | **Embeddings**: proveedor, modelo, dimensión y `pgvector`. | Giovanni | Abierta. La IA usa búsqueda de texto completo en español mientras tanto. |
| 7 | **Estados de `knowledge_documents.status`** (`PENDING`, `PROCESSING`, `INDEXED`, `FAILED`). | Giovanni | Pendiente de aprobación (el agente usa `INDEXED`). |
| 18 | **Moderación de alojamientos**: hoy un HOST publica sin revisión previa; un ADMIN puede pasarlos a `REJECTED` y solo un ADMIN los saca de ese estado. | Joshua / equipo | Por confirmar. |
| 19 | **Retención de `audit_logs`**: hoy no se borra nada. | Joshua | Abierta. |

## Reglas agregadas al implementar (confirmar con el equipo)

No estaban en el requerimiento; se agregaron para evitar estados inválidos.

| # | Regla | Módulo |
|---|-------|--------|
| 20 | Un ADMIN no puede cambiar su propio estado ni quitarse su propio rol ADMIN (`409`), para no quedarse sin administradores. | perfiles |
| 21 | GUEST, HOST y ADMIN no se renombran ni se borran; un rol asignado no se borra (`409`). | roles |
| 22 | `locations` no tiene dueño en V1: un HOST no puede editar una ubicación que usen alojamientos de otro anfitrión (`403`). | catálogo |
| 23 | Categorías y amenidades no se borran: `DELETE` las desactiva. Un alojamiento conserva una categoría o amenidad ya desactivada, pero no puede elegir una nueva inactiva. | catálogo / alojamientos |
| 24 | La primera imagen se vuelve portada; al borrar la portada pasa a la siguiente; un alojamiento publicado no puede quedarse sin imágenes (`409`). | alojamientos |
| 25 | No se borra (lógicamente) un alojamiento con reservas `PENDING` o `CONFIRMED` que aún no terminan (`409`). | alojamientos |

## Observaciones del esquema V1

| # | Observación | Impacto | Área |
|---|-------------|---------|------|
| 8 | `profiles.id → auth.users(id)` con `ON DELETE RESTRICT`. | Supabase Auth no podrá borrar un usuario que tenga perfil. Depende de la decisión 4. | Joshua |
| 9 | `profiles.email` duplica `auth.users.email`. | Se copia al crear el perfil; si el usuario cambia su correo en Auth, no se actualiza. Definir si se sincroniza. | Joshua |
| 10 | `reviews.property_id` es redundante con `reviews.booking_id → bookings.property_id`. | Mitigado: el backend lo copia siempre de la reserva. | Giovanni |
| 11 | No hay triggers para `updated_at`. | **Resuelta en JPA**: las entidades con `updated_at` lo actualizan con `@PreUpdate`. Los cambios hechos por SQL directo no lo actualizan. | Joshua |
| 12 | Sin protección en base de datos contra reservas solapadas. | Mitigado en el backend: la reserva bloquea el alojamiento (`FOR UPDATE`) y valida reservas y bloqueos. Queda abierta la restricción `EXCLUDE USING gist` (requiere `btree_gist`). | Giovanni / Joshua |

## Seguridad, operación y roles de base de datos

| # | Decisión | Área |
|---|----------|------|
| 13 | Crear el usuario dedicado para Flyway y confirmar que es propietario de los objetos de aplicación. | Joshua |
| 14 | Rol con el que se conecta el backend: propietario de las tablas o rol con `BYPASSRLS` (con RLS sin políticas, otro rol no ve filas). | Joshua |
| 15 | Rol de solo lectura para Power BI: por `security_invoker`, necesita políticas `SELECT` o `BYPASSRLS` para leer las vistas `reporting_*`. | Joshua / Giovanni |
| 16 | ~~Tablas futuras: `ALTER DEFAULT PRIVILEGES`.~~ Resuelto en V1 para objetos creados por `postgres`; cada tabla nueva sigue necesitando `ENABLE ROW LEVEL SECURITY` en su migración. | Joshua |
| 17 | Firma de JWT de Supabase: el backend solo valida llaves asimétricas (JWKS). Confirmar en **Project Settings → JWT Keys** que la llave actual no es la heredada HS256 ([AUTENTICACION.md](../architecture/AUTENTICACION.md)). | Joshua |
| 26 | El Session pooler admite 15 clientes para todo el proyecto: cada backend usa `DB_POOL_SIZE` (3 por defecto). Para la demo o un despliegue, evaluar la conexión directa o un plan con más conexiones. | Joshua |

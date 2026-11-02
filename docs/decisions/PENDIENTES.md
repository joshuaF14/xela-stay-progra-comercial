# Decisiones pendientes

Decisiones abiertas que bloquean o condicionan la implementación. Cada una debe cerrarse con un acuerdo del equipo y, si afecta al esquema, con una migración Flyway nueva (o editando V1 mientras no se haya aplicado en Supabase).

El "área" indica a quién corresponde según la distribución de [AGENTS.md](../../AGENTS.md).

## Producto y reglas de negocio

| # | Decisión | Área | Estado |
|---|----------|------|--------|
| 1 | **Roles iniciales**: catálogo de `roles` (nombres, descripción) y rol que recibe un usuario al registrarse. | Joshua | Abierta |
| 2 | **Expiración de reservas `PENDING`**: plazo, quién la ejecuta (tarea programada, RabbitMQ) y estado final. | Giovanni | Abierta |
| 3 | **Pagos simulados**: métodos permitidos en `payments.payment_method`, cómo se aprueba o rechaza un pago y su efecto sobre la reserva. | Giovanni / Joshua | Abierta |
| 4 | **Borrado de usuarios**: borrado físico, desactivación (`profiles.status`) o anonimización, y qué ocurre con reservas, reseñas, mensajes y auditoría. | Joshua | Abierta |
| 5 | **Lista de los 15 reportes**: definición de cada reporte, fuentes y vistas `reporting_*` necesarias. | Giovanni (apoyo) / Joshua | Abierta |
| 6 | **Embeddings**: proveedor, modelo y dimensión del vector; habilitación de `pgvector` y columna `embedding` en `knowledge_chunks` (migración posterior). | Giovanni | Abierta |
| 7 | **Estados de `knowledge_documents.status`**: V1 incluye como propuesta `CHECK (status IN ('PENDING','PROCESSING','INDEXED','FAILED'))`. | Giovanni | Pendiente de aprobación |

## Observaciones del esquema V1 no modificadas

| # | Observación | Impacto | Área |
|---|-------------|---------|------|
| 8 | `profiles.id → auth.users(id)` con `ON DELETE RESTRICT`. | Supabase Auth no podrá borrar un usuario que tenga perfil. Depende de la decisión 4. | Joshua |
| 9 | `profiles.email` duplica `auth.users.email`. | Puede desincronizarse si el usuario cambia su correo en Auth. Definir si se elimina, se sincroniza o se trata como copia. | Joshua |
| 10 | `reviews.property_id` es redundante con `reviews.booking_id → bookings.property_id`. | Nada impide que ambos valores difieran. Eliminar la columna o validar la coherencia. | Giovanni |
| 11 | No hay triggers para `updated_at`. | La columna solo toma `now()` al insertar. Definir si se actualiza con trigger o desde JPA (`@PreUpdate`/`@UpdateTimestamp`). | Joshua |
| 12 | Sin protección en base de datos contra reservas solapadas. | Dos reservas activas pueden cruzar fechas en la misma propiedad, y nada valida el cruce con `availability_blocks`. Opciones: `EXCLUDE USING gist` (requiere `btree_gist`) y/o bloqueo transaccional en backend. | Giovanni / Joshua |

## Seguridad y roles de base de datos

| # | Decisión | Área |
|---|----------|------|
| 13 | Crear el usuario dedicado para Flyway y confirmar que es propietario de los objetos de aplicación. | Joshua |
| 14 | Rol con el que se conecta el backend: propietario de las tablas o rol con `BYPASSRLS` (con RLS sin políticas, otro rol no ve filas). | Joshua |
| 15 | Rol de solo lectura para Power BI: por `security_invoker`, necesita políticas `SELECT` o `BYPASSRLS` para leer las vistas `reporting_*`. | Joshua / Giovanni |
| 16 | ~~Tablas futuras: `ALTER DEFAULT PRIVILEGES`.~~ Resuelto en V1 para objetos creados por `postgres`; cada tabla nueva sigue necesitando `ENABLE ROW LEVEL SECURITY` en su migración. | Joshua |
| 17 | Firma de JWT de Supabase: confirmar que el proyecto usa llaves asimétricas (JWKS). Si usa el secreto heredado HS256, decidir entre migrar a llaves asimétricas (recomendado) o soportar HS256 en el backend. Ver [AUTENTICACION.md](../architecture/AUTENTICACION.md). | Joshua |

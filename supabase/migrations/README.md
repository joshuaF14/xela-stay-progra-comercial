# supabase/migrations

Esta carpeta no contiene las migraciones del esquema de Xela Stay.

- Las migraciones viven en `backend/src/main/resources/db/migration` y las administra **Flyway**.
- **No se usa la CLI de Supabase** (`supabase db push`, `supabase migration new`, etc.) para este esquema. Usar dos mecanismos sobre las mismas tablas generaría historiales en conflicto.

Procedimiento: [docs/database/SUPABASE_FLYWAY.md](../../docs/database/SUPABASE_FLYWAY.md).

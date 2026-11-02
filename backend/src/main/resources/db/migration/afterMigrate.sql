-- Callback de Flyway: se ejecuta después de cada migrate, cuando las migraciones ya están confirmadas.
-- Flyway crea flyway_schema_history antes de ejecutar V1 y la tabla hereda los privilegios por
-- defecto de Supabase; aquí se le activa RLS y se retiran los privilegios a los roles de la API.
-- Ambas sentencias se pueden repetir sin efecto adicional.
ALTER TABLE flyway_schema_history ENABLE ROW LEVEL SECURITY;
REVOKE ALL ON flyway_schema_history FROM anon, authenticated;

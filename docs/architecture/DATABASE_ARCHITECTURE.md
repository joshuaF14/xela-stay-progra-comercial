# Arquitectura de acceso a datos

## Componentes

- Supabase aloja PostgreSQL y gestiona Supabase Auth. `profiles.id` comparte UUID con `auth.users.id`; no se almacenan contraseñas en el esquema de la aplicación.
- Spring Boot 3.5.x / Java 21 es la API REST y la capa de reglas comerciales. Spring Data JPA accede a PostgreSQL mediante el driver JDBC.
- Flyway es la fuente única de cambios versionados del esquema de aplicación. Las migraciones viven en `backend/src/main/resources/db/migration`; Hibernate está configurado en `validate`, no para crear/actualizar tablas.
- React consume la API REST. No debe conectarse con credenciales privilegiadas ni modificar directamente datos sensibles.
- RabbitMQ se integra posteriormente mediante `outbox_events`, para publicar eventos después de confirmar la transacción en PostgreSQL.

## Límites y flujo

```text
React/TypeScript -> API REST Spring Boot -> Supabase PostgreSQL
                                      -> Supabase Auth (validación JWT por configurar)
PostgreSQL outbox_events -> publicador backend -> RabbitMQ (por implementar)
Flyway (backend, ejecución controlada) -> esquema de aplicación PostgreSQL
```

Las operaciones protegidas deben validar JWT de Supabase y derivar el perfil del sujeto autenticado, no confiar en un UUID de usuario enviado por el cliente. Para una reserva, el backend deberá realizar la comprobación de disponibilidad y escritura dentro de una transacción, bloqueando la fila de `properties`; RabbitMQ no sustituye esta protección.

## Conexión y cambios

La configuración usa `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` y `FLYWAY_ENABLED`. En local, Spring las toma del `.env` de la raíz mediante `spring.config.import` (las variables de entorno del sistema tienen prioridad). Flyway se encuentra apagado por defecto. No guardar credenciales reales en Git. La guía de selección de endpoint, carga del `.env`, respaldo, preflight y autorización está en [SUPABASE_FLYWAY.md](../database/SUPABASE_FLYWAY.md).

La migración inicial referencia `auth.users` (servicio gestionado ya existente), pero no crea objetos dentro de `auth` ni activa extensiones.

## Seguridad y roles

- RLS activado sin políticas en las 22 tablas y `REVOKE ALL` a `anon` y `authenticated` sobre tablas y vistas: el frontend y la API de datos de Supabase no acceden directamente al esquema.
- Las vistas de reporte usan `security_invoker = true`, de modo que respetan permisos y RLS del rol que consulta.
- Roles previstos (documentados, no creados): un usuario dedicado para Flyway y propietario de los objetos, el backend conectado como propietario o con `BYPASSRLS`, y un rol de solo lectura para Power BI limitado a las vistas `reporting_*`, que necesitará políticas `SELECT` o `BYPASSRLS` por el efecto de `security_invoker`.

Detalle en [SUPABASE_FLYWAY.md](../database/SUPABASE_FLYWAY.md#seguridad-del-esquema); decisiones abiertas en [PENDIENTES.md](../decisions/PENDIENTES.md).

# Guía del repositorio

## Proyecto

Xela Stay es una plataforma de reservas de alojamientos con mapas, chat, reseñas, IA (RAG) y procesamiento asíncrono de reservas. Es el proyecto final del curso Programación Comercial. Actualmente el repositorio es un esqueleto: no hay endpoints ni lógica de negocio implementada.

## Stack fijo (no cambiar)

- Backend: Java 21, Spring Boot 3.5, Maven, Spring Data JPA, Flyway.
- Frontend: React, TypeScript, Vite.
- Datos y autenticación: Supabase PostgreSQL y Supabase Auth (JWT validado en Spring).
- Mensajería: RabbitMQ.
- Contenedores: Docker.

## Estructura de carpetas

```text
backend/                         API Spring Boot (paquete com.xelastay)
  src/main/resources/
    application.properties       Configuración; lee variables y el .env local
    db/migration/                Migraciones Flyway (V<n>__<descripcion>.sql)
frontend/                        App React + TypeScript + Vite
supabase/migrations/             Sin uso para el esquema (ver su README.md)
docs/
  api/                           Contratos de API
  architecture/                  Decisiones de arquitectura
  database/                      Base de datos y procedimiento Flyway
  decisions/                     Decisiones abiertas (PENDIENTES.md)
  technical-manual/              Manual técnico
infra/rabbitmq/                  Configuración de RabbitMQ
docker-compose.yml               Servicios locales (aún vacío)
.env.example                     Plantilla de variables sin valores reales
```

Mantén la separación entre `backend/`, `frontend/`, `supabase/`, `docs/` e `infra/`. Documenta decisiones de arquitectura, API, datos y operación en su carpeta correspondiente.

## Reglas obligatorias para agentes

- No hacer commits, pushes, merges, rebases ni crear, cambiar o borrar ramas. Todo lo de Git lo hace el equipo manualmente.
- No ejecutar migraciones, no conectarse a Supabase ni correr Flyway. `FLYWAY_ENABLED` debe permanecer en `false`.
- No leer, mostrar ni copiar valores de `.env` ni contraseñas. Para saber si una variable existe, revisar solo su nombre.
- No incluir secretos en archivos versionados; `.env.example` solo lleva marcadores de posición.
- No instalar dependencias ni ejecutar `npm install` o `mvn` sin aprobación previa.
- No inventar endpoints, tablas, estados ni reglas de negocio. No agregar lógica de negocio sin un requerimiento explícito.
- Trabajar por bloques: al terminar cada bloque, mostrar el diff y esperar confirmación antes de continuar.

## Base de datos y Flyway

Flyway es el único mecanismo para versionar el esquema. Las migraciones viven en `backend/src/main/resources/db/migration`; no se usa la CLI de Supabase ni el Dashboard para crear tablas. Una migración ya aplicada no se edita: cualquier cambio es una nueva versión. El procedimiento completo (respaldo, verificación previa y autorización) está en [docs/database/SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md).

## Flujo de ramas

```text
feat/* ──PR──> develop ──PR──> main
```

- Cada funcionalidad se trabaja en una rama `feat/*` creada desde `develop`.
- Toda integración a `develop` y a `main` se hace mediante Pull Request revisado.
- No se hace push directo a `develop` ni a `main`.

## Responsables

| Persona  | Áreas |
|----------|-------|
| Joshua   | Arquitectura, backend, base de datos, autenticación, seguridad e integración |
| Pedro    | Frontend, UX, mapas e integración con la API |
| Giovanni | Reservas, disponibilidad, RabbitMQ, chat, reseñas, IA/RAG y apoyo a reportes y auditoría |

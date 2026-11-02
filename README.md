# xela-stay-progra-comercial

Plataforma de reservas de alojamientos con mapas, chat, reseñas, IA y procesamiento asíncrono de reservas. Proyecto final de Programación Comercial.

## Estado actual

El repositorio es todavía un **esqueleto**:

- Backend: clase principal de Spring Boot, configuración de datos y la migración inicial `V1` (no aplicada en Supabase). Aún no incluye `spring-boot-starter-web`, por lo que no expone endpoints.
- Frontend: proyecto Vite + React mínimo.
- `docker-compose.yml` sin servicios definidos; RabbitMQ pendiente.
- Decisiones abiertas en [docs/decisions/PENDIENTES.md](docs/decisions/PENDIENTES.md).

## Stack

- Java 21, Spring Boot 3.5, Maven, Spring Data JPA, Flyway
- React, TypeScript, Vite
- Supabase PostgreSQL y Supabase Auth (JWT validado en Spring)
- RabbitMQ
- Docker

## Requisitos

- JDK 21
- Maven (el proyecto no incluye Maven Wrapper)
- Node.js LTS y npm
- Docker (para servicios locales como RabbitMQ, cuando se definan)

## Variables de entorno

La lista de variables está en [.env.example](.env.example). Para desarrollo local:

1. Copiar `.env.example` como `.env` en la raíz del repositorio (está ignorado por Git).
2. Completar los valores reales de Supabase. Mantener `FLYWAY_ENABLED=false`.

El backend lee ese archivo mediante `spring.config.import` (desde la raíz o desde `backend/`). Las variables de entorno del sistema tienen prioridad. El `.env` se interpreta como archivo `.properties`: sin `export` ni comillas. `SPRING_PROFILES_ACTIVE` y `SERVER_PORT` solo tienen efecto si se definen como variables de entorno reales. Vite no lee el `.env` de la raíz; usa `frontend/.env` para `VITE_API_URL`.

Nunca subir credenciales al repositorio.

## Ejecución

### Backend

```bash
cd backend
mvn spring-boot:run
```

Hibernate está en modo `validate`, así que el arranque necesita una base de datos accesible con el esquema ya creado. Flyway está desactivado por defecto; ver [docs/database/SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md) antes de aplicar migraciones.

### Frontend

```bash
cd frontend
npm install
npm run dev
```

## Documentación

- [AGENTS.md](AGENTS.md): reglas del repositorio, flujo de ramas y responsables.
- [docs/architecture/DATABASE_ARCHITECTURE.md](docs/architecture/DATABASE_ARCHITECTURE.md): arquitectura de acceso a datos.
- [docs/database/SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md): procedimiento de migraciones.

# xela-stay-progra-comercial

Plataforma de reservas de alojamientos con mapas, chat, reseñas, IA y procesamiento asíncrono de reservas. Proyecto final de Programación Comercial.

## Estado actual

El repositorio es todavía un **esqueleto**:

- Base de datos: migración `V1` aplicada en Supabase el 2026-11-02 (ver [registro](docs/database/SUPABASE_FLYWAY.md#registro-de-migraciones-aplicadas)).
- Backend: API base con `GET /api/health`, `/actuator/health`, manejo global de errores, CORS y seguridad stateless. Los módulos de negocio son paquetes vacíos.
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
- Maven no es necesario: el backend incluye Maven Wrapper (`mvnw` / `mvnw.cmd`), que descarga la versión fijada en `backend/.mvn/wrapper/maven-wrapper.properties` la primera vez que se ejecuta
- Node.js LTS y npm
- Docker (para servicios locales como RabbitMQ, cuando se definan)

## Variables de entorno

La lista de variables está en [.env.example](.env.example). Para desarrollo local:

1. Copiar `.env.example` como `.env` en la raíz del repositorio (está ignorado por Git).
2. Completar los valores reales de Supabase. Mantener `FLYWAY_ENABLED=false`.

El backend lee ese archivo mediante `spring.config.import` (desde la raíz o desde `backend/`). Las variables de entorno del sistema tienen prioridad. El `.env` se interpreta como archivo `.properties`: sin `export` ni comillas. `SPRING_PROFILES_ACTIVE` (por defecto `local`), `SERVER_PORT` (por defecto `8080`) y `CORS_ALLOWED_ORIGINS` (por defecto `http://localhost:5173`) se leen desde el `.env` o el entorno. Vite no lee el `.env` de la raíz; usa `frontend/.env` para `VITE_API_URL`.

Nunca subir credenciales al repositorio.

## Ejecución

### Backend

```powershell
# Windows (PowerShell)
cd backend
.\mvnw spring-boot:run
```

```bash
# Linux / macOS
cd backend
./mvnw spring-boot:run
```

Hibernate está en modo `validate`, así que el arranque necesita una base de datos accesible con el esquema ya creado. Flyway está desactivado por defecto; ver [docs/database/SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md) antes de aplicar migraciones.

Comprobación: `http://localhost:8080/api/health` y `http://localhost:8080/actuator/health` (el componente `db` debe estar `UP`).

Pruebas (perfil `test`, sin base de datos):

```powershell
cd backend
.\mvnw test
```

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

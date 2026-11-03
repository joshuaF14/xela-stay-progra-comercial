# xela-stay-progra-comercial

Plataforma de reservas de alojamientos en Quetzaltenango con mapas, chat, reseñas, pagos simulados, IA (RAG) y
procesamiento asíncrono de reservas. Proyecto final de Programación Comercial.

## Estado actual

| Área | Estado |
|---|---|
| Base de datos | Migraciones `V1` (esquema, RLS y vistas de reporte) y `V2` (roles GUEST/HOST/ADMIN y catálogo base) aplicadas en Supabase ([registro](docs/database/SUPABASE_FLYWAY.md#registro-de-migraciones-aplicadas)). |
| Seguridad | JWT de Supabase validado en Spring (JWKS); perfil automático en la primera petición; roles con `@PreAuthorize`; perfiles suspendidos bloqueados. |
| Perfiles y roles | `/me`, administración de perfiles y CRUD de roles. |
| Catálogo | Categorías, amenidades y ubicaciones con lectura pública. |
| Alojamientos | Búsqueda pública con filtros y mapa, detalle, gestión por anfitrión, imágenes y amenidades. |
| Reservas y disponibilidad | Reservas con bloqueo transaccional, transiciones de estado y calendario público. |
| Eventos | Outbox → RabbitMQ → notificaciones ([EVENTOS.md](docs/architecture/EVENTOS.md)). |
| Reseñas, chat y notificaciones | Disponibles. |
| Pagos | Simulados por reserva; reembolso por ADMIN (el pago no confirma la reserva). |
| IA | Agente RAG con Gemini y búsqueda de texto completo en español. |
| Auditoría | Operaciones clave en `audit_logs` y consulta para ADMIN ([AUDITORIA.md](docs/architecture/AUDITORIA.md)). |
| Frontend | React + Vite con catálogo, mapa, detalle, reservas, panel de anfitrión e inicio de sesión con Supabase. |
| **Producción** | **Desplegado en Render**: frontend https://xela-stay-web.onrender.com y API https://xela-stay-api.onrender.com ([DESPLIEGUE.md](docs/technical-manual/DESPLIEGUE.md)). |

Decisiones abiertas: [docs/decisions/PENDIENTES.md](docs/decisions/PENDIENTES.md).

## Stack

- Java 21, Spring Boot 3.5, Maven, Spring Data JPA, Flyway
- React, TypeScript, Vite
- Supabase PostgreSQL y Supabase Auth (JWT validado en Spring)
- RabbitMQ
- Docker

## Requisitos

- JDK 21 (Temurin)
- Maven no es necesario: el backend incluye Maven Wrapper (`mvnw` / `mvnw.cmd`).
- Node.js 24 y npm
- Docker Desktop (para RabbitMQ)
- Un usuario de Supabase Auth con el email confirmado

## Variables de entorno

### Backend y Docker (`.env` en la raíz)

1. Copiar [.env.example](.env.example) como `.env` en la raíz del repositorio (ignorado por Git).
2. Completar los valores reales. Las credenciales de Supabase se comparten por un canal privado.
3. Mantener `FLYWAY_ENABLED=false` (solo Joshua migra).

| Variable | Para qué |
|---|---|
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | Supabase PostgreSQL por el **Session pooler** (puerto 5432) |
| `DB_POOL_SIZE` | Conexiones por backend (por defecto 3; el pooler admite 15 en total para todo el equipo) |
| `SUPABASE_JWKS_URI`, `SUPABASE_JWT_ISSUER` | Validación del JWT ([AUTENTICACION.md](docs/architecture/AUTENTICACION.md)) |
| `RABBITMQ_*`, `EVENTS_ENABLED` | RabbitMQ local; `EVENTS_ENABLED=false` si no está corriendo |
| `AI_API_KEY`, `AI_MODEL` | Agente de IA (Gemini); sin key, `/api/v1/ai/chat` responde `503` |
| `CORS_ALLOWED_ORIGINS`, `SERVER_PORT`, `SPRING_PROFILES_ACTIVE` | Opcionales |

El backend lee ese archivo mediante `spring.config.import` (desde la raíz o desde `backend/`). Las variables de
entorno del sistema tienen prioridad. El `.env` se interpreta como `.properties`: sin `export` ni comillas.

### Frontend (`frontend/.env`)

Vite no lee el `.env` de la raíz:

```properties
VITE_API_URL=http://localhost:8080
VITE_SUPABASE_URL=https://<project-ref>.supabase.co
VITE_SUPABASE_ANON_KEY=<publishable-o-anon-key>
```

Nunca subir credenciales al repositorio.

## Ejecución local

```powershell
# 1. RabbitMQ (opcional; sin él, EVENTS_ENABLED=false)
docker compose up -d rabbitmq          # consola: http://localhost:15672

# 2. Backend
cd backend
.\mvnw spring-boot:run                 # Linux/macOS: ./mvnw spring-boot:run

# 3. Frontend (otra terminal)
cd frontend
npm ci
npm run dev                            # http://localhost:5173
```

Comprobación: `http://localhost:8080/api/health` y `http://localhost:8080/actuator/health` (el componente `db` debe
estar `UP`). Hibernate está en modo `validate`: el esquema debe existir (V1 y V2 ya están aplicadas).

### Datos de demostración

Con el backend corriendo, crea 4 alojamientos publicados en Quetzaltenango a través de la API:

```powershell
powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1
```

Detalle en [ENDPOINTS.md](docs/api/ENDPOINTS.md#datos-de-demostración). El primer ADMIN se asigna desde el SQL
Editor de Supabase ([AUTENTICACION.md](docs/architecture/AUTENTICACION.md#crear-el-primer-admin)).

### Pruebas

```powershell
cd backend
.\mvnw test                            # perfil test: sin base de datos ni RabbitMQ
```

El CI ([.github/workflows/ci.yml](.github/workflows/ci.yml)) ejecuta `./mvnw -B verify` y compila el frontend en cada
push y PR hacia `develop` y `main`.

## Despliegue

| Servicio | Plataforma | URL |
|---|---|---|
| Frontend | Render Static Site (CDN) | https://xela-stay-web.onrender.com |
| API | Render Web Service con Docker (`backend/Dockerfile`), plan Free | https://xela-stay-api.onrender.com |
| Base de datos y Auth | Supabase (Session pooler) | — |
| RabbitMQ | CloudAMQP | — |

- La infraestructura está en [render.yaml](render.yaml) (Blueprint). Se despliega solo al fusionar en `main`
  (`develop` → PR → `main`). Los secretos se cargan en el panel de Render, nunca en el repositorio.
- La API usa el perfil `prod` ([application-prod.properties](backend/src/main/resources/application-prod.properties)):
  Flyway apagado, pool pequeño, CloudAMQP por `RABBITMQ_URL`, proxy de Render y Actuator sin detalles.
- **Plan Free**: la API se apaga tras 15 minutos sin uso y tarda **~106 s** en arrancar. Antes de una demostración,
  abrir `https://xela-stay-api.onrender.com/api/health` 2-3 minutos antes.
- Verificación sin credenciales:

  ```powershell
  powershell -ExecutionPolicy Bypass -File scripts\smoke-prod.ps1 `
    -ApiUrl https://xela-stay-api.onrender.com -WebUrl https://xela-stay-web.onrender.com
  ```

- Imagen local de la API: `cd backend; docker build -t xela-stay-api .`. Todo junto en local (RabbitMQ, API y
  frontend con nginx): `docker compose --profile full up -d --build`.

Guía paso a paso (Render, Supabase, CloudAMQP, logs, despertar la API y carga de la IA):
[docs/technical-manual/DESPLIEGUE.md](docs/technical-manual/DESPLIEGUE.md).

## Documentación

| Documento | Contenido |
|---|---|
| [AGENTS.md](AGENTS.md) | Reglas del repositorio, estructura, ramas y responsables |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Flujo de PR, commits y migraciones |
| [docs/api/ENDPOINTS.md](docs/api/ENDPOINTS.md) | Contratos de todos los endpoints |
| [docs/api/CONVENCIONES.md](docs/api/CONVENCIONES.md) | Rutas, paginación, errores, fechas, dinero y roles |
| [docs/architecture/AUTENTICACION.md](docs/architecture/AUTENTICACION.md) | JWT, perfil automático, roles y primer ADMIN |
| [docs/architecture/AUDITORIA.md](docs/architecture/AUDITORIA.md) | Qué se audita y cómo se consulta |
| [docs/architecture/EVENTOS.md](docs/architecture/EVENTOS.md) | Outbox, RabbitMQ y notificaciones |
| [docs/architecture/DATABASE_ARCHITECTURE.md](docs/architecture/DATABASE_ARCHITECTURE.md) | Arquitectura de acceso a datos |
| [docs/database/SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md) | Procedimiento y registro de migraciones |
| [docs/technical-manual/DESPLIEGUE.md](docs/technical-manual/DESPLIEGUE.md) | Despliegue en Render, Supabase y CloudAMQP; verificación |
| [docs/ai/README.md](docs/ai/README.md) | Base de conocimiento del agente de IA |
| [docs/decisions/PENDIENTES.md](docs/decisions/PENDIENTES.md) | Decisiones abiertas |

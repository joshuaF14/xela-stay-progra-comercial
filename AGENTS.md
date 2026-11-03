# Guía del repositorio

## Proyecto

Xela Stay es una plataforma de reservas de alojamientos en Quetzaltenango con mapas, chat, reseñas, pagos simulados,
IA (RAG) y procesamiento asíncrono de reservas. Es el proyecto final del curso Programación Comercial.

Estado: backend funcional con todos los módulos (ver la tabla de módulos), migraciones V1, V2 y V3 aplicadas en Supabase y
frontend conectado a la API. Contratos en [docs/api/ENDPOINTS.md](docs/api/ENDPOINTS.md).

## Stack fijo (no cambiar)

- Backend: Java 21, Spring Boot 3.5, Maven (Maven Wrapper), Spring Data JPA, Flyway.
- Frontend: React, TypeScript, Vite.
- Datos y autenticación: Supabase PostgreSQL y Supabase Auth (JWT validado en Spring con JWKS).
- Mensajería: RabbitMQ (outbox).
- IA: Gemini con búsqueda de texto completo en PostgreSQL.
- Contenedores: Docker.

## Estructura de carpetas

```text
backend/                         API Spring Boot (paquete com.xelastay, un paquete por módulo)
  Dockerfile                     Imagen de producción (Render)
  src/main/resources/
    application*.properties      Configuración (base, local, test y prod); lee el .env de la raíz
    db/migration/                Migraciones Flyway (V1, V2…) y callback afterMigrate.sql
frontend/                        App React + TypeScript + Vite (lee frontend/.env)
scripts/                         seed-demo.ps1 (alojamientos) y demo-actividad.ps1 (reservas, pagos, reseñas); solo API
supabase/migrations/             Sin uso para el esquema (ver su README.md)
docs/
  api/                           ENDPOINTS.md y CONVENCIONES.md
  architecture/                  Autenticación, auditoría, eventos y acceso a datos
  database/                      Procedimiento y registro de migraciones
  decisions/                     Decisiones abiertas (PENDIENTES.md)
  technical-manual/              Manual técnico: índice (README.md), despliegue (DESPLIEGUE.md) y módulos
  ai/                            Base de conocimiento del agente
  technical-manual/              Manual técnico (despliegue, Power BI…)
  powerbi/                       Evidencia del tablero (.pbix sin credenciales y capturas)
infra/rabbitmq/                  Configuración de RabbitMQ
docker-compose.yml               RabbitMQ local; perfil "full" con API y frontend
.github/workflows/ci.yml         CI: backend (mvnw verify) y frontend (npm ci + build)
.env.example                     Plantilla de variables sin valores reales
CONTRIBUTING.md                  Flujo de PR, commits y migraciones
```

Mantén la separación entre `backend/`, `frontend/`, `supabase/`, `docs/` e `infra/`. Documenta decisiones de
arquitectura, API, datos y operación en su carpeta correspondiente.

## Módulos del backend

| Paquete `com.xelastay` | Contenido | Responsable |
|---|---|---|
| `security`, `config`, `common` | JWT, perfil automático, CORS, `ApiError`, `PageResponse`, `Paging` | Joshua |
| `profile` | `/me`, perfiles, roles | Joshua |
| `catalog` | Categorías, amenidades, ubicaciones | Joshua |
| `property` | Alojamientos, imágenes, amenidades del alojamiento | Joshua |
| `payment` | Pagos simulados | Joshua |
| `audit` | `AuditService` y `/audit-logs` | Joshua |
| `booking` | Reservas y disponibilidad | Giovanni |
| `events`, `notification` | Outbox, RabbitMQ y notificaciones | Giovanni |
| `review`, `messaging` | Reseñas y chat | Giovanni |
| `ai` | Agente RAG | Giovanni |
| `reporting` (infraestructura, exportación y reportes 9 a 15) | Catálogo de reportes, Excel y PDF | Giovanni |
| `reporting.definitions` (reportes 1 a 8, `BiReportSupport`) | Reportes sobre el esquema `bi` | Joshua |

Convenciones de código y API: [docs/api/CONVENCIONES.md](docs/api/CONVENCIONES.md). Entidades JPA idénticas a V1
(Hibernate en `validate`), DTO como records, validación con `jakarta.validation`, usuario siempre desde `CurrentUser`,
roles con `@PreAuthorize`. Cada operación que cambia datos de negocio se registra con `AuditService`
([AUDITORIA.md](docs/architecture/AUDITORIA.md)).

## Reglas obligatorias para agentes

- No hacer commits, pushes, merges, rebases ni crear, cambiar o borrar ramas, salvo las operaciones de Git que el
  usuario autorice explícitamente en su mensaje. Los commits los hace el equipo.
- No ejecutar migraciones ni Flyway contra Supabase. Única excepción: cuando el usuario escriba exactamente
  `AUTORIZO MIGRACIÓN V<n>`, y solo para esa versión, siguiendo [SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md).
  `FLYWAY_ENABLED` permanece en `false` en el `.env`.
- El MCP de Supabase se usa **solo en modo lectura** (esquema y verificación de datos). Nunca escribir con él. No
  mostrar datos personales (correos, teléfonos) salvo que el usuario lo pida.
- No leer, mostrar ni copiar valores de `.env` ni contraseñas. Para saber si una variable existe, revisar solo su
  nombre. En logs que se resuman, ocultar URL, host y usuario de la base de datos.
- No incluir secretos en archivos versionados; `.env.example` solo lleva marcadores de posición.
- Usar `.\mvnw` dentro de `backend/` (Maven no está instalado globalmente). Pedir aprobación antes de agregar
  dependencias nuevas (Maven o npm).
- No inventar endpoints, tablas, estados ni reglas de negocio. Una regla adicional necesaria (por ejemplo, evitar que
  un ADMIN se bloquee a sí mismo) se informa explícitamente al entregar.
- No modificar los módulos de otro responsable salvo lo que se pida; si es necesario, con el cambio mínimo y sin
  romper sus pruebas.
- Trabajar por bloques: al terminar cada bloque, `.\mvnw test` debe pasar; mostrar el diff, los comandos de prueba y
  el commit sugerido, y esperar confirmación antes de continuar.

## Base de datos y Flyway

Flyway es el único mecanismo para versionar el esquema. Las migraciones viven en
`backend/src/main/resources/db/migration`; no se usa la CLI de Supabase ni el Dashboard para crear tablas. Una
migración ya aplicada no se edita: cualquier cambio es una nueva versión. Aplicadas: V1 (esquema), V2 (roles y
catálogo base) y V3 (esquema `bi` y rol `powerbi_reader`). Procedimiento y registro:
[docs/database/SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md).

### Esquema `bi` y Power BI

- `bi` contiene solo vistas de lectura para los reportes 1 a 8 y Power BI ([POWER-BI.md](docs/technical-manual/POWER-BI.md)).
  No se expone en la Data API; `anon` y `authenticated` no tienen acceso.
- `powerbi_reader` es de **solo lectura**: `SELECT` sobre `bi` y nada sobre `public`. Se crea `NOLOGIN` en V3; la
  contraseña la pone Joshua a mano en el SQL Editor y nunca va al repositorio.
- **Ninguna vista de `bi` expone datos personales**: email, teléfono, `avatar_url`, `ip_address`, `user_agent` ni
  textos libres de usuarios (mensajes, comentarios de reseñas). Una vista nueva va en una migración nueva.

El Session pooler de Supabase admite 15 clientes para todo el proyecto: cada backend usa `DB_POOL_SIZE` conexiones
(3 por defecto). Detener los backends que no se usen.

### Callback `afterMigrate.sql`

`backend/src/main/resources/db/migration/afterMigrate.sql` es un callback de Flyway: se ejecuta al final de **cada**
`migrate`, cuando las migraciones ya están confirmadas, y no se registra en `flyway_schema_history` ni tiene
checksum. Hoy activa RLS en `flyway_schema_history` y le retira los privilegios a `anon` y `authenticated`. Eso no
puede ir dentro de una migración, porque alterar esa tabla la bloquearía mientras Flyway registra la propia migración.

Regla: `afterMigrate.sql` **solo puede contener sentencias de permisos repetibles** (`ENABLE ROW LEVEL SECURITY`,
`GRANT`, `REVOKE`), que dan el mismo resultado sin importar cuántas veces se ejecuten. Nunca se agregan ahí `CREATE`,
`ALTER` de columnas, `DROP`, `INSERT`, `UPDATE`, `DELETE` ni cambios de esquema o de datos: esos van en una migración
versionada (V3, V4…).

## Despliegue (producción)

Render despliega desde `main` según [render.yaml](render.yaml): la API con `backend/Dockerfile` y el perfil `prod`
([application-prod.properties](backend/src/main/resources/application-prod.properties)), y el frontend como Static
Site. Guía completa: [docs/technical-manual/DESPLIEGUE.md](docs/technical-manual/DESPLIEGUE.md).

- **Flyway nunca corre en producción**: el perfil `prod` fija `spring.flyway.enabled=false` (sin variable) y
  `FLYWAY_ENABLED=false` en Render. Las migraciones se aplican a mano según la regla de migraciones de arriba. No
  agregar ninguna forma de migrar al arrancar la API.
- Ningún secreto en `render.yaml`: las variables sensibles van con `sync: false` y se cargan en el panel de Render.
- El health check de Render es `/api/health`, que no depende de la base, RabbitMQ ni Gemini. No cambiarlo a
  `/actuator/health` (se pone `DOWN` si RabbitMQ falla y Render reiniciaría la API en bucle).
- Las variables `VITE_*` se incrustan al compilar: cambiarlas exige un nuevo deploy del Static Site.
- Plan Free: 512 MB y 0.1 CPU; la API arranca en ~106 s tras dormirse. No subir la memoria de la JVM ni el pool
  (`DB_POOL_SIZE=5`) sin revisar el límite de 15 conexiones del pooler.
- Verificación: `scripts/smoke-prod.ps1 -ApiUrl … -WebUrl …`.

## Flujo de ramas

```text
feat/* ──PR──> develop ──PR──> main
```

- Cada funcionalidad se trabaja en una rama `feat/*` creada desde `develop`.
- Toda integración a `develop` y a `main` se hace mediante Pull Request revisado (al menos una aprobación).
- No se hace push directo a `develop` ni a `main`. Detalle en [CONTRIBUTING.md](CONTRIBUTING.md).

## Responsables

| Persona  | Áreas |
|----------|-------|
| Joshua   | Arquitectura, backend, base de datos, autenticación, seguridad e integración |
| Pedro    | Frontend, UX, mapas, integración con la API y tablero de Power BI |
| Giovanni | Reservas, disponibilidad, RabbitMQ, chat, reseñas, IA/RAG y apoyo a reportes y auditoría |

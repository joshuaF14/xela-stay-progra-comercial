# Despliegue

Propuesta con planes gratuitos: la base de datos sigue en Supabase y cada pieza va a un servicio que ya cubre lo que
necesita. Ningún secreto va al repositorio: todos se cargan como variables de entorno en cada plataforma.

```text
 Navegador ──> Vercel (frontend React + Vite)
                  │  Supabase Auth (login)        ──> Supabase Auth
                  │  Authorization: Bearer <JWT>
                  v
              Render (backend Spring Boot, Docker) ──> Supabase PostgreSQL (Session pooler)
                  │                                ──> Gemini API (agente de IA)
                  v
              CloudAMQP (RabbitMQ, plan Little Lemur)
```

## 1. RabbitMQ en CloudAMQP

1. Crear una instancia con el plan gratuito **Little Lemur** en una región de Estados Unidos.
2. En los detalles de la instancia copiar: host, usuario, contraseña y *virtual host* (en los planes compartidos
   suele coincidir con el usuario).
3. Usar el puerto **5671** con TLS (`RABBITMQ_SSL=true`).

Límites del plan gratuito: 1 millón de mensajes al mes, 20 conexiones y 10 000 mensajes en cola; de sobra para la
demo. Spring declara el exchange `xela.events` y las colas al conectarse (ver [EVENTOS.md](../architecture/EVENTOS.md)).

## 2. Backend en Render

1. *New → Web Service*, conectar el repositorio y elegir la rama `main` (o `develop` para pruebas).
2. **Root Directory:** `backend`. **Runtime:** Docker (usa `backend/Dockerfile`). **Plan:** Free.
3. **Health Check Path:** `/api/health`.
4. Cargar las variables de entorno:

| Variable | Valor |
|---|---|
| `PORT` | `8080` (puerto en el que escucha Spring) |
| `SPRING_PROFILES_ACTIVE` | `local` |
| `CORS_ALLOWED_ORIGINS` | URL del frontend en Vercel, p. ej. `https://xela-stay.vercel.app` |
| `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` | los del Session pooler de Supabase |
| `DB_POOL_SIZE` | `3` (el pooler admite 15 clientes para todo el proyecto) |
| `FLYWAY_ENABLED` | `false` |
| `SUPABASE_JWKS_URI`, `SUPABASE_JWT_ISSUER` | los mismos del `.env` |
| `RABBITMQ_HOST`, `RABBITMQ_USER`, `RABBITMQ_PASSWORD`, `RABBITMQ_VHOST` | los de CloudAMQP |
| `RABBITMQ_PORT` | `5671` |
| `RABBITMQ_SSL` | `true` |
| `EVENTS_ENABLED` | `true` |
| `AI_API_KEY` | key de Google AI Studio |
| `AI_MODEL` | `gemini-3.8-flash` |

El plan gratuito se duerme tras 15 minutos sin tráfico y tarda alrededor de un minuto en despertar. Antes de una
demostración, abrir `https://<backend>.onrender.com/api/health` unos minutos antes. Mientras duerme no se publican
eventos; quedan en `outbox_events` y salen al despertar.

Probar la imagen en local antes de subirla:

```bash
docker build -t xelastay-backend backend
docker run --rm -p 8080:8080 --env-file .env -e RABBITMQ_HOST=host.docker.internal xelastay-backend
```

## 3. Frontend en Vercel

1. *Add New → Project*, importar el repositorio. **Root Directory:** `frontend`. **Framework:** Vite.
2. Variables de entorno:

| Variable | Valor |
|---|---|
| `VITE_API_URL` | URL del backend en Render, p. ej. `https://xela-stay-api.onrender.com` |
| `VITE_SUPABASE_URL` | `https://<project-ref>.supabase.co` |
| `VITE_SUPABASE_ANON_KEY` | clave pública `anon` de Supabase |

3. `frontend/vercel.json` redirige todas las rutas a `index.html` para que `BrowserRouter` funcione al recargar.
4. Las variables `VITE_*` se leen al compilar: si se cambian, hay que volver a desplegar.

## 4. Ajustes finales

1. Con la URL de Vercel, actualizar `CORS_ALLOWED_ORIGINS` en Render y redesplegar el backend.
2. En Supabase, *Authentication → URL Configuration*: agregar la URL de Vercel como *Site URL* o *Redirect URL*.
3. Verificar:
   - `GET /actuator/health` responde `UP` con `db` y `rabbit` en `UP`.
   - Iniciar sesión en el frontend y abrir una pantalla que llame a la API.
   - Crear y confirmar una reserva: en CloudAMQP se ve el tráfico y en `/api/v1/me/notifications` aparecen los avisos.

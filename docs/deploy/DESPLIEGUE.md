# Despliegue en producción

Xela Stay se despliega en **Render** a partir de [render.yaml](../../render.yaml) (Blueprint), desde la rama `main`.

| Pieza | Dónde | URL |
|---|---|---|
| API Spring Boot (Docker) | Render Web Service `xela-stay-api`, plan Free, Oregon | https://xela-stay-api.onrender.com |
| Frontend React | Render Static Site `xela-stay-web` (CDN) | https://xela-stay-web.onrender.com |
| Base de datos y Auth | Supabase (la misma del desarrollo), Session pooler | — |
| RabbitMQ | CloudAMQP, plan Little Lemur (20 conexiones) | — |
| IA | Gemini (`AI_API_KEY`) | — |

```text
Navegador ──> xela-stay-web (CDN) ──JS──> xela-stay-api ──> Supabase PostgreSQL (Session pooler :5432)
    │                                           ├──> CloudAMQP (amqps)
    └── login/registro ──> Supabase Auth         └──> Gemini
```

Flujo de publicación: `feat/*` → PR → `develop` → PR → `main` → Render despliega solo (`autoDeployTrigger: commit`).
`buildFilter` hace que un cambio solo en `frontend/` no reconstruya la API, y al revés.

---

## 1. Crear el Blueprint en Render

1. Entrar a https://render.com con **GitHub** y dar acceso al repositorio.
2. **New → Blueprint**, elegir el repositorio y la rama **`main`**. Render lee `render.yaml` y muestra los dos
   servicios.
3. Completar las variables marcadas como secretas (`sync: false`). Render solo las pide al crear el Blueprint;
   después se editan en **servicio → Environment**.

   | Servicio | Variable | De dónde sale |
   |---|---|---|
   | `xela-stay-api` | `DB_URL` | Supabase, paso 2 |
   | `xela-stay-api` | `DB_USERNAME` | Supabase, paso 2 |
   | `xela-stay-api` | `DB_PASSWORD` | Supabase, paso 2 |
   | `xela-stay-api` | `RABBITMQ_URL` | CloudAMQP, paso 4 |
   | `xela-stay-api` | `AI_API_KEY` | Google AI Studio |
   | `xela-stay-web` | `VITE_SUPABASE_ANON_KEY` | Supabase, paso 2 |

4. **Apply**. El primer build de la API tarda varios minutos (descarga de dependencias de Maven).

Las variables no secretas (`SPRING_PROFILES_ACTIVE=prod`, `FLYWAY_ENABLED=false`, `DB_POOL_SIZE=5`, JWKS, issuer,
`CORS_ALLOWED_ORIGINS`, `EVENTS_ENABLED`, `AI_MODEL`, `VITE_API_URL`, `VITE_SUPABASE_URL`, `NODE_VERSION`) ya vienen en
`render.yaml`.

> Si Render asignara otra URL a un servicio (por ejemplo con sufijo), corregir `CORS_ALLOWED_ORIGINS` en la API y
> `VITE_API_URL` en el sitio. **Cambiar una variable `VITE_*` exige un nuevo deploy del sitio** (Vite las incrusta
> al compilar): *Manual Deploy → Deploy latest commit*.

## 2. Datos de Supabase

| Dato | Dónde en Supabase | Variable |
|---|---|---|
| Host y usuario del **Session pooler** | **Connect → Session pooler** (puerto **5432**, usuario `postgres.<project-ref>`) | `DB_URL`, `DB_USERNAME` |
| Contraseña de la base | **Project Settings → Database** (restablecerla si no se conoce) | `DB_PASSWORD` |
| JWKS e issuer | `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` y `https://<project-ref>.supabase.co/auth/v1` | ya en `render.yaml` |
| Publishable/anon key | **Project Settings → API Keys** | `VITE_SUPABASE_ANON_KEY` |

Formato de `DB_URL` (sin usuario ni contraseña dentro):

```text
jdbc:postgresql://<host-del-session-pooler>:5432/postgres?sslmode=require
```

**Nunca** el Transaction pooler (puerto 6543). El pooler admite **15 clientes para todo el proyecto**: la API usa 5
(`DB_POOL_SIZE`) y cada integrante en local 3. Si aparece `max clients reached in session mode`, detener backends
locales que no se usen.

## 3. Supabase Authentication: URL Configuration

**Authentication → URL Configuration**:

| Campo | Valor |
|---|---|
| **Site URL** | `https://xela-stay-web.onrender.com` |
| **Redirect URLs** | `https://xela-stay-web.onrender.com/**` y, para desarrollo, `http://localhost:5173/**` |

Sin esto, el correo de confirmación del registro lleva a `localhost` y el login desde producción falla: el frontend
llama a `supabase.auth.signUp` sin `emailRedirectTo`, así que Supabase usa el **Site URL**.

## 4. CloudAMQP (RabbitMQ)

1. En CloudAMQP, abrir la instancia (plan **Little Lemur**) → **Details**.
2. Copiar la **AMQP URL** que empieza con `amqps://` (usuario, contraseña, host y virtual host en una sola URL).
3. Pegarla en `RABBITMQ_URL` de `xela-stay-api`. Con `amqps://` Spring activa TLS (puerto 5671) y usa el virtual host
   de la URL, aunque `RABBITMQ_SSL` no esté definida (lo cubre `ProdProfileConfigTest`).

Si CloudAMQP aún no está listo: `EVENTS_ENABLED=false`. La API funciona igual; los eventos de reservas quedan
`PENDING` en `outbox_events` y se publican cuando se vuelva a `true`.

## 5. Migraciones

**Nunca corren en producción**: el perfil `prod` fija `spring.flyway.enabled=false`. Las aplica solo Joshua, desde su
máquina, con el procedimiento de [SUPABASE_FLYWAY.md](../database/SUPABASE_FLYWAY.md) (y la autorización explícita).
Producción y desarrollo usan la misma base: una migración aplicada vale para los dos.

## 6. Logs y fallos de build

- **Logs**: Render → `xela-stay-api` → **Logs** (en vivo, con búsqueda). Los deploys y reinicios están en **Events**.
  Un arranque correcto termina con `Started XelaStayApplication in … seconds`.
- **Build de la API falla**: abrir el deploy en **Events** y revisar el log de build.
  - Errores de compilación o de Maven: reproducir en local con `cd backend; .\mvnw -B -DskipTests package`.
  - Error intermitente o de memoria durante el build: **Manual Deploy → Clear build cache & deploy** y reintentar.
- **La API se reinicia sola** (`OutOfMemoryError` en los logs; la JVM sale por `-XX:+ExitOnOutOfMemoryError`):
  bajar `DB_POOL_SIZE` a 3, o pasar el servicio a un plan con más memoria. Opciones de la JVM y por qué: comentarios
  de [backend/Dockerfile](../../backend/Dockerfile).
- **Build del sitio falla por memoria de Node**: agregar `NODE_OPTIONS=--max-old-space-size=1024` en
  `xela-stay-web → Environment` y volver a desplegar.
- **Health check**: Render usa `/api/health`, que no depende de la base, RabbitMQ ni Gemini. No usar
  `/actuator/health`: se pone `DOWN` si RabbitMQ falla y Render reiniciaría el servicio en bucle.

## 7. Despertar la API antes de la defensa

El plan Free **apaga la API tras 15 minutos sin tráfico**. El primer arranque medido tardó **106 s** (0.1 CPU).

1. **Unos 2-3 minutos antes** de la presentación, abrir https://xela-stay-api.onrender.com/api/health y esperar
   `{"status":"UP",…}` (el navegador puede mostrar carga durante cerca de 2 minutos; no recargar).
2. Ejecutar la verificación (paso 9) para dejar todo probado.
3. Abrir https://xela-stay-web.onrender.com. Mientras haya uso, la API no se apaga.

El sitio estático no se apaga nunca (CDN); solo la API.

## 8. Cargar la base de conocimiento de la IA en producción

Requiere un usuario con rol **ADMIN** (ver [AUTENTICACION.md](../architecture/AUTENTICACION.md#crear-el-primer-admin))
y la API despierta. Desde la raíz del repo, en PowerShell 5.1:

```powershell
$api = 'https://xela-stay-api.onrender.com'
$anon = Read-Host 'Publishable/anon key'
$email = Read-Host 'Email del ADMIN'
$pass = Read-Host 'Contraseña' -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($pass)
$body = @{ email = $email; password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) } | ConvertTo-Json
[Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
$login = Invoke-RestMethod -Method Post -ContentType 'application/json; charset=utf-8' `
  -Uri 'https://hnflawvzlrttdkbseahe.supabase.co/auth/v1/token?grant_type=password' `
  -Headers @{ apikey = $anon } -Body $body
$body = $null
$token = $login.access_token

foreach ($doc in @('politicas-de-cancelacion:POLITICA', 'reglas-de-la-casa:REGLAS', 'preguntas-frecuentes:FAQ')) {
  $name, $type = $doc.Split(':')
  curl.exe -s -X POST "$api/api/v1/ai/documents" -H "Authorization: Bearer $token" `
    -F "file=@docs/ai/$name.md" -F "documentType=$type"
  ''
}
$token = $null
```

Cada documento responde `201` con `chunkCount`. **Cargarlos una sola vez**: cada ejecución crea documentos nuevos.
Para revisar qué hay cargado, en el SQL Editor: `select title, document_type, created_at from knowledge_documents;`.

Los alojamientos de demostración se crean con [scripts/seed-demo.ps1](../../scripts/seed-demo.ps1)
`-BaseUrl https://xela-stay-api.onrender.com` (idempotente; ver [ENDPOINTS.md](../api/ENDPOINTS.md#datos-de-demostración)).

## 9. Verificación

```powershell
powershell -ExecutionPolicy Bypass -File scripts\smoke-prod.ps1 `
  -ApiUrl https://xela-stay-api.onrender.com -WebUrl https://xela-stay-web.onrender.com
```

Comprueba, sin credenciales: `/api/health`, alojamientos publicados, categorías, `401` en una ruta protegida, CORS
para el origen del frontend, que el sitio cargue y que una ruta de React Router (`/login`) funcione al recargar. Al
final muestra OK/FALLA por prueba. La primera petición espera hasta 3 minutos por si la API está dormida.

## 10. Problemas comunes

| Síntoma | Causa probable | Solución |
|---|---|---|
| El frontend muestra error de red / CORS en la consola | `CORS_ALLOWED_ORIGINS` no coincide con la URL del sitio | Corregirla en la API (sin `/` final) |
| Todas las rutas con token dan `401` | `SUPABASE_JWKS_URI` o `SUPABASE_JWT_ISSUER` incorrectos, o la llave JWT es HS256 | Revisar las variables; ver [AUTENTICACION.md](../architecture/AUTENTICACION.md) |
| El correo de registro abre `localhost` | Site URL de Supabase sin configurar | Paso 3 |
| El frontend llama a `localhost:8080` | `VITE_API_URL` vacío al compilar | Revisar la variable del sitio y volver a desplegarlo |
| Recargar `/login` da 404 | Falta la regla de reescritura | `routes` de `xela-stay-web` en `render.yaml` |
| `max clients reached in session mode` | Se agotaron las 15 conexiones del pooler | Detener backends locales; bajar `DB_POOL_SIZE` |
| La IA responde `503` | Falta `AI_API_KEY` o se agotó la cuota de Gemini | Revisar la variable o cambiar `AI_MODEL` |
| La primera petición tarda ~2 minutos | La API estaba dormida (plan Free) | Paso 7 |

# Autenticación con Supabase Auth

## Flujo

```text
Frontend ──login/registro──> Supabase Auth ──access token (JWT)──> Frontend
Frontend ──Authorization: Bearer <JWT>──> API Spring ──valida firma con JWKS──> recurso
```

- El frontend usa Supabase Auth solo para iniciar sesión y registrarse, y envía el `access_token` al backend en el encabezado `Authorization: Bearer`.
- El backend es un **resource server**: no emite tokens ni guarda contraseñas; valida cada JWT.
- Rutas públicas: `GET /api/health`, `GET /actuator/health`, `GET /api/v1/properties/{id}/availability` y `GET /api/v1/properties/{id}/reviews`. El resto de `/api/**` requiere un JWT válido. Cualquier otra ruta se rechaza.
- API stateless: sin sesión HTTP; CSRF desactivado para `/api/**` porque no se usan cookies.
- Los errores 401/403 usan el formato común `{timestamp, status, error, message, path, details}` y conservan el encabezado `WWW-Authenticate`.

## Validación del token

| Comprobación | Valor esperado | Configuración |
|---|---|---|
| Firma | Llave pública del JWKS del proyecto (ES256 o RS256) | `SUPABASE_JWKS_URI` |
| `iss` | `https://<project-ref>.supabase.co/auth/v1` | `SUPABASE_JWT_ISSUER` |
| `aud` | `authenticated` | fijo en `application.properties` |
| `exp` / `nbf` | Vigente (tolerancia de 60 s) | por defecto |
| `sub` | UUID (= `auth.users.id` = `profiles.id`) | por defecto |

Si falta `SUPABASE_JWKS_URI` o `SUPABASE_JWT_ISSUER`, la aplicación **no arranca**, para no aceptar tokens sin validar. El JWKS se descarga solo cuando llega el primer token y queda en caché.

## Usuario actual

`com.xelastay.security.CurrentUser` obtiene el UUID del usuario desde el claim `sub`:

```java
UUID userId = currentUser.requireId(); // 401 si no hay JWT
```

Regla: **nunca se confía en un ID de usuario enviado por el cliente** (ruta, query o cuerpo). Las operaciones "propias" (mis reservas, mi perfil) usan siempre `CurrentUser`.

## Perfil automático y roles

En cada petición con un JWT válido, `ProfileJwtAuthenticationConverter` llama a `ProfileAccessService`:

1. Busca el perfil con `id = sub` junto con sus roles (una consulta).
2. Si no existe, lo crea con `INSERT … ON CONFLICT DO NOTHING`: `email` (claim `email`), `full_name`
   (`user_metadata.full_name` o la parte local del email) y `status = ACTIVE`, y le asigna el rol `GUEST`. Todo en
   una transacción; dos peticiones simultáneas del mismo usuario no chocan.
3. Expone los roles como `ROLE_GUEST`, `ROLE_HOST` y `ROLE_ADMIN`. Los controladores los exigen con
   `@PreAuthorize("hasRole('ADMIN')")` (seguridad por método activa con `@EnableMethodSecurity`).

Los roles se leen de la base en cada petición: un cambio de roles o de estado tiene efecto en la siguiente llamada,
sin esperar a que venza el token.

**Perfil bloqueado:** si el perfil está `SUSPENDED` o `INACTIVE` (o no se pudo crear: token sin email o email ya
usado por otro perfil), recibe la autoridad interna `PROFILE_BLOCKED` y la API responde `403` en todo `/api/v1/**`,
salvo `GET /api/v1/me`, para que el frontend pueda mostrarle su estado. Las rutas públicas siguen disponibles.

Un usuario se vuelve anfitrión con `POST /api/v1/me/host` (agrega `HOST`).

### Crear el primer ADMIN

Ningún usuario es ADMIN al registrarse, y para asignar roles por la API hace falta ser ADMIN. El primero lo crea
**Joshua** desde el SQL Editor de Supabase, una sola vez:

1. El usuario inicia sesión en el frontend (o hace cualquier petición con su token) para que exista su perfil.
2. En el SQL Editor:

   ```sql
   -- Confirmar el perfil por correo
   select id, email, status from profiles where email = '<correo-del-admin>';

   -- Asignar ADMIN (idempotente)
   insert into profile_roles (profile_id, role_id)
   select p.id, r.id from profiles p, roles r
   where p.email = '<correo-del-admin>' and r.name = 'ADMIN'
   on conflict do nothing;

   -- Verificar
   select p.email, r.name from profile_roles pr
   join profiles p on p.id = pr.profile_id join roles r on r.id = pr.role_id
   where p.email = '<correo-del-admin>';
   ```

3. Desde ahí, el ADMIN gestiona los roles de los demás con `PUT /api/v1/profiles/{id}/roles`. Un ADMIN no puede
   quitarse su propio rol ADMIN ni cambiar su propio estado (`409`), para evitar quedarse sin administradores.

## ¿Mi proyecto usa JWKS o el secreto heredado HS256?

Supabase puede firmar los tokens con **llaves asimétricas** (publicadas en JWKS) o con el **secreto compartido heredado (HS256)**. El backend solo implementa JWKS. Para comprobar cuál usa el proyecto:

1. En el panel de Supabase: **Project Settings → JWT Keys**. En la pestaña de llaves de firma, revisar el tipo de la llave **actual** (*current key*):
   - `ECC (P-256)` o `RSA`: llaves asimétricas, compatibles con este backend.
   - `Legacy HS256 (Shared Secret)`: secreto heredado, **no** compatible.
2. Abrir en el navegador `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json`. Si `keys` está vacío (`{"keys":[]}`), el proyecto solo usa el secreto heredado.
3. Opcional: revisar el encabezado de un access token **localmente**, sin pegarlo en sitios de terceros:

   ```powershell
   $token = "<access_token>"
   $h = $token.Split('.')[0].Replace('-', '+').Replace('_', '/')
   $h = $h.PadRight($h.Length + (4 - $h.Length % 4) % 4, '=')
   [Text.Encoding]::UTF8.GetString([Convert]::FromBase64String($h))   # {"alg":"ES256","kid":"..."} o {"alg":"HS256"}
   ```

Si el proyecto usa HS256, la opción recomendada es migrar a llaves asimétricas en **JWT Keys**: crear una llave nueva en espera y rotarla. Los tokens viejos siguen siendo válidos hasta que vencen. Soportar HS256 en el backend implicaría guardar el secreto en el servidor; queda como decisión pendiente en [PENDIENTES.md](../decisions/PENDIENTES.md).

## Variables

| Variable | Ejemplo |
|---|---|
| `SUPABASE_JWKS_URI` | `https://<project-ref>.supabase.co/auth/v1/.well-known/jwks.json` |
| `SUPABASE_JWT_ISSUER` | `https://<project-ref>.supabase.co/auth/v1` |

No son secretos, pero dependen del proyecto. Van en `.env` (ver [.env.example](../../.env.example)).

## Pendiente

- Si el usuario cambia su correo en Supabase Auth, `profiles.email` no se actualiza (observación 9 de
  [PENDIENTES.md](../decisions/PENDIENTES.md)).

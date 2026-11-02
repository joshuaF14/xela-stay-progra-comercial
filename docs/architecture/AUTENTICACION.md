# Autenticación con Supabase Auth

## Flujo

```text
Frontend ──login/registro──> Supabase Auth ──access token (JWT)──> Frontend
Frontend ──Authorization: Bearer <JWT>──> API Spring ──valida firma con JWKS──> recurso
```

- El frontend usa Supabase Auth solo para iniciar sesión y registrarse, y envía el `access_token` al backend en el encabezado `Authorization: Bearer`.
- El backend es un **resource server**: no emite tokens ni guarda contraseñas; valida cada JWT.
- Rutas públicas: `GET /api/health` y `GET /actuator/health`. El resto de `/api/**` requiere un JWT válido. Cualquier otra ruta se rechaza.
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

- Roles y autorización por rol (`profiles` / `profile_roles`): hoy solo se exige estar autenticado.
- Sincronización `auth.users` → `profiles`.

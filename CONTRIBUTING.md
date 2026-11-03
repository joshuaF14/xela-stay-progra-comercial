# Cómo contribuir

## Ramas y Pull Requests

```text
feat/* ──PR──> develop ──PR──> main
```

- Cada tarea se trabaja en una rama `feat/<tema>` creada desde `develop` y actualizada desde `develop` antes de abrir el PR.
- Todo cambio entra por Pull Request con base `develop` y **al menos una aprobación**. `main` solo recibe PRs desde `develop`.
- No se hace push directo a `develop` ni a `main`.
- El CI (`.github/workflows/ci.yml`) debe pasar antes de fusionar.

## Commits

En español y con [Conventional Commits](https://www.conventionalcommits.org/es/): `tipo(área): descripción`.

```text
feat(reservas): validar fechas cruzadas con bloqueos
fix(seguridad): devolver 401 con token vencido
docs(api): documentar endpoints de pagos
```

Tipos habituales: `feat`, `fix`, `docs`, `test`, `refactor`, `chore`, `ci`. Cada integrante hace sus propios commits.

## Secretos

- **Prohibido subir `.env`** o cualquier credencial. Las variables se documentan con marcadores en `.env.example`.
- Las credenciales de Supabase se comparten por un canal privado, nunca en el repositorio, issues ni PRs.

## Base de datos

- **Solo Joshua ejecuta migraciones** contra Supabase. En el `.env` de todos los demás, `FLYWAY_ENABLED=false`.
- Los cambios de esquema o de datos van en una migración nueva (`V2__…`, `V3__…`) en
  `backend/src/main/resources/db/migration`, incluida en el PR que la necesita.
- **Nunca se edita una migración ya aplicada.**
- Procedimiento: [docs/database/SUPABASE_FLYWAY.md](docs/database/SUPABASE_FLYWAY.md).

## Antes de abrir el PR

```powershell
cd backend
.\mvnw test
```

Convenciones de la API: [docs/api/CONVENCIONES.md](docs/api/CONVENCIONES.md).

# Migraciones de Xela Stay en Supabase

## Mecanismo y alcance

Flyway es el único mecanismo elegido para versionar el esquema de la aplicación. Las migraciones están en `backend/src/main/resources/db/migration` y siguen `V<VERSION>__<descripcion>.sql`. No crear tablas desde el Dashboard ni gestionar el mismo esquema con Supabase CLI. Supabase Auth y Storage siguen siendo servicios administrados de Supabase; Flyway solo administra los objetos SQL que pertenecen a Xela Stay.

La migración inicial `V1__xela_stay_initial_schema.sql` es para un esquema de aplicación vacío. Crea 22 tablas y dos vistas de reporte, activa RLS sin políticas y retira privilegios a `anon` y `authenticated` (ver [Seguridad del esquema](#seguridad-del-esquema)); no crea ni borra objetos de Auth, no incluye datos semilla ni altera tablas preexistentes. Antes de usarla en un proyecto con objetos existentes, comparar el esquema y definir una estrategia de adopción/baseline con respaldo; no habilitar `baseline-on-migrate` ni `clean` como atajo.

## Conexión desde Spring Boot

Copiar `.env.example` a un archivo local ignorado por Git o, preferentemente, inyectar las variables desde el entorno/gestor de secretos:

- `DB_URL`: URL JDBC `jdbc:postgresql://<host>:<puerto>/<base>?sslmode=require`.
- `DB_USERNAME` y `DB_PASSWORD`: credenciales de conexión a PostgreSQL, nunca las claves `anon` o `service_role`.
- `FLYWAY_ENABLED`: `false` por defecto. No activar en un arranque normal contra el proyecto remoto.

### Carga del `.env` local

Spring Boot no lee archivos `.env` por sí solo. `application.properties` declara:

```properties
spring.config.import=optional:file:../.env[.properties],optional:file:.env[.properties]
```

- Las rutas son relativas al directorio de trabajo: `../.env` cubre la ejecución desde `backend/` y `.env` la ejecución desde la raíz. Si existen ambos, prevalece el segundo. Al ejecutar desde la raíz, `../.env` apunta fuera del repositorio.
- Ambos son `optional:`; si no existen, el arranque continúa con las variables del entorno o los valores por defecto.
- Las variables de entorno del sistema tienen prioridad sobre el archivo.
- El archivo se interpreta como `.properties`: sin `export` ni comillas, y `\` debe escaparse.
- Solo tienen efecto las claves referenciadas con `${...}` en `application.properties`: `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `FLYWAY_ENABLED`, `SPRING_PROFILES_ACTIVE`, `SERVER_PORT` y `CORS_ALLOWED_ORIGINS`.
- En Docker, inyectar las variables con `env_file`/`environment` del servicio en lugar de depender de este archivo.

Tomar host, puerto, base y usuario del diálogo **Connect** de Supabase. Para Flyway, preferir conexión directa (las migraciones son sesiones únicas); si la red solo permite IPv4, usar el pooler en modo sesión. No usar el pooler en modo transacción para Flyway. La conexión directa suele requerir IPv6 o el complemento IPv4; los endpoints del pooler son una alternativa según el modo indicado por Supabase.

No copiar secretos al repositorio, frontend, logs o historial de comandos. Mantener el puerto TLS requerido.

### Roles de base de datos (solo documentado, no creado)

- **Usuario dedicado para Flyway**: un rol propio con permisos mínimos para crear/alterar los objetos de aplicación en `public` y gestionar `flyway_schema_history`. No usar el superusuario `postgres` de forma rutinaria. Este rol será el propietario de las tablas.
- **Backend**: debe conectarse como propietario de las tablas (o con un rol con `BYPASSRLS`), porque RLS está activo sin políticas; cualquier otro rol verá las tablas vacías.
- **Rol de solo lectura para Power BI**: `SELECT` únicamente sobre `reporting_bookings` y `reporting_property_ratings`. Como las vistas usan `security_invoker`, RLS de las tablas base también aplica a este rol: necesitará políticas `SELECT` específicas o `BYPASSRLS`. Decisión pendiente en [PENDIENTES.md](../decisions/PENDIENTES.md).

Ninguno de estos roles se crea en V1; su creación requiere una migración o procedimiento aprobado.

## Seguridad del esquema

El acceso a los datos es exclusivamente a través del backend Spring; no se expone el esquema mediante la API de datos de Supabase (PostgREST).

- **RLS activado sin políticas** en las 22 tablas (`ALTER TABLE ... ENABLE ROW LEVEL SECURITY`). Sin políticas, `anon` y `authenticated` no ven ni modifican filas. El propietario no queda sujeto a RLS porque no se usa `FORCE ROW LEVEL SECURITY`.
- **`REVOKE ALL ... FROM anon, authenticated`** sobre las 22 tablas y las 2 vistas, para retirar los privilegios que Supabase concede por defecto en `public`.
- **Vistas con `security_invoker = true`**: aplican los permisos y RLS de quien consulta, no los del propietario, para que no sirvan como atajo para saltarse RLS. Requiere PostgreSQL 15 o superior.
- `service_role` conserva acceso total (`BYPASSRLS`); su clave nunca debe llegar al frontend ni al repositorio.
- `flyway_schema_history` también queda con RLS y sin privilegios para `anon` y `authenticated` mediante el callback `db/migration/afterMigrate.sql`. Se ejecuta después de cada `migrate`, porque alterar esa tabla dentro de una migración la bloquearía mientras Flyway registra la misma migración.
- V1 ejecuta `ALTER DEFAULT PRIVILEGES IN SCHEMA public REVOKE ALL ON TABLES/SEQUENCES/FUNCTIONS FROM anon, authenticated`. Aplica a los objetos que cree el rol que migra (`postgres`), así que las tablas de V2, V3… nacen sin acceso para esos roles. Aun así, cada tabla nueva debe incluir `ENABLE ROW LEVEL SECURITY` en su migración. Los objetos creados por otros roles (por ejemplo, desde el Dashboard con `supabase_admin`) no quedan cubiertos.

## Procedimiento controlado (requiere autorización explícita antes del paso remoto)

1. Revisar la migración y su impacto en una revisión de código.
2. Confirmar proyecto, endpoint, rama/entorno y usuario objetivo desde Supabase; no inferirlos del archivo de ejemplo.
3. Crear y comprobar un respaldo recuperable. Inspeccionar tablas/esquemas existentes y confirmar que el destino está vacío o aprobar por separado una estrategia de baseline/adopción.
4. Confirmar que `auth.users` está disponible y que `profiles` puede referenciarlo. Confirmar permisos de creación de objetos en el esquema elegido.
5. Probar la migración en un proyecto Supabase desechable con versión PostgreSQL equivalente y revisar `flyway info`/`validate` antes de `migrate`.
6. Solo tras autorización explícita para ese proyecto/entorno, suministrar secretos mediante variables seguras y ejecutar la migración de forma controlada. La configuración de aplicación mantiene Flyway desactivado salvo que `FLYWAY_ENABLED=true` se indique expresamente.
7. Registrar versión aplicada, resultado y respaldo asociado. No ejecutar `clean`, borrar tablas ni editar migraciones ya aplicadas; cualquier cambio posterior es una nueva migración versionada.

## Aplicación de V1

**Solo Joshua ejecuta migraciones.** El resto del equipo mantiene `FLYWAY_ENABLED=false` en su `.env` y nunca lo cambia.

### Conexión

- Usar el **Session pooler** (diálogo **Connect** de Supabase): puerto **5432**, usuario `postgres.<project-ref>`, con `sslmode=require` en `DB_URL`.
- **Nunca usar el Transaction pooler (puerto 6543)** para Flyway. Ese modo reparte las sentencias entre distintas conexiones del servidor, lo que rompe los bloqueos de sesión (advisory locks) de Flyway y las sentencias preparadas.
- La conexión directa (`db.<project-ref>.supabase.co:5432`) también es válida si la red tiene IPv6.

### Verificación previa (SQL Editor de Supabase)

```sql
select version();                                              -- PostgreSQL 15 o superior (security_invoker)
select count(*) from pg_tables where schemaname = 'public';    -- 0: esquema vacío
select to_regclass('public.flyway_schema_history');            -- null: Flyway nunca se ejecutó
select has_table_privilege('postgres', 'auth.users', 'REFERENCES'); -- true: FK profiles -> auth.users
```

### Ejecución única

Se habilita Flyway solo en la sesión de terminal, nunca en `.env`. La variable de entorno tiene prioridad sobre el `.env`:

```powershell
cd backend
$env:FLYWAY_ENABLED = "true"
.\mvnw spring-boot:run
# Al terminar, haya funcionado o no:
Remove-Item Env:FLYWAY_ENABLED
```

Mensaje esperado en el log: `Successfully applied 1 migration to schema "public", now at version v1`. PostgreSQL ejecuta la migración en una transacción, así que un fallo no deja objetos a medias. Ante un error, no reintentar ni ejecutar `repair`, `clean` o `baseline`: primero se analiza la causa.

### Verificación posterior (SQL Editor de Supabase)

```sql
-- a) Tablas y vistas de public con RLS. Esperado: 23 tablas (22 + flyway_schema_history), todas con rls = true, y 2 vistas.
select c.relname as objeto,
       case c.relkind when 'r' then 'tabla' when 'v' then 'vista' end as tipo,
       c.relrowsecurity as rls
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
where n.nspname = 'public' and c.relkind in ('r', 'v')
order by tipo, objeto;

-- b) Privilegios de anon y authenticated en public. Esperado: 0 filas.
select grantee, table_name, privilege_type
from information_schema.role_table_grants
where table_schema = 'public' and grantee in ('anon', 'authenticated');

-- b2) Igual que b), pero leyendo el catálogo directamente. Esperado: 0 filas.
select c.relname, a.grantee::regrole as grantee, a.privilege_type
from pg_class c
join pg_namespace n on n.oid = c.relnamespace
cross join lateral aclexplode(c.relacl) a
where n.nspname = 'public' and c.relkind in ('r', 'v')
  and a.grantee in ('anon'::regrole, 'authenticated'::regrole);

-- c) Historial de Flyway. Esperado: una fila, versión 1 con success = true.
select version, description, success, installed_on from flyway_schema_history;

-- d) Privilegios por defecto del rol postgres en public. anon y authenticated no deben aparecer en defaclacl.
select pg_get_userbyid(d.defaclrole) as rol, d.defaclobjtype as tipo, d.defaclacl
from pg_default_acl d
join pg_namespace n on n.oid = d.defaclnamespace
where n.nspname = 'public';
```

## Registro de migraciones aplicadas

| Fecha | Versión | Entorno | Ejecutó | Resultado |
|-------|---------|---------|---------|-----------|
| 2026-11-02 | v1 (`V1__xela_stay_initial_schema.sql`) | Supabase xela-stay (producción del curso), PostgreSQL 17.11, Session pooler | Joshua | Aplicada. Log: `Successfully applied 1 migration to schema "public", now at version v1` (11 s); callback `afterMigrate` ejecutado. |
| 2026-11-02 | v2 (`V2__seed_roles_y_catalogo.sql`) | Supabase xela-stay (producción del curso), PostgreSQL 17.11, Session pooler | Joshua | Aplicada (hora exacta en `flyway_schema_history.installed_on`). Un arranque posterior con Flyway activo validó 2 migraciones (`Schema "public" is up to date`) y Hibernate validó las entidades de perfiles y roles. Datos verificados por Joshua: roles GUEST, HOST y ADMIN; 5 categorías; 8 amenidades. |
| 2026-11-03 | v3 (`V3__bi_vistas_y_rol_powerbi.sql`) | Supabase xela-stay (producción del curso), PostgreSQL 17.11, Session pooler | Joshua | Aplicada. Log: `Successfully applied 1 migration to schema "public", now at version v3` (2.8 s); callback `afterMigrate` ejecutado. Verificado (solo lectura): 10 vistas en `bi`; `powerbi_reader` con `SELECT` en las 10, sin escritura y sin acceso a ninguna tabla de `public`; `anon` y `authenticated` sin acceso a `bi`; rol `NOLOGIN`, límite de 3 conexiones. |

Verificación posterior de V1 (SQL Editor de Supabase, consultas a–d):

- **a)** Todas las tablas de `public` tienen `rls = true` (22 + `flyway_schema_history`), y están las 2 vistas.
- **b)** 0 filas. **b2)** 0 filas: `anon` y `authenticated` no tienen privilegios en `public`.
- **c)** `version = 1`, `"xela stay initial schema"`, `success = true`, `installed_on = 2026-11-02 20:01:44 UTC`.
- **d)** Las filas de `postgres` no incluyen `anon` ni `authenticated`. Las de `supabase_admin` sí los incluyen (esperado: solo afectan a objetos creados desde el Dashboard).

## PostgreSQL, Supabase y Flyway

Supabase ofrece PostgreSQL estándar con servicios administrados alrededor. Flyway documenta PostgreSQL como base soportada y requiere el módulo `org.flywaydb:flyway-database-postgresql` además del driver JDBC `org.postgresql:postgresql`; Spring Boot ofrece integración con Flyway. El proyecto agrega esos módulos mediante Maven.

Referencias oficiales: [Flyway PostgreSQL](https://documentation.red-gate.com/flyway/reference/database-driver-reference/postgresql-database), [Spring Boot database initialization](https://docs.spring.io/spring-boot/how-to/data-initialization.html), [conexiones de Supabase](https://supabase.com/docs/guides/database/connecting-to-postgres).

## Extensiones y decisiones pendientes

- `pgvector` no se habilita en esta migración. `knowledge_chunks` conserva texto y metadatos; `embedding VECTOR` queda fuera hasta seleccionar proveedor/modelo, dimensión y comprobar que la extensión esté disponible en el proyecto. Después se propondrá una migración revisable `V2`; `CREATE EXTENSION` requiere autorización previa y no debe ejecutarse remotamente sin ella.
- No se agregaron restricciones de exclusión para intervalos solapados porque requieren `btree_gist`. Bloqueos y reservas concurrentes requieren transacción, bloqueo del alojamiento y validación en backend; las reglas completas deben implementarse antes de habilitar reservas.
- La creación de perfil al registrarse en Supabase Auth está pendiente. RLS está activo sin políticas; si en el futuro se decide exponer alguna tabla por la API de Supabase, se deberán definir políticas en una migración nueva. El backend Spring es el camino previsto para las operaciones comerciales.
- El resto de decisiones abiertas está en [docs/decisions/PENDIENTES.md](../decisions/PENDIENTES.md).
- La expiración de `PENDING`, política de pagos simulados, catálogo inicial de roles/categorías, reglas de eliminación de usuarios y estructura/retención de auditoría necesitan acuerdos de producto.
- `updated_at`, coherencia de precios/totales, reserva propia, identidad del revisor, participante/emisor de mensajes y reglas del estado `PUBLISHED` son responsabilidades del backend por ahora; no están garantizadas por la migración inicial.

## Operación local

El POM integra la API de Flyway con Spring Boot; no se agregó el plugin Maven de Flyway. Las migraciones se ejecutan arrancando la aplicación con `FLYWAY_ENABLED=true` solo en la sesión de terminal, según [Aplicación de V1](#aplicación-de-v1).

-- V3: esquema "bi" con vistas de solo lectura para reportes y Power BI, y el rol powerbi_reader.
--
-- Seguridad:
-- * El esquema bi no está expuesto en la Data API de Supabase (solo public) y no da acceso a anon ni authenticated.
-- * Las vistas NO usan security_invoker: su propietario es el rol que migra (postgres, dueño de las tablas), así que
--   leen public sin depender de RLS. powerbi_reader no tiene permisos sobre ninguna tabla de public: solo lee bi.
-- * Ninguna vista expone email, teléfono, avatar_url, ip_address, user_agent ni textos libres de usuarios
--   (mensajes, comentarios de reseñas, motivos de cancelación). Los meses y días se calculan en hora de Guatemala.
-- * Sin contraseñas en este archivo: el rol nace sin LOGIN.

-- 1. Esquema -------------------------------------------------------------------------------------------------------
CREATE SCHEMA IF NOT EXISTS bi;
REVOKE ALL ON SCHEMA bi FROM PUBLIC, anon, authenticated;

-- 2. Rol de solo lectura -------------------------------------------------------------------------------------------
-- Joshua le da LOGIN y contraseña a mano en el SQL Editor (ALTER ROLE powerbi_reader WITH LOGIN PASSWORD '...');
-- la contraseña nunca va al repositorio. El límite de conexiones protege las 15 del Session pooler.
DO $$
BEGIN
    IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'powerbi_reader') THEN
        CREATE ROLE powerbi_reader NOLOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOINHERIT CONNECTION LIMIT 3;
    END IF;
END
$$;

-- 3. Vistas por reporte --------------------------------------------------------------------------------------------

-- Ingresos por mes de aprobación del pago (paid_at), por moneda. Un pago REFUNDED se aprobó y luego se reembolsó:
-- cuenta como aprobado y como reembolsado en el mes en que se aprobó (V1 no guarda la fecha del reembolso).
CREATE OR REPLACE VIEW bi.ingresos_por_mes AS
SELECT CAST(date_trunc('month', p.paid_at AT TIME ZONE 'America/Guatemala') AS date) AS mes,
       CAST(p.currency AS text)                                                     AS moneda,
       COUNT(*)                                                                     AS pagos_aprobados,
       SUM(p.amount)                                                                AS monto_aprobado,
       COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'REFUNDED'), 0)              AS monto_reembolsado,
       COALESCE(SUM(p.amount) FILTER (WHERE p.status = 'APPROVED'), 0)              AS monto_neto
FROM payments p
WHERE p.status IN ('APPROVED', 'REFUNDED') AND p.paid_at IS NOT NULL
GROUP BY 1, 2;

-- Reservas por mes de entrada (check_in) y estado, con noches y monto total.
CREATE OR REPLACE VIEW bi.reservas_por_estado AS
SELECT CAST(date_trunc('month', CAST(b.check_in AS timestamp)) AS date) AS mes,
       CASE b.status
           WHEN 'PENDING' THEN 'Pendiente'
           WHEN 'CONFIRMED' THEN 'Confirmada'
           WHEN 'COMPLETED' THEN 'Completada'
           WHEN 'CANCELLED' THEN 'Cancelada'
           WHEN 'REJECTED' THEN 'Rechazada'
           ELSE b.status
       END                                              AS estado,
       CAST(b.currency AS text)                         AS moneda,
       COUNT(*)                                         AS reservas,
       SUM(b.check_out - b.check_in)                    AS noches,
       SUM(b.total)                                     AS monto_total
FROM bookings b
GROUP BY 1, 2, 3;

-- Una fila por noche reservada (CONFIRMED o COMPLETED) y alojamiento: la fecha permite filtrar cualquier periodo.
-- El porcentaje de ocupación (noches reservadas / días del periodo) se calcula en el reporte o en Power BI.
CREATE OR REPLACE VIEW bi.ocupacion_por_alojamiento AS
SELECT p.id                       AS alojamiento_id,
       p.title                    AS alojamiento,
       l.municipality             AS municipio,
       CAST(n.noche AS date)      AS fecha
FROM bookings b
JOIN properties p ON p.id = b.property_id
JOIN locations l ON l.id = p.location_id
CROSS JOIN LATERAL generate_series(CAST(b.check_in AS timestamp), CAST(b.check_out - 1 AS timestamp),
                                   interval '1 day') AS n(noche)
WHERE b.status IN ('CONFIRMED', 'COMPLETED');

-- Alojamientos vigentes (sin borrado lógico) por categoría, ubicación y estado, con su precio promedio.
CREATE OR REPLACE VIEW bi.alojamientos_por_categoria_municipio AS
SELECT c.name                            AS categoria,
       l.department                      AS departamento,
       l.municipality                    AS municipio,
       p.status                          AS estado,
       CAST(p.currency AS text)          AS moneda,
       COUNT(*)                          AS alojamientos,
       ROUND(AVG(p.price_per_night), 2)  AS precio_promedio_noche
FROM properties p
JOIN property_categories c ON c.id = p.category_id
JOIN locations l ON l.id = p.location_id
WHERE p.deleted_at IS NULL
GROUP BY 1, 2, 3, 4, 5;

-- Una fila por reseña (sin el comentario): la fecha permite filtrar; el promedio se calcula en el reporte.
CREATE OR REPLACE VIEW bi.alojamientos_mejor_calificados AS
SELECT p.id                                                         AS alojamiento_id,
       p.title                                                      AS alojamiento,
       l.municipality                                               AS municipio,
       CAST(r.created_at AT TIME ZONE 'America/Guatemala' AS date)  AS fecha_resena,
       r.rating                                                     AS calificacion
FROM reviews r
JOIN properties p ON p.id = r.property_id
JOIN locations l ON l.id = p.location_id;

-- Por anfitrión y mes de entrada de sus reservas CONFIRMED o COMPLETED: reservas y monto de pagos APPROVED de esas
-- reservas. "alojamientos" es la cantidad actual de alojamientos vigentes del anfitrión. Solo el nombre visible.
CREATE OR REPLACE VIEW bi.ingresos_por_anfitrion AS
WITH alojamientos AS (
    SELECT host_id, COUNT(*) AS alojamientos FROM properties WHERE deleted_at IS NULL GROUP BY host_id
), pagos AS (
    SELECT booking_id, SUM(amount) AS monto FROM payments WHERE status = 'APPROVED' GROUP BY booking_id
)
SELECT h.id                                              AS anfitrion_id,
       h.full_name                                       AS anfitrion,
       CAST(date_trunc('month', CAST(b.check_in AS timestamp)) AS date) AS mes,
       COALESCE(a.alojamientos, 0)                       AS alojamientos,
       COUNT(b.id)                                       AS reservas,
       COALESCE(SUM(pg.monto), 0)                        AS monto_aprobado
FROM bookings b
JOIN properties p ON p.id = b.property_id
JOIN profiles h ON h.id = p.host_id
LEFT JOIN alojamientos a ON a.host_id = h.id
LEFT JOIN pagos pg ON pg.booking_id = b.id
WHERE b.status IN ('CONFIRMED', 'COMPLETED')
GROUP BY h.id, h.full_name, 3, a.alojamientos;

-- Usuarios por mes de registro y rol. Un usuario con varios roles cuenta una vez en cada rol.
CREATE OR REPLACE VIEW bi.usuarios_por_mes_y_rol AS
SELECT CAST(date_trunc('month', pr.created_at AT TIME ZONE 'America/Guatemala') AS date) AS mes,
       COALESCE(r.name, 'SIN ROL')                                                       AS rol,
       COUNT(DISTINCT pr.id)                                                             AS usuarios
FROM profiles pr
LEFT JOIN profile_roles x ON x.profile_id = pr.id
LEFT JOIN roles r ON r.id = x.role_id
GROUP BY 1, 2;

-- Pagos por mes de creación, método y estado.
CREATE OR REPLACE VIEW bi.pagos_por_metodo_estado AS
SELECT CAST(date_trunc('month', p.created_at AT TIME ZONE 'America/Guatemala') AS date) AS mes,
       p.payment_method                                                                  AS metodo,
       p.status                                                                          AS estado,
       CAST(p.currency AS text)                                                          AS moneda,
       COUNT(*)                                                                          AS pagos,
       SUM(p.amount)                                                                     AS monto
FROM payments p
GROUP BY 1, 2, 3, 4;

-- 4. Dimensiones para Power BI -------------------------------------------------------------------------------------

-- Todos los alojamientos (también los borrados, para no romper relaciones con reservas históricas).
CREATE OR REPLACE VIEW bi.dim_alojamientos AS
SELECT p.id                        AS alojamiento_id,
       p.title                     AS titulo,
       c.name                      AS categoria,
       l.department                AS departamento,
       l.municipality              AS municipio,
       l.latitude                  AS latitud,
       l.longitude                 AS longitud,
       p.price_per_night           AS precio_por_noche,
       CAST(p.currency AS text)    AS moneda,
       p.capacity                  AS capacidad,
       p.status                    AS estado,
       (p.deleted_at IS NOT NULL)  AS eliminado
FROM properties p
JOIN property_categories c ON c.id = p.category_id
JOIN locations l ON l.id = p.location_id;

-- Calendario 2025-01-01 a 2027-12-31. "fecha" relaciona con las columnas de día (fecha, fecha_resena) y con las
-- columnas "mes" de las otras vistas, que guardan el primer día del mes.
CREATE OR REPLACE VIEW bi.dim_fechas AS
SELECT CAST(d AS date)                                      AS fecha,
       CAST(EXTRACT(YEAR FROM d) AS integer)                AS anio,
       CAST(EXTRACT(MONTH FROM d) AS integer)               AS numero_mes,
       (ARRAY['Enero', 'Febrero', 'Marzo', 'Abril', 'Mayo', 'Junio', 'Julio', 'Agosto', 'Septiembre',
              'Octubre', 'Noviembre', 'Diciembre'])[CAST(EXTRACT(MONTH FROM d) AS integer)] AS nombre_mes,
       to_char(d, 'YYYY-MM')                                AS anio_mes,
       CAST(EXTRACT(QUARTER FROM d) AS integer)             AS trimestre,
       CAST(date_trunc('month', d) AS date)                 AS inicio_mes
FROM generate_series(TIMESTAMP '2025-01-01', TIMESTAMP '2027-12-31', interval '1 day') AS d;

-- 5. Permisos ------------------------------------------------------------------------------------------------------
REVOKE ALL ON ALL TABLES IN SCHEMA bi FROM PUBLIC, anon, authenticated;
GRANT USAGE ON SCHEMA bi TO powerbi_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA bi TO powerbi_reader;
-- Vistas que se agreguen después en bi (creadas por el rol que migra) nacen legibles para powerbi_reader
-- y sin acceso para anon y authenticated.
ALTER DEFAULT PRIVILEGES IN SCHEMA bi GRANT SELECT ON TABLES TO powerbi_reader;
ALTER DEFAULT PRIVILEGES IN SCHEMA bi REVOKE ALL ON TABLES FROM PUBLIC, anon, authenticated;

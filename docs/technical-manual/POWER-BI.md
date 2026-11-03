# Power BI

El tablero de Power BI lee el esquema **`bi`** de Supabase (migración V3) con el rol de solo lectura
**`powerbi_reader`**. Es la misma base de producción y de desarrollo.

| Qué | Detalle |
|---|---|
| Esquema | `bi`: 8 vistas de reportes y 2 dimensiones (`dim_alojamientos`, `dim_fechas`) |
| Rol | `powerbi_reader`: solo `SELECT` sobre `bi`; ningún acceso a `public` ni permiso de escritura; máximo 3 conexiones |
| Datos personales | Ninguna vista tiene email, teléfono, avatar, IP, user agent ni textos libres (mensajes, comentarios); solo el nombre visible de los anfitriones |
| Exposición | `bi` no está en la Data API de Supabase; `anon` y `authenticated` no tienen acceso |

Los mismos datos alimentan los reportes 1 a 8 de la API (`GET /api/v1/reports`), que leen estas vistas.

---

## 0. Datos de demostración (antes de cargar Power BI y antes de la defensa)

Para que los 15 reportes y el tablero tengan datos, ejecutar **una vez** [scripts/demo-actividad.ps1](../../scripts/demo-actividad.ps1)
después de [scripts/seed-demo.ps1](../../scripts/seed-demo.ps1). Solo usa la API: crea 10 reservas en noviembre y
diciembre de 2026, confirma 8, rechaza 1 y deja 1 pendiente, registra pagos con tarjeta, transferencia y efectivo
(más 1 fallido), completa 3 estancias con reseña, reembolsa 1 pago y deja una conversación de chat respondida.

```powershell
# Contra producción (o -BaseUrl http://localhost:8080 en local)
powershell -ExecutionPolicy Bypass -File scripts\demo-actividad.ps1 -BaseUrl https://xela-stay-api.onrender.com
```

Pide la publishable key y las credenciales (sin guardarlas) del anfitrión con rol HOST y ADMIN, dueño de los
alojamientos, y de dos huéspedes de prueba. Se puede repetir sin duplicar: cada paso revisa el estado antes de actuar
y una reserva que choca por fechas se omite. La reserva pendiente vence sola a las 24 h y queda cancelada (aparece en
el reporte 9).

Después de ejecutarlo, en Power BI: **Inicio → Actualizar**.

## 1. Activar el rol (una sola vez, Joshua)

En el SQL Editor de Supabase. La contraseña se comparte con Pedro por un canal privado y nunca va al repositorio:

```sql
ALTER ROLE powerbi_reader WITH LOGIN PASSWORD '<contraseña>';
```

## 2. Conexión desde Power BI Desktop

1. **Obtener datos → Base de datos PostgreSQL**.
2. **Servidor**: `<host-pooler>:5432`, el host del **Session pooler** que aparece en Supabase → **Connect →
   Session pooler** (termina en `pooler.supabase.com`). No usar el puerto 6543 (Transaction pooler).
3. **Base de datos**: `postgres`.
4. **Modo de conectividad**: **Importar** (DirectQuery no es necesario y gastaría conexiones del pooler).
5. **Credenciales → Base de datos**:
   - Usuario: `powerbi_reader.<project-ref>`. El pooler exige el sufijo con el identificador del proyecto (el mismo
     que aparece en la URL de Supabase).
   - Contraseña: la del paso 1.
6. En el **Navegador**, abrir el esquema `bi` y marcar las vistas de la sección 3.

### Si falla el SSL o el certificado

Supabase acepta conexiones cifradas y Power BI las pide por defecto. Si aparece un error de certificado
(*"The remote certificate is invalid"* o similar):

1. En Supabase: **Project Settings → Database → SSL Configuration → Download certificate**.
2. En Windows: abrir el archivo `.crt` → **Instalar certificado** → *Equipo local* → **Colocar todos los
   certificados en el siguiente almacén** → *Entidades de certificación raíz de confianza*.
3. Cerrar y volver a abrir Power BI Desktop y reintentar la conexión.

Si la conexión se rechaza por cantidad de clientes (`max clients reached`), cerrar otras sesiones de Power BI o
backends locales: el pooler admite 15 conexiones para todo el proyecto y `powerbi_reader`, como máximo 3.

Si aparece un error de permisos (`permission denied for schema public`), se está intentando leer una tabla de
`public`: el rol solo puede leer `bi`.

## 3. Modelo de datos

| Vista | Grano | Columnas clave |
|---|---|---|
| `bi.ingresos_por_mes` | mes de aprobación del pago × moneda | `mes`, `pagos_aprobados`, `monto_aprobado`, `monto_reembolsado`, `monto_neto` |
| `bi.reservas_por_estado` | mes de entrada × estado × moneda | `mes`, `estado`, `reservas`, `noches`, `monto_total` |
| `bi.ocupacion_por_alojamiento` | una fila por noche reservada (confirmada o completada) | `alojamiento_id`, `fecha` |
| `bi.alojamientos_por_categoria_municipio` | categoría × departamento × municipio × estado (actual) | `alojamientos`, `precio_promedio_noche` |
| `bi.alojamientos_mejor_calificados` | una fila por reseña | `alojamiento_id`, `fecha_resena`, `calificacion` |
| `bi.ingresos_por_anfitrion` | anfitrión × mes de entrada | `anfitrion`, `mes`, `reservas`, `monto_aprobado` |
| `bi.usuarios_por_mes_y_rol` | mes de registro × rol | `mes`, `rol`, `usuarios` |
| `bi.pagos_por_metodo_estado` | mes × método × estado × moneda | `metodo`, `estado`, `pagos`, `monto` |
| `bi.dim_alojamientos` | alojamiento (incluye borrados, `eliminado = true`) | `alojamiento_id`, `titulo`, `categoria`, `municipio`, `latitud`, `longitud` |
| `bi.dim_fechas` | día de 2025-01-01 a 2027-12-31 | `fecha`, `anio`, `numero_mes`, `nombre_mes`, `anio_mes`, `trimestre`, `inicio_mes` |

Todas las columnas `mes` guardan el **primer día del mes**, así que se relacionan con `dim_fechas[fecha]`.

### Relaciones (vista Modelo)

| Desde (1) | Hacia (varios) |
|---|---|
| `dim_fechas[fecha]` | `ocupacion_por_alojamiento[fecha]` |
| `dim_fechas[fecha]` | `alojamientos_mejor_calificados[fecha_resena]` |
| `dim_fechas[fecha]` | `ingresos_por_mes[mes]`, `reservas_por_estado[mes]`, `ingresos_por_anfitrion[mes]`, `usuarios_por_mes_y_rol[mes]`, `pagos_por_metodo_estado[mes]` |
| `dim_alojamientos[alojamiento_id]` | `ocupacion_por_alojamiento[alojamiento_id]` |
| `dim_alojamientos[alojamiento_id]` | `alojamientos_mejor_calificados[alojamiento_id]` |

Ajustes:

- `dim_fechas`: **Marcar como tabla de fechas** con la columna `fecha`, y ordenar `nombre_mes` por `numero_mes`
  (*Herramientas de columnas → Ordenar por columna*).
- `dim_alojamientos[latitud]` y `[longitud]`: *Categoría de datos* **Latitud** y **Longitud**. Un alojamiento sin
  coordenadas no aparece en el mapa.
- Las relaciones con las columnas `mes` solo coinciden con el primer día de cada mes: para esas vistas, filtrar con
  `dim_fechas[anio_mes]` o `dim_fechas[anio]`, no con un rango de días.
- `bi.alojamientos_por_categoria_municipio` no tiene fecha (foto actual del catálogo): queda sin relación.

### Medidas DAX

Crear en una tabla de medidas (o en `ingresos_por_mes`):

```DAX
Ingreso total = SUM ( ingresos_por_mes[monto_aprobado] )

Ingreso neto = SUM ( ingresos_por_mes[monto_neto] )

Ocupación promedio % =
DIVIDE (
    COUNTROWS ( ocupacion_por_alojamiento ),
    COUNTROWS ( dim_fechas )
        * CALCULATE ( COUNTROWS ( dim_alojamientos ), dim_alojamientos[eliminado] = FALSE () )
)

Calificación promedio = AVERAGE ( alojamientos_mejor_calificados[calificacion] )
```

- *Ocupación promedio %*: noches reservadas sobre noches disponibles (días seleccionados × alojamientos vigentes). En
  un gráfico por alojamiento da la ocupación de cada uno; con un segmentador de fechas, la del periodo elegido.
  Darle formato de porcentaje.
- *Ingreso total* y *Ingreso neto*: formato de moneda en quetzales (`Q`).

## 4. Tablero sugerido (4 páginas)

| Página | Visuales |
|---|---|
| **Ingresos** | Tarjetas *Ingreso total* e *Ingreso neto*; gráfico de **líneas** por `dim_fechas[anio_mes]` con `monto_aprobado` y `monto_neto`; barras apiladas de `pagos_por_metodo_estado` (método × estado) |
| **Ocupación** | **Barras** horizontales por `dim_alojamientos[titulo]` con *Ocupación promedio %*; segmentador de fechas (`dim_fechas[fecha]`); columnas de `reservas_por_estado` por estado |
| **Alojamientos** | **Mapa** con `dim_alojamientos[latitud]` y `[longitud]`, tamaño por `precio_por_noche` y leyenda por `categoria`; matriz de `alojamientos_por_categoria_municipio` (categoría × municipio) |
| **Usuarios y reseñas** | Columnas apiladas de `usuarios_por_mes_y_rol` (mes × rol); tarjeta *Calificación promedio*; tabla por alojamiento con reseñas y *Calificación promedio*; barras de `ingresos_por_anfitrion` |

Un segmentador de `dim_fechas[anio_mes]` en cada página mantiene el periodo coherente.

## 5. Evidencia para la entrega

- Guardar el archivo como **`docs/powerbi/xela-stay.pbix`**. Power BI no guarda la contraseña dentro del `.pbix` (queda
  en las credenciales del equipo), pero antes de subirlo conviene revisar *Archivo → Opciones y configuración →
  Configuración de origen de datos* y no incluir capturas con la cadena de conexión completa.
- Exportar capturas de las 4 páginas a **`docs/powerbi/capturas/`** (`1-ingresos.png`, `2-ocupacion.png`,
  `3-alojamientos.png`, `4-usuarios-resenas.png`).
- Opcional: *Archivo → Exportar → PDF* del tablero en `docs/powerbi/xela-stay.pdf`.

## 6. Agregar una vista nueva

Nuevas vistas van en una migración versionada (V4 en adelante) dentro de `bi`, sin columnas de datos personales.
Por los privilegios por defecto de V3, una vista creada por el rol que migra nace legible para `powerbi_reader` y sin
acceso para `anon` ni `authenticated`.

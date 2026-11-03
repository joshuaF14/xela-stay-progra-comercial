<#
.SYNOPSIS
    Verificación rápida de Xela Stay en producción (sin credenciales).

.DESCRIPTION
    Solo hace peticiones GET públicas o sin token. La primera petición espera hasta 3 minutos porque la API del plan
    Free de Render se apaga tras 15 minutos sin uso y tarda cerca de 2 minutos en despertar.
    Termina con código 1 si alguna prueba falla.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\smoke-prod.ps1 `
        -ApiUrl https://xela-stay-api.onrender.com -WebUrl https://xela-stay-web.onrender.com
#>
param(
    [Parameter(Mandatory)] [string]$ApiUrl,
    [Parameter(Mandatory)] [string]$WebUrl
)

$ErrorActionPreference = 'Stop'
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
$ApiUrl = $ApiUrl.TrimEnd('/')
$WebUrl = $WebUrl.TrimEnd('/')
$results = New-Object System.Collections.Generic.List[object]

# GET que devuelve status, cuerpo como texto UTF-8 y encabezados, sin lanzar excepción por 4xx/5xx.
function Invoke-Get([string]$Url, [int]$TimeoutSec = 30, [hashtable]$Headers = @{}) {
    try {
        $r = Invoke-WebRequest -Uri $Url -UseBasicParsing -TimeoutSec $TimeoutSec -Headers $Headers
        $body = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
        return @{ Status = [int]$r.StatusCode; Body = $body; Headers = $r.Headers }
    } catch [System.Net.WebException] {
        $response = $_.Exception.Response
        if ($response) {
            $reader = New-Object IO.StreamReader($response.GetResponseStream(), [Text.Encoding]::UTF8)
            return @{ Status = [int]$response.StatusCode; Body = $reader.ReadToEnd(); Headers = $response.Headers }
        }
        return @{ Status = 0; Body = $_.Exception.Message; Headers = @{} }
    }
}

function Add-Result([string]$Name, [bool]$Ok, [string]$Detail) {
    $results.Add([pscustomobject]@{ Prueba = $Name; Resultado = $(if ($Ok) { 'OK' } else { 'FALLA' }); Detalle = $Detail })
    $color = if ($Ok) { 'Green' } else { 'Red' }
    Write-Host ("[{0}] {1} - {2}" -f $(if ($Ok) { 'OK' } else { 'FALLA' }), $Name, $Detail) -ForegroundColor $color
}

Write-Host "API: $ApiUrl"
Write-Host "Web: $WebUrl"
Write-Host 'Despertando la API (puede tardar hasta 3 minutos si estaba dormida)...'

# 1. Health de la API (la que usa Render como health check).
$watch = [Diagnostics.Stopwatch]::StartNew()
$health = Invoke-Get "$ApiUrl/api/health" 180
$watch.Stop()
$up = $health.Status -eq 200 -and $health.Body -match '"status"\s*:\s*"UP"'
Add-Result 'GET /api/health' $up ("HTTP {0} en {1:N0} s" -f $health.Status, $watch.Elapsed.TotalSeconds)

# 2. Alojamientos publicados (búsqueda pública).
$props = Invoke-Get "$ApiUrl/api/v1/properties?size=50"
$count = -1
if ($props.Status -eq 200) {
    try { $count = [int]($props.Body | ConvertFrom-Json).totalElements } catch { }
}
Add-Result 'GET /api/v1/properties' ($props.Status -eq 200 -and $count -ge 1) ("HTTP {0}, {1} alojamientos publicados" -f $props.Status, $count)

# 3. Categorías (catálogo público).
$cats = Invoke-Get "$ApiUrl/api/v1/property-categories"
$catCount = -1
if ($cats.Status -eq 200) {
    # En PowerShell 5.1 ConvertFrom-Json emite el arreglo como un solo objeto: se cuenta enumerándolo.
    try { $parsed = $cats.Body | ConvertFrom-Json; $catCount = ($parsed | Measure-Object).Count } catch { }
}
Add-Result 'GET /api/v1/property-categories' ($cats.Status -eq 200 -and $catCount -ge 1) ("HTTP {0}, {1} categorías" -f $cats.Status, $catCount)

# 4. Ruta protegida sin token: debe responder 401.
$me = Invoke-Get "$ApiUrl/api/v1/me"
Add-Result 'GET /api/v1/me sin token' ($me.Status -eq 401) ("HTTP {0} (se espera 401)" -f $me.Status)

# 5. CORS: la API acepta el origen del frontend.
$cors = Invoke-Get "$ApiUrl/api/v1/property-categories" 30 @{ Origin = $WebUrl }
$allowed = "$($cors.Headers['Access-Control-Allow-Origin'])"
Add-Result 'CORS para el frontend' ($allowed -eq $WebUrl) ("Access-Control-Allow-Origin: '{0}'" -f $allowed)

# 6. El frontend carga.
$web = Invoke-Get "$WebUrl/"
Add-Result 'GET frontend /' ($web.Status -eq 200 -and $web.Body -match 'id="root"') ("HTTP {0}" -f $web.Status)

# 7. Una ruta de React Router responde index.html (regla de reescritura /* -> /index.html).
$spa = Invoke-Get "$WebUrl/login"
Add-Result 'GET frontend /login (SPA)' ($spa.Status -eq 200 -and $spa.Body -match 'id="root"') ("HTTP {0}" -f $spa.Status)

# Resumen
Write-Host ''
$results | Format-Table -AutoSize
$failed = @($results | Where-Object { $_.Resultado -eq 'FALLA' }).Count
if ($failed -gt 0) {
    Write-Host "$failed prueba(s) fallaron. Ver docs/technical-manual/DESPLIEGUE.md, sección Problemas comunes." -ForegroundColor Red
    exit 1
}
Write-Host 'Todo OK.' -ForegroundColor Green

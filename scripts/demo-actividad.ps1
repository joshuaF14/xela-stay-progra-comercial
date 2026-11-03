<#
.SYNOPSIS
    Genera actividad de demostración (reservas, pagos, reseñas, chat) a través de la API de Xela Stay.

.DESCRIPTION
    Pensado para que los 15 reportes y el tablero de Power BI tengan datos. Usa solo la API (sin SQL) y no guarda
    ninguna credencial. Requiere los alojamientos de scripts/seed-demo.ps1 publicados y tres usuarios de Supabase Auth
    con el email confirmado: el anfitrión (con rol HOST y ADMIN, dueño de los alojamientos) y dos huéspedes.

    Pasos: 10 reservas en noviembre y diciembre de 2026 → el anfitrión confirma 8, rechaza 1 y deja 1 PENDING →
    los huéspedes pagan las confirmadas (CARD, TRANSFER, CASH) y simulan 1 pago fallido → el anfitrión completa 3 →
    los huéspedes reseñan esas 3 → el ADMIN reembolsa 1 pago → 1 conversación de chat con respuesta del anfitrión.

    Se puede repetir: cada reserva usa una clave de idempotencia fija (la API devuelve la misma reserva), cada paso
    revisa el estado antes de actuar y una reserva que choca por fechas (409) se omite.

.PARAMETER BaseUrl
    URL del backend. Por defecto http://localhost:8080. Producción: https://xela-stay-api.onrender.com

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\demo-actividad.ps1
    powershell -ExecutionPolicy Bypass -File scripts\demo-actividad.ps1 -BaseUrl https://xela-stay-api.onrender.com
#>
param(
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$SupabaseAuthUrl = 'https://hnflawvzlrttdkbseahe.supabase.co/auth/v1/token?grant_type=password'
$BaseUrl = $BaseUrl.TrimEnd('/')
$script:Http = New-Object System.Net.Http.HttpClient
$script:Http.Timeout = [TimeSpan]::FromSeconds(180)   # la API de Render puede estar despertando

# ---------------------------------------------------------------------------------------------------------------
# HTTP (mismo enfoque que seed-demo.ps1: StringContent UTF-8, nunca byte[])
# ---------------------------------------------------------------------------------------------------------------

function Get-ErrorText([string]$Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return '(sin cuerpo)' }
    try { $body = $Text | ConvertFrom-Json } catch { return $Text }
    $message = $body.message
    if (-not $message) { $message = $body.error_description }
    if (-not $message) { $message = $body.msg }
    if (-not $message) { $message = $body.error }
    if (-not $message) { return $Text }
    if ($body.details) {
        $message += ' (' + (($body.details | ForEach-Object { "$($_.field): $($_.message)" }) -join '; ') + ')'
    }
    return $message
}

# Devuelve @{ Status; Body; Error }. No lanza excepción por 4xx/5xx: cada paso decide qué hacer.
function Invoke-Json {
    param([string]$Method, [string]$Url, $Body, [string]$Token, [hashtable]$Headers = @{})
    $request = New-Object System.Net.Http.HttpRequestMessage((New-Object System.Net.Http.HttpMethod($Method.ToUpperInvariant())), $Url)
    $response = $null
    try {
        foreach ($name in $Headers.Keys) { $null = $request.Headers.TryAddWithoutValidation($name, [string]$Headers[$name]) }
        if ($Token) { $null = $request.Headers.TryAddWithoutValidation('Authorization', "Bearer $Token") }
        if ($null -ne $Body) {
            $json = ConvertTo-Json -InputObject $Body -Depth 10
            $request.Content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, 'application/json')
        }
        try {
            $response = $script:Http.SendAsync($request).GetAwaiter().GetResult()
        } catch {
            return @{ Status = 0; Body = $null; Error = "Sin respuesta de $Url ($($_.Exception.Message))" }
        }
        $text = [Text.Encoding]::UTF8.GetString($response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult())
        $status = [int]$response.StatusCode
        if ($status -ge 200 -and $status -lt 300) {
            $parsed = if ([string]::IsNullOrWhiteSpace($text)) { $null } else { $text | ConvertFrom-Json }
            return @{ Status = $status; Body = $parsed; Error = $null }
        }
        return @{ Status = $status; Body = $null; Error = "HTTP $status - $(Get-ErrorText $text)" }
    } finally {
        if ($response) { $response.Dispose() }
        $request.Dispose()
    }
}

# Llamada que debe salir bien: si falla, detiene el script con el mensaje del ApiError.
function Invoke-Required([string]$Method, [string]$Path, $Body, [string]$Token) {
    $r = Invoke-Json -Method $Method -Url "$BaseUrl$Path" -Body $Body -Token $Token
    if ($r.Error) { throw "$Method $Path -> $($r.Error)" }
    return $r.Body
}

# Elementos de una respuesta JSON que es un arreglo (PowerShell 5.1 lo entrega como un solo objeto).
function ConvertTo-Items($Value) {
    if ($null -eq $Value) { return @() }
    return @($Value | ForEach-Object { $_ })
}

function Get-Token([string]$Label, [string]$AnonKey) {
    $email = Read-Host "Email del $Label"
    $secure = Read-Host "Contraseña del $Label" -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        $credentials = @{ email = $email; password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
        $r = Invoke-Json -Method Post -Url $SupabaseAuthUrl -Body $credentials -Headers @{ apikey = $AnonKey }
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        $credentials = $null
    }
    if ($r.Error -or -not $r.Body.access_token) { throw "Inicio de sesión del $Label fallido: $($r.Error)" }
    return $r.Body.access_token
}

function Write-Step([string]$Text) { Write-Host $Text -ForegroundColor Cyan }
function Write-Skip([string]$Text) { Write-Host "  - $Text" -ForegroundColor Yellow }
function Write-Done([string]$Text) { Write-Host "  + $Text" -ForegroundColor Green }

# ---------------------------------------------------------------------------------------------------------------
# Plan de la demostración
# ---------------------------------------------------------------------------------------------------------------

# Fechas futuras distintas (noviembre y diciembre de 2026): [entrada, salida).
$Slots = @(
    @('2026-11-12', '2026-11-14'), @('2026-11-15', '2026-11-17'), @('2026-11-19', '2026-11-22'),
    @('2026-11-24', '2026-11-26'), @('2026-11-28', '2026-11-30'), @('2026-12-03', '2026-12-05'),
    @('2026-12-08', '2026-12-11'), @('2026-12-13', '2026-12-15'), @('2026-12-18', '2026-12-20'),
    @('2026-12-22', '2026-12-24')
)
$ConfirmUpTo = 7          # reservas 0..7 se confirman
$RejectIndex = 8          # la 8 se rechaza con motivo
$PendingIndex = 9         # la 9 queda PENDING
$FailedPaymentIndex = 7   # la 7 solo tiene un pago fallido
$CompleteIndexes = @(0, 1, 2)
$Ratings = @{ 0 = 5; 1 = 4; 2 = 5 }
$Comments = @{
    0 = 'Muy limpio y cómodo, el anfitrión respondió rápido.'
    1 = 'Buena ubicación; el agua caliente tardó un poco.'
    2 = 'Excelente vista y muy tranquilo. Volvería.'
}
$RefundIndex = 3
$Methods = @('CARD', 'TRANSFER', 'CASH')

function Start-Demo {
    Write-Host 'Xela Stay - actividad de demostración' -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"
    $health = Invoke-Json -Method Get -Url "$BaseUrl/api/health"
    if ($health.Status -ne 200) { throw "La API no responde en $BaseUrl/api/health ($($health.Error))" }

    $anonKey = Read-Host 'Publishable/anon key de Supabase'
    $hostToken = Get-Token 'anfitrión/ADMIN' $anonKey
    $guestTokens = @((Get-Token 'huésped 1' $anonKey), (Get-Token 'huésped 2' $anonKey))

    $hostMe = Invoke-Required Get '/api/v1/me' $null $hostToken
    if ($hostMe.roles -notcontains 'HOST') { throw 'El usuario anfitrión no tiene rol HOST (ejecuta antes seed-demo.ps1).' }
    $isAdmin = $hostMe.roles -contains 'ADMIN'
    $guestIds = @()
    foreach ($t in $guestTokens) { $guestIds += (Invoke-Required Get '/api/v1/me' $null $t).id }
    if ($guestIds -contains $hostMe.id) { throw 'Los huéspedes deben ser usuarios distintos del anfitrión.' }

    # Alojamientos publicados del anfitrión, en orden estable (para que cada reserva caiga siempre en el mismo).
    $page = Invoke-Required Get '/api/v1/properties?size=50&sort=title,asc' $null $null
    $properties = @(ConvertTo-Items $page.content | Where-Object { $_.hostId -eq $hostMe.id })
    if ($properties.Count -eq 0) { throw 'El anfitrión no tiene alojamientos publicados (ejecuta antes seed-demo.ps1).' }
    Write-Host "Alojamientos publicados del anfitrión: $($properties.Count)"

    # 1. Reservas -------------------------------------------------------------------------------------------------
    Write-Step '1. Reservas'
    $bookings = @{}
    for ($i = 0; $i -lt $Slots.Count; $i++) {
        $property = $properties[$i % $properties.Count]
        $guest = $i % 2
        $body = @{
            propertyId     = $property.id
            checkIn        = $Slots[$i][0]
            checkOut       = $Slots[$i][1]
            guestsCount    = [Math]::Min(2, [int]$property.capacity)
            idempotencyKey = "demo-actividad-$i"
        }
        $r = Invoke-Json -Method Post -Url "$BaseUrl/api/v1/bookings" -Body $body -Token $guestTokens[$guest]
        if ($r.Status -eq 409) { Write-Skip "Reserva $i omitida ($($r.Error))"; continue }
        if ($r.Error) { throw "POST /api/v1/bookings (reserva $i) -> $($r.Error)" }
        $bookings[$i] = @{ Id = $r.Body.id; Guest = $guest; Status = $r.Body.status }
        Write-Done ("Reserva {0}: {1} del {2} al {3} ({4})" -f $i, $property.title, $Slots[$i][0], $Slots[$i][1], $r.Body.status)
    }

    function Update-Status([int]$i, [string]$action, $body) {
        $b = $bookings[$i]
        $r = Invoke-Json -Method Patch -Url "$BaseUrl/api/v1/bookings/$($b.Id)/$action" -Body $body -Token $hostToken
        if ($r.Error) { Write-Skip "Reserva ${i}: $action no aplicado ($($r.Error))"; return }
        $b.Status = $r.Body.status
        Write-Done "Reserva ${i}: $($r.Body.status)"
    }

    # 2. Confirmar, rechazar y dejar una pendiente ---------------------------------------------------------------
    Write-Step '2. Respuesta del anfitrión'
    foreach ($i in @($bookings.Keys | Sort-Object)) {
        if ($bookings[$i].Status -ne 'PENDING') { continue }
        if ($i -le $ConfirmUpTo) { Update-Status $i 'confirm' $null }
        elseif ($i -eq $RejectIndex) { Update-Status $i 'reject' @{ reason = 'Fechas reservadas para mantenimiento' } }
        elseif ($i -eq $PendingIndex) { Write-Skip "Reserva ${i}: queda PENDING a propósito" }
    }

    # 3. Pagos ---------------------------------------------------------------------------------------------------
    Write-Step '3. Pagos'
    foreach ($i in @($bookings.Keys | Sort-Object)) {
        $b = $bookings[$i]
        if ($i -gt $ConfirmUpTo -or $b.Status -ne 'CONFIRMED') { continue }
        $token = $guestTokens[$b.Guest]
        $existing = ConvertTo-Items (Invoke-Required Get "/api/v1/bookings/$($b.Id)/payments" $null $token)
        $failed = $i -eq $FailedPaymentIndex
        if (($failed -and $existing.Count -gt 0) -or (-not $failed -and @($existing | Where-Object { $_.status -in 'APPROVED', 'REFUNDED' }).Count -gt 0)) {
            Write-Skip "Reserva ${i}: ya tiene pago"
            continue
        }
        $body = @{ paymentMethod = $Methods[$i % $Methods.Count]; simulateFailure = $failed }
        $r = Invoke-Json -Method Post -Url "$BaseUrl/api/v1/bookings/$($b.Id)/payments" -Body $body -Token $token
        if ($r.Error) { Write-Skip "Reserva ${i}: pago no creado ($($r.Error))"; continue }
        Write-Done ("Reserva {0}: pago {1} con {2} por {3} {4}" -f $i, $r.Body.status, $r.Body.paymentMethod, $r.Body.amount, $r.Body.currency)
    }

    # 4. Completar -----------------------------------------------------------------------------------------------
    Write-Step '4. Estancias completadas'
    foreach ($i in $CompleteIndexes) {
        if ($bookings.ContainsKey($i) -and $bookings[$i].Status -eq 'CONFIRMED') { Update-Status $i 'complete' $null }
    }

    # 5. Reseñas -------------------------------------------------------------------------------------------------
    Write-Step '5. Reseñas'
    $reviewsCreated = 0
    foreach ($i in $CompleteIndexes) {
        if (-not $bookings.ContainsKey($i) -or $bookings[$i].Status -ne 'COMPLETED') { continue }
        $b = $bookings[$i]
        $r = Invoke-Json -Method Post -Url "$BaseUrl/api/v1/bookings/$($b.Id)/review" `
            -Body @{ rating = $Ratings[$i]; comment = $Comments[$i] } -Token $guestTokens[$b.Guest]
        if ($r.Status -eq 409) { Write-Skip "Reserva ${i}: ya tiene reseña"; continue }
        if ($r.Error) { Write-Skip "Reserva ${i}: reseña no creada ($($r.Error))"; continue }
        $reviewsCreated++
        Write-Done "Reserva ${i}: reseña de $($Ratings[$i]) estrellas"
    }

    # 6. Reembolso (ADMIN) ---------------------------------------------------------------------------------------
    Write-Step '6. Reembolso'
    if (-not $isAdmin) {
        Write-Skip 'El anfitrión no tiene rol ADMIN: se omite el reembolso.'
    } elseif ($bookings.ContainsKey($RefundIndex)) {
        $b = $bookings[$RefundIndex]
        $payments = ConvertTo-Items (Invoke-Required Get "/api/v1/bookings/$($b.Id)/payments" $null $hostToken)
        $approved = $payments | Where-Object { $_.status -eq 'APPROVED' } | Select-Object -First 1
        if ($approved) {
            $r = Invoke-Json -Method Patch -Url "$BaseUrl/api/v1/payments/$($approved.id)/refund" -Token $hostToken
            if ($r.Error) { Write-Skip "Reembolso no aplicado ($($r.Error))" } else { Write-Done "Pago de la reserva $RefundIndex reembolsado" }
        } else {
            Write-Skip "La reserva $RefundIndex no tiene un pago aprobado para reembolsar (¿ya se reembolsó?)"
        }
    }

    # 7. Chat (reporte 12: tiempo de respuesta del anfitrión) ----------------------------------------------------
    Write-Step '7. Chat'
    $conversation = Invoke-Required Post '/api/v1/conversations' @{ propertyId = $properties[0].id } $guestTokens[0]
    $messages = ConvertTo-Items (Invoke-Required Get "/api/v1/conversations/$($conversation.id)/messages?size=50" $null $guestTokens[0]).content
    if (@($messages | Where-Object { $_.senderId -eq $guestIds[0] }).Count -eq 0) {
        $null = Invoke-Required Post "/api/v1/conversations/$($conversation.id)/messages" @{ content = '¡Hola! ¿El alojamiento tiene parqueo para un carro?' } $guestTokens[0]
        Write-Done 'Mensaje del huésped'
    }
    if (@($messages | Where-Object { $_.senderId -eq $hostMe.id }).Count -eq 0) {
        $null = Invoke-Required Post "/api/v1/conversations/$($conversation.id)/messages" @{ content = 'Sí, hay parqueo privado sin costo. ¡Bienvenido!' } $hostToken
        Write-Done 'Respuesta del anfitrión'
    } else {
        Write-Skip 'La conversación ya tiene respuesta del anfitrión'
    }

    # Resumen ----------------------------------------------------------------------------------------------------
    Write-Host ''
    Write-Host 'Resumen' -ForegroundColor Cyan
    $hosting = ConvertTo-Items (Invoke-Required Get '/api/v1/me/hosting/bookings?size=100' $null $hostToken).content
    Write-Host 'Reservas del anfitrión por estado:'
    $hosting | Group-Object status | Sort-Object Name | ForEach-Object { Write-Host ("  {0,-10} {1}" -f $_.Name, $_.Count) }
    if ($isAdmin) {
        $payments = ConvertTo-Items (Invoke-Required Get '/api/v1/payments?size=100' $null $hostToken).content
        Write-Host 'Pagos por estado:'
        $payments | Group-Object status | Sort-Object Name | ForEach-Object { Write-Host ("  {0,-10} {1}" -f $_.Name, $_.Count) }
    }
    Write-Host "Reseñas creadas en esta ejecución: $reviewsCreated"
    Write-Host 'La reserva PENDING vence sola a las 24 h (queda CANCELLED: aparece en el reporte 9).'
}

try {
    Start-Demo
} finally {
    $script:Http.Dispose()
}

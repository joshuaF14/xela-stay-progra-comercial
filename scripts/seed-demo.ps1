<#
.SYNOPSIS
    Crea alojamientos de demostración en Quetzaltenango a través de la API de Xela Stay.

.DESCRIPTION
    Inicia sesión en Supabase Auth con email y contraseña, se asigna el rol HOST y crea 4 alojamientos
    publicados (con ubicación, imágenes y amenidades) usando solo endpoints públicos de la API.
    Es idempotente: omite los alojamientos propios que ya tengan el mismo título.
    No guarda la key, el email, la contraseña ni el token.

    Todas las peticiones pasan por Invoke-Json (HttpClient + StringContent en UTF-8). Al cargar el script con
    dot-sourcing (". .\scripts\seed-demo.ps1") solo se definen las funciones, sin pedir datos ni llamar a la red.

.PARAMETER BaseUrl
    URL del backend. Por defecto http://localhost:8080.

.EXAMPLE
    powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1
    powershell -ExecutionPolicy Bypass -File scripts\seed-demo.ps1 -BaseUrl http://localhost:8081
#>
param(
    [string]$BaseUrl = 'http://localhost:8080'
)

$ErrorActionPreference = 'Stop'
Add-Type -AssemblyName System.Net.Http
# PowerShell 5.1 (.NET Framework) no siempre negocia TLS 1.2, que Supabase exige.
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12

$SupabaseAuthUrl = 'https://hnflawvzlrttdkbseahe.supabase.co/auth/v1/token?grant_type=password'
$BaseUrl = $BaseUrl.TrimEnd('/')
$script:Http = New-Object System.Net.Http.HttpClient
$script:Http.Timeout = [TimeSpan]::FromSeconds(30)
$script:AccessToken = $null

# ---------------------------------------------------------------------------------------------------------------
# HTTP
# ---------------------------------------------------------------------------------------------------------------

# Texto legible de un cuerpo de error: ApiError del backend ({message, details}) o error de Supabase.
function Get-ErrorText([string]$Text) {
    if ([string]::IsNullOrWhiteSpace($Text)) { return '(sin cuerpo)' }
    try {
        $body = $Text | ConvertFrom-Json
    } catch {
        return $Text
    }
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

<#
    Envía una petición JSON y devuelve la respuesta ya convertida con ConvertFrom-Json ($null si viene vacía).
    El cuerpo se serializa con ConvertTo-Json y viaja como texto UTF-8 (StringContent), nunca como byte[].
    Si el status no es 2xx, lanza "HTTP <status> - <mensaje>".
#>
function Invoke-Json {
    param(
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$Url,
        $Body,
        [hashtable]$Headers = @{}
    )
    $httpMethod = New-Object System.Net.Http.HttpMethod($Method.ToUpperInvariant())
    $request = New-Object System.Net.Http.HttpRequestMessage($httpMethod, $Url)
    $response = $null
    try {
        foreach ($name in $Headers.Keys) {
            $null = $request.Headers.TryAddWithoutValidation($name, [string]$Headers[$name])
        }
        if ($null -ne $Body) {
            $json = ConvertTo-Json -InputObject $Body -Depth 10
            $request.Content = New-Object System.Net.Http.StringContent($json, [Text.Encoding]::UTF8, 'application/json')
        }
        try {
            $response = $script:Http.SendAsync($request).GetAwaiter().GetResult()
        } catch {
            $reason = $_.Exception.Message
            if ($_.Exception.InnerException) { $reason += ' ' + $_.Exception.InnerException.Message }
            throw "Sin respuesta de $Url ($reason)"
        }
        # Se decodifica siempre como UTF-8, aunque el servidor no declare charset.
        $bytes = $response.Content.ReadAsByteArrayAsync().GetAwaiter().GetResult()
        $text = [Text.Encoding]::UTF8.GetString($bytes)
        $status = [int]$response.StatusCode
        if ($status -lt 200 -or $status -ge 300) {
            throw "HTTP $status - $(Get-ErrorText $text)"
        }
        if ([string]::IsNullOrWhiteSpace($text)) {
            return $null
        }
        return $text | ConvertFrom-Json
    } finally {
        if ($response) { $response.Dispose() }
        $request.Dispose()
    }
}

# Llamada al backend con el token de la sesión.
function Invoke-Api {
    param(
        [Parameter(Mandatory)] [string]$Method,
        [Parameter(Mandatory)] [string]$Path,
        $Body
    )
    try {
        return Invoke-Json -Method $Method -Url "$BaseUrl$Path" -Body $Body `
            -Headers @{ Authorization = "Bearer $script:AccessToken" }
    } catch {
        throw "$Method $Path -> $($_.Exception.Message)"
    }
}

function Get-Id($Items, [string]$Name, [string]$Kind) {
    $match = $Items | Where-Object { $_.name -eq $Name } | Select-Object -First 1
    if (-not $match) {
        throw "No existe la $Kind '$Name' en la API (¿se aplicó V2?)."
    }
    return $match.id
}

# ---------------------------------------------------------------------------------------------------------------
# Datos de demostración (coordenadas aproximadas)
# ---------------------------------------------------------------------------------------------------------------

$DemoProperties = @(
    @{
        category  = 'Cabaña'
        title     = 'Cabaña El Mirador de San Nicolás'
        description = "Cabaña de madera con chimenea, terraza y vista al valle de Quetzaltenango. Rodeada de bosque, ideal para descansar del frío de Xela con una taza de café de la región."
        price     = 450.00; capacity = 4; bedrooms = 2; bathrooms = 1.0
        location  = @{ department = 'Quetzaltenango'; municipality = 'Quetzaltenango'
                       address = 'Camino a San Nicolás, zona 6'; latitude = 14.8560; longitude = -91.5050 }
        amenities = 'WiFi', 'Parqueo', 'Cocina', 'Agua caliente'
        images    = @(
            @{ imageUrl = 'https://images.unsplash.com/photo-1542314831-068cd1dbfeeb?auto=format&fit=crop&w=1200&q=80'; caption = 'Fachada de la cabaña'; cover = $true }
            @{ imageUrl = 'https://images.unsplash.com/photo-1449844908441-8829872d2607?auto=format&fit=crop&w=1200&q=80'; caption = 'Terraza con vista' }
            @{ imageUrl = 'https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=1200&q=80'; caption = 'Habitación principal' }
        )
    },
    @{
        category  = 'Apartamento'
        title     = 'Apartamento colonial junto al Parque Central'
        description = "Apartamento renovado en una casona del Centro Histórico, a dos cuadras del Parque Centro América y del Teatro Municipal. Techos altos, luz natural y cafés a pocos pasos."
        price     = 380.00; capacity = 2; bedrooms = 1; bathrooms = 1.0
        location  = @{ department = 'Quetzaltenango'; municipality = 'Quetzaltenango'
                       address = '12 avenida y 4a calle, zona 1'; latitude = 14.8347; longitude = -91.5181 }
        amenities = 'WiFi', 'Cocina', 'Agua caliente'
        images    = @(
            @{ imageUrl = 'https://images.unsplash.com/photo-1502672260266-1c1ef2d93688?auto=format&fit=crop&w=1200&q=80'; caption = 'Sala'; cover = $true }
            @{ imageUrl = 'https://images.unsplash.com/photo-1522708323590-d24dbb6b0267?auto=format&fit=crop&w=1200&q=80'; caption = 'Comedor y cocina' }
        )
    },
    @{
        category  = 'Casa'
        title     = 'Casa de campo en Zunil cerca de las Fuentes Georginas'
        description = "Casa amplia entre cultivos y montañas, a pocos minutos de las aguas termales de las Fuentes Georginas. Jardín privado, cocina equipada y desayuno típico incluido."
        price     = 850.00; capacity = 6; bedrooms = 3; bathrooms = 2.5
        location  = @{ department = 'Quetzaltenango'; municipality = 'Zunil'
                       address = 'Carretera a Fuentes Georginas, aldea Pachavac'; latitude = 14.7560; longitude = -91.4820 }
        amenities = 'Parqueo', 'Cocina', 'Agua caliente', 'Desayuno incluido'
        images    = @(
            @{ imageUrl = 'https://images.unsplash.com/photo-1580587771525-78b9dba3b914?auto=format&fit=crop&w=1200&q=80'; caption = 'Vista de la casa'; cover = $true }
            @{ imageUrl = 'https://images.unsplash.com/photo-1586023492125-27b2c045efd7?auto=format&fit=crop&w=1200&q=80'; caption = 'Sala con chimenea' }
            @{ imageUrl = 'https://images.unsplash.com/photo-1518780664697-55e3ad937233?auto=format&fit=crop&w=1200&q=80'; caption = 'Jardín' }
        )
    },
    @{
        category  = 'Hostal'
        title     = 'Hostal Los Altos en la zona 3'
        description = "Hostal sencillo y acogedor para viajeros y estudiantes de español, cerca de la Terminal Minerva y de restaurantes. Cocina compartida, agua caliente y ambiente tranquilo."
        price     = 250.00; capacity = 3; bedrooms = 1; bathrooms = 1.0
        location  = @{ department = 'Quetzaltenango'; municipality = 'Quetzaltenango'
                       address = '4a calle, zona 3'; latitude = 14.8440; longitude = -91.5260 }
        amenities = 'WiFi', 'Agua caliente', 'Se permiten mascotas'
        images    = @(
            @{ imageUrl = 'https://images.unsplash.com/photo-1555854877-bab0e564b8d5?auto=format&fit=crop&w=1200&q=80'; caption = 'Habitación'; cover = $true }
            @{ imageUrl = 'https://images.unsplash.com/photo-1566073771259-6a8506099945?auto=format&fit=crop&w=1200&q=80'; caption = 'Área común' }
        )
    }
)

# ---------------------------------------------------------------------------------------------------------------
# Flujo principal
# ---------------------------------------------------------------------------------------------------------------

function Start-Seed {
    Write-Host 'Xela Stay - datos de demostración' -ForegroundColor Cyan
    Write-Host "Backend: $BaseUrl"

    # 1. Inicio de sesión en Supabase Auth. La contraseña solo existe en memoria y se borra al terminar.
    $anonKey = Read-Host 'Publishable/anon key de Supabase'
    $email = Read-Host 'Email'
    $securePassword = Read-Host 'Contraseña' -AsSecureString
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($securePassword)
    try {
        $credentials = @{ email = $email; password = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr) }
        $login = Invoke-Json -Method Post -Url $SupabaseAuthUrl -Body $credentials -Headers @{ apikey = $anonKey }
    } catch {
        throw "Inicio de sesión fallido: $($_.Exception.Message)"
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        $credentials = $null
    }
    $script:AccessToken = $login.access_token
    if (-not $script:AccessToken) { throw 'Supabase no devolvió access_token.' }
    Write-Host 'Sesión iniciada.' -ForegroundColor Green

    # 2. Rol HOST (idempotente: si ya lo tiene, el backend no hace nada).
    $me = Invoke-Api -Method Post -Path '/api/v1/me/host'
    Write-Host "Perfil: $($me.fullName) - roles: $($me.roles -join ', ')"

    # 3. Catálogo real (ids de la API).
    $categories = Invoke-Api -Method Get -Path '/api/v1/property-categories'
    $amenities = Invoke-Api -Method Get -Path '/api/v1/amenities'
    $amenityId = @{}
    foreach ($name in 'WiFi', 'Parqueo', 'Cocina', 'Agua caliente', 'Desayuno incluido', 'Se permiten mascotas',
            'Piscina', 'Aire acondicionado') {
        $amenityId[$name] = Get-Id $amenities $name 'amenidad'
    }

    # 4. Alojamientos. Se omiten los propios con el mismo título (cualquier estado).
    $mine = Invoke-Api -Method Get -Path '/api/v1/me/properties?size=100'
    $existingTitles = @($mine.content | ForEach-Object { $_.title })

    foreach ($item in $DemoProperties) {
        if ($existingTitles -contains $item.title) {
            Write-Host "Omitido (ya existe): $($item.title)" -ForegroundColor Yellow
            continue
        }
        Write-Host "Creando: $($item.title)"

        # PropertyRequest: ubicación nueva en la misma petición (location en lugar de locationId).
        $property = Invoke-Api -Method Post -Path '/api/v1/properties' -Body @{
            categoryId    = Get-Id $categories $item.category 'categoría'
            location      = $item.location
            title         = $item.title
            description   = $item.description
            pricePerNight = [decimal]$item.price
            currency      = 'GTQ'
            capacity      = $item.capacity
            bedrooms      = $item.bedrooms
            bathrooms     = [decimal]$item.bathrooms
        }

        # PropertyImageRequest: la marcada con cover queda como portada.
        $order = 0
        foreach ($image in $item.images) {
            $null = Invoke-Api -Method Post -Path "/api/v1/properties/$($property.id)/images" -Body @{
                imageUrl     = $image.imageUrl
                caption      = $image.caption
                displayOrder = $order
                cover        = [bool]$image.cover
            }
            $order++
        }

        # PropertyAmenitiesRequest: lista completa de amenidades.
        $null = Invoke-Api -Method Put -Path "/api/v1/properties/$($property.id)/amenities" -Body @{
            amenityIds = @($item.amenities | ForEach-Object { $amenityId[$_] })
        }

        # PropertyStatusRequest: publicar (requiere al menos una imagen).
        $null = Invoke-Api -Method Patch -Path "/api/v1/properties/$($property.id)/status" -Body @{ status = 'PUBLISHED' }
        Write-Host "  Publicado: $($property.id)" -ForegroundColor Green
    }

    # 5. Resumen.
    $final = Invoke-Api -Method Get -Path '/api/v1/me/properties?size=100'
    Write-Host ''
    Write-Host 'Mis alojamientos:' -ForegroundColor Cyan
    $final.content | Select-Object id, title, status | Format-Table -AutoSize
}

# Con dot-sourcing (". .\scripts\seed-demo.ps1") solo se cargan las funciones (para pruebas).
if ($MyInvocation.InvocationName -ne '.') {
    try {
        Start-Seed
    } finally {
        $script:AccessToken = $null
        $script:Http.Dispose()
    }
}

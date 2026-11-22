<#
.SYNOPSIS
  Respaldo completo de la base datec_catalogo con pg_dump (formato personalizado).

.DESCRIPTION
  1. Genera respaldos\respaldo_datec_AAAA-MM-DD.dump (comprimido, se recupera con pg_restore).
  2. Comprueba el archivo con pg_restore --list (que se pueda leer y tenga los datos de las 7 tablas).
  3. Opcional: copia los roles y usuarios (-IncluirRoles) y descarga las imágenes (-Imagenes).
  4. Opcional: copia el respaldo a otra ubicación (-CopiaExterna), por ejemplo un USB o OneDrive.
  5. Solo si el respaldo nuevo es correcto, aplica la retención: 7 diarios, 4 semanales, 3 mensuales.
  6. Registra cada operación (correcta o fallida) en respaldos\historial_respaldos.csv.

  La contraseña no se guarda en el script. Se toma de PGPASSWORD, del archivo pgpass.conf
  (%APPDATA%\postgresql\pgpass.conf) o se pide una vez al ejecutarlo.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\respaldo.ps1
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\respaldo.ps1 -Imagenes -IncluirRoles -CopiaExterna "E:\Respaldos DATEC"
#>
param(
  [string]$Servidor = 'localhost',
  [int]$Puerto = 5432,
  [string]$Usuario = 'postgres',
  [string]$Base = 'datec_catalogo',
  [string]$Carpeta = '',
  [string]$CopiaExterna = '',
  [string]$Responsable = $env:USERNAME,
  [switch]$Imagenes,
  [switch]$IncluirRoles,
  [int]$Diarios = 7,
  [int]$Semanales = 4,
  [int]$Mensuales = 3
)

$ErrorActionPreference = 'Stop'
$proyecto = Split-Path -Parent $PSScriptRoot
if (-not $Carpeta) { $Carpeta = Join-Path $proyecto 'respaldos' }
New-Item -ItemType Directory -Force -Path $Carpeta | Out-Null
$historial = Join-Path $Carpeta 'historial_respaldos.csv'

# --- Herramientas de PostgreSQL: primero el PATH, luego la instalación más reciente ---------
function Find-PgTool([string]$nombre) {
  $cmd = Get-Command "$nombre.exe" -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $candidatos = Get-ChildItem 'C:\Program Files\PostgreSQL\*\bin' -Directory -ErrorAction SilentlyContinue |
    Sort-Object { [int]($_.Parent.Name -replace '\D', '') } -Descending
  foreach ($c in $candidatos) {
    $ruta = Join-Path $c.FullName "$nombre.exe"
    if (Test-Path -LiteralPath $ruta) { return $ruta }
  }
  throw "No se encontró $nombre.exe. Agregue la carpeta bin de PostgreSQL al PATH."
}
$pgDump = Find-PgTool 'pg_dump'
$pgRestore = Find-PgTool 'pg_restore'
$psql = Find-PgTool 'psql'

# --- Contraseña: solo en memoria durante la ejecución ----------------------------------------
$pgpass = Join-Path $env:APPDATA 'postgresql\pgpass.conf'
$claveTemporal = $false
if (-not $env:PGPASSWORD -and -not (Test-Path -LiteralPath $pgpass)) {
  $segura = Read-Host "Contraseña de PostgreSQL para $Usuario" -AsSecureString
  $ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($segura)
  $env:PGPASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr)
  [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr)
  $claveTemporal = $true
}
$conexion = @('-h', $Servidor, '-p', "$Puerto", '-U', $Usuario, '-w')

function Write-Historial([string]$operacion, [string]$archivo, [string]$ubicacion, [string]$resultado, [string]$detalle) {
  $tam = ''
  if ($archivo -and (Test-Path -LiteralPath $archivo)) { $tam = [math]::Round((Get-Item -LiteralPath $archivo).Length / 1KB, 1) }
  $ahora = Get-Date
  [pscustomobject]@{
    Fecha       = $ahora.ToString('yyyy-MM-dd')
    Hora        = $ahora.ToString('HH:mm:ss')
    Operacion   = $operacion
    Archivo     = if ($archivo) { Split-Path -Leaf $archivo } else { '' }
    TamanoKB    = $tam
    Ubicacion   = $ubicacion
    Responsable = $Responsable
    Resultado   = $resultado
    Detalle     = $detalle
  } | Export-Csv -LiteralPath $historial -Append -NoTypeInformation -Encoding UTF8
}

# --- Retención: conserva los N diarios, semanales y mensuales más recientes ------------------
function Invoke-Retencion([string]$dir) {
  $respaldos = Get-ChildItem -LiteralPath $dir -Filter 'respaldo_datec_*.dump' -File |
    Where-Object { $_.BaseName -match '^respaldo_datec_(\d{4}-\d{2}-\d{2})' } |
    ForEach-Object {
      $f = [datetime]::ParseExact($Matches[1], 'yyyy-MM-dd', $null)
      $cal = [Globalization.CultureInfo]::InvariantCulture.Calendar
      $semana = $cal.GetWeekOfYear($f, 'FirstFourDayWeek', 'Monday')
      [pscustomobject]@{ Item = $_; Fecha = $f; Semana = '{0}-{1:00}' -f $f.Year, $semana; Mes = $f.ToString('yyyy-MM') }
    } | Sort-Object { $_.Item.Name } -Descending

  $conservar = @{}
  $respaldos | Select-Object -First $Diarios | ForEach-Object { $conservar[$_.Item.FullName] = $true }
  $respaldos | Group-Object Semana | Select-Object -First $Semanales | ForEach-Object { $conservar[$_.Group[0].Item.FullName] = $true }
  $respaldos | Group-Object Mes | Select-Object -First $Mensuales | ForEach-Object { $conservar[$_.Group[0].Item.FullName] = $true }

  foreach ($r in $respaldos) {
    if ($conservar.ContainsKey($r.Item.FullName)) { continue }
    $sufijo = $r.Item.BaseName.Substring('respaldo_datec_'.Length)
    Remove-Item -LiteralPath $r.Item.FullName -Force
    foreach ($extra in @("roles_$sufijo.sql", "imagenes_$sufijo.zip")) {
      $p = Join-Path $dir $extra
      if (Test-Path -LiteralPath $p) { Remove-Item -LiteralPath $p -Force }
    }
    Write-Historial 'Retiro por retención' $null $dir 'Correcto' $r.Item.Name
    Write-Output "Retirado por retención: $($r.Item.Name)"
  }
}

$sufijo = Get-Date -Format 'yyyy-MM-dd'
if (Test-Path -LiteralPath (Join-Path $Carpeta "respaldo_datec_$sufijo.dump")) { $sufijo = Get-Date -Format 'yyyy-MM-dd_HHmmss' }
$archivo = Join-Path $Carpeta "respaldo_datec_$sufijo.dump"
$inicio = Get-Date

try {
  # 1. Respaldo de la base -------------------------------------------------------------------
  & $pgDump @conexion -d $Base -F c -f $archivo
  if ($LASTEXITCODE -ne 0) { throw "pg_dump terminó con código $LASTEXITCODE." }

  # 2. Comprobación: el archivo se puede leer y contiene los datos de las siete tablas -----
  $contenido = & $pgRestore --list $archivo
  if ($LASTEXITCODE -ne 0) { throw 'pg_restore no pudo leer el respaldo.' }
  $tablas = 'marca', 'categoria', 'tipopresentacion', 'producto', 'precioproducto', 'imagenproducto', 'caracteristicaproducto'
  $faltan = $tablas | Where-Object { -not ($contenido -match "TABLE DATA public $_ ") }
  if ($faltan) { throw "El respaldo no contiene los datos de: $($faltan -join ', ')." }
  $duracion = [math]::Round(((Get-Date) - $inicio).TotalSeconds, 1)
  Write-Historial 'Respaldo de la base' $archivo $Carpeta 'Correcto' "Duración $duracion s; $($contenido.Count) líneas en el índice"
  Write-Output "Respaldo correcto: $archivo ($duracion s)"

  # 3a. Roles y usuarios (pertenecen al servidor, no a la base) -----------------------------
  if ($IncluirRoles) {
    $roles = Join-Path $Carpeta "roles_$sufijo.sql"
    & (Find-PgTool 'pg_dumpall') @conexion --globals-only -f $roles
    if ($LASTEXITCODE -ne 0) { throw 'pg_dumpall no pudo copiar los roles (requiere superusuario).' }
    Write-Historial 'Respaldo de roles' $roles $Carpeta 'Correcto' 'Contiene contraseñas cifradas: guárdelo con cuidado'
  }

  # 3b. Imágenes: la base solo guarda los enlaces; aquí se descargan los archivos -----------
  if ($Imagenes) {
    $tmp = Join-Path $Carpeta "imagenes_$sufijo"
    New-Item -ItemType Directory -Force -Path $tmp | Out-Null
    $filas = & $psql @conexion -d $Base -X -A -t -F '|' -c 'SELECT p.codigoproducto, i.idimagen, i.imagen FROM ImagenProducto i JOIN Producto p USING (idproducto) ORDER BY 1, 2'
    if ($LASTEXITCODE -ne 0) { throw 'No se pudo leer la lista de imágenes.' }
    [Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
    $ok = 0; $errores = @()
    foreach ($fila in $filas) {
      if (-not $fila) { continue }
      $codigo, $id, $url = $fila -split '\|', 3
      $nombre = Split-Path -Leaf ([uri]$url).AbsolutePath
      $destino = Join-Path (Join-Path $tmp $codigo) "$id-$nombre"
      New-Item -ItemType Directory -Force -Path (Split-Path $destino) | Out-Null
      try { Invoke-WebRequest -Uri $url -OutFile $destino -UseBasicParsing; $ok++ }
      catch { $errores += "$codigo ($url)" }
    }
    $zip = Join-Path $Carpeta "imagenes_$sufijo.zip"
    Compress-Archive -Path (Join-Path $tmp '*') -DestinationPath $zip -Force
    Remove-Item -LiteralPath $tmp -Recurse -Force
    $estado = if ($errores) { 'Con observaciones' } else { 'Correcto' }
    Write-Historial 'Respaldo de imágenes' $zip $Carpeta $estado "$ok descargadas; fallaron: $($errores -join '; ')"
    Write-Output "Imágenes: $ok descargadas, $($errores.Count) con error."
  }

  # 4. Copia en otra ubicación ----------------------------------------------------------------
  if ($CopiaExterna) {
    New-Item -ItemType Directory -Force -Path $CopiaExterna | Out-Null
    Get-ChildItem -LiteralPath $Carpeta -File | Where-Object { $_.Name -like "*_$sufijo.*" } |
      Copy-Item -Destination $CopiaExterna -Force
    Write-Historial 'Copia externa' $archivo $CopiaExterna 'Correcto' ''
    Invoke-Retencion $CopiaExterna
  }

  # 5. Retención: solo después de comprobar el respaldo nuevo ------------------------------
  Invoke-Retencion $Carpeta
}
catch {
  Write-Historial 'Respaldo de la base' $archivo $Carpeta 'Error' $_.Exception.Message
  if ((Test-Path -LiteralPath $archivo) -and ((Get-Item -LiteralPath $archivo).Length -eq 0)) { Remove-Item -LiteralPath $archivo -Force }
  Write-Error "El respaldo falló: $($_.Exception.Message) No se retiró ningún respaldo anterior."
  exit 1
}
finally {
  if ($claveTemporal) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}

<#
.SYNOPSIS
  Prueba mensual de recuperación: restaura un respaldo en una base vacía independiente.

.DESCRIPTION
  1. Crea la base vacía datec_prueba:  createdb -T template0 -E UTF8 datec_prueba
  2. Restaura el respaldo:              pg_restore -d datec_prueba respaldo_datec_AAAA-MM-DD.dump
  3. Compara la cantidad de filas de las siete tablas, procedimientos y triggers con la base
     original (si el respaldo es de hoy deben coincidir; si es antiguo, se informa la diferencia).
  4. Registra el resultado en respaldos\historial_respaldos.csv y elimina datec_prueba
     (use -Conservar para revisarla después).

  Nunca toca datec_catalogo: solo lee de ella para comparar.

.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\restaurar_prueba.ps1
.EXAMPLE
  powershell -NoProfile -ExecutionPolicy Bypass -File scripts\restaurar_prueba.ps1 -Archivo respaldos\respaldo_datec_2026-11-21.dump -Conservar
#>
param(
  [string]$Archivo = '',
  [string]$Carpeta = '',
  [string]$Servidor = 'localhost',
  [int]$Puerto = 5432,
  [string]$Usuario = 'postgres',
  [string]$BaseOriginal = 'datec_catalogo',
  [string]$BasePrueba = 'datec_prueba',
  [string]$Responsable = $env:USERNAME,
  [switch]$Conservar
)

$ErrorActionPreference = 'Stop'
$proyecto = Split-Path -Parent $PSScriptRoot
$carpeta = if ($Carpeta) { $Carpeta } else { Join-Path $proyecto 'respaldos' }
$historial = Join-Path $carpeta 'historial_respaldos.csv'
if ($BasePrueba -eq $BaseOriginal) { throw 'La base de prueba no puede ser la base original.' }

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
$psql = Find-PgTool 'psql'

if (-not $Archivo) {
  $ultimo = Get-ChildItem -LiteralPath $carpeta -Filter 'respaldo_datec_*.dump' -File -ErrorAction SilentlyContinue |
    Sort-Object Name -Descending | Select-Object -First 1
  if (-not $ultimo) { throw "No hay respaldos en $carpeta. Ejecute primero scripts\respaldo.ps1." }
  $Archivo = $ultimo.FullName
}
if (-not (Test-Path -LiteralPath $Archivo)) { throw "No existe el archivo $Archivo." }

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

function Get-Resumen([string]$base) {
  $sql = @"
SELECT 'Marca', count(*) FROM Marca UNION ALL SELECT 'Categoria', count(*) FROM Categoria
UNION ALL SELECT 'TipoPresentacion', count(*) FROM TipoPresentacion UNION ALL SELECT 'Producto', count(*) FROM Producto
UNION ALL SELECT 'PrecioProducto', count(*) FROM PrecioProducto UNION ALL SELECT 'ImagenProducto', count(*) FROM ImagenProducto
UNION ALL SELECT 'CaracteristicaProducto', count(*) FROM CaracteristicaProducto UNION ALL SELECT 'Bitacora', count(*) FROM Bitacora
UNION ALL SELECT 'Procedimientos', count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace WHERE n.nspname = 'public' AND p.prokind = 'p'
UNION ALL SELECT 'Triggers', count(*) FROM pg_trigger t JOIN pg_class c ON c.oid = t.tgrelid JOIN pg_namespace n ON n.oid = c.relnamespace WHERE n.nspname = 'public' AND NOT t.tgisinternal
"@
  $filas = & $psql @conexion -d $base -X -A -t -F '|' -c $sql
  if ($LASTEXITCODE -ne 0) { throw "No se pudo leer la base $base." }
  $r = [ordered]@{}
  foreach ($f in $filas) { if ($f) { $k, $v = $f -split '\|'; $r[$k] = [int]$v } }
  return $r
}

function Write-Historial([string]$resultado, [string]$detalle) {
  New-Item -ItemType Directory -Force -Path $carpeta | Out-Null
  $ahora = Get-Date
  [pscustomobject]@{
    Fecha = $ahora.ToString('yyyy-MM-dd'); Hora = $ahora.ToString('HH:mm:ss')
    Operacion = 'Prueba de recuperación'; Archivo = Split-Path -Leaf $Archivo
    TamanoKB = [math]::Round((Get-Item -LiteralPath $Archivo).Length / 1KB, 1)
    Ubicacion = "$Servidor`:$Puerto/$BasePrueba"; Responsable = $Responsable
    Resultado = $resultado; Detalle = $detalle
  } | Export-Csv -LiteralPath $historial -Append -NoTypeInformation -Encoding UTF8
}

$creada = $false
try {
  $existe = & $psql @conexion -d postgres -X -A -t -c "SELECT 1 FROM pg_database WHERE datname = '$BasePrueba'"
  if ($existe -eq '1') { throw "La base $BasePrueba ya existe. Elimínela o use -BasePrueba con otro nombre." }

  $inicio = Get-Date
  & (Find-PgTool 'createdb') @conexion -T template0 -E UTF8 $BasePrueba
  if ($LASTEXITCODE -ne 0) { throw 'createdb no pudo crear la base de prueba.' }
  $creada = $true

  & (Find-PgTool 'pg_restore') @conexion -d $BasePrueba --exit-on-error $Archivo
  if ($LASTEXITCODE -ne 0) { throw "pg_restore terminó con código $LASTEXITCODE." }
  $duracion = [math]::Round(((Get-Date) - $inicio).TotalSeconds, 1)

  $prueba = Get-Resumen $BasePrueba
  $original = Get-Resumen $BaseOriginal
  $tabla = foreach ($k in $prueba.Keys) {
    [pscustomobject]@{ Elemento = $k; Original = $original[$k]; Restaurada = $prueba[$k]; Igual = ($original[$k] -eq $prueba[$k]) }
  }
  $tabla | Format-Table -AutoSize | Out-String | Write-Output

  if ($prueba['Producto'] -eq 0) { throw 'La base restaurada no tiene productos.' }
  $distintos = @($tabla | Where-Object { -not $_.Igual } | ForEach-Object { $_.Elemento })
  $resumen = ($tabla | ForEach-Object { "$($_.Elemento)=$($_.Restaurada)" }) -join ', '
  if ($distintos.Count -eq 0) {
    Write-Historial 'Correcto' "Restaurada en $duracion s. Coincide con la base original. $resumen"
    Write-Output "Prueba de recuperación correcta ($duracion s): la copia coincide con $BaseOriginal."
  } else {
    Write-Historial 'Correcto con diferencias' "Restaurada en $duracion s. Difieren: $($distintos -join ', ') (cambios posteriores al respaldo). $resumen"
    Write-Output "Restauración correcta ($duracion s). Difieren de la base actual: $($distintos -join ', '), por cambios hechos después del respaldo."
  }
}
catch {
  Write-Historial 'Error' $_.Exception.Message
  Write-Error "La prueba de recuperación falló: $($_.Exception.Message)"
  $codigo = 1
}
finally {
  if ($creada -and -not $Conservar) {
    & (Find-PgTool 'dropdb') @conexion $BasePrueba
    Write-Output "Base de prueba $BasePrueba eliminada."
  }
  if ($claveTemporal) { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
}
if ($codigo) { exit $codigo }

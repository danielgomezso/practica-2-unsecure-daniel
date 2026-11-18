param([string]$BaseUrl = 'http://localhost:8080')
$ErrorActionPreference = 'Stop'
$BaseUrl = $BaseUrl.TrimEnd('/')
$sesion = Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/auth/login" -ContentType 'application/json' -Body '{"username":"carlos_mora","password":"carlos123"}'
$headers = @{ Authorization = "Bearer $($sesion.token)" }
$bloqueada = Invoke-WebRequest -SkipHttpErrorCheck -Uri "$BaseUrl/api/cuentas/3/saldo" -Headers $headers
$contenidoBloqueado = if ($bloqueada.Content -is [byte[]]) {
    [System.Text.Encoding]::UTF8.GetString($bloqueada.Content)
} else { $bloqueada.Content }
$problema = $contenidoBloqueado | ConvertFrom-Json
if ($bloqueada.StatusCode -ne 403 -or $problema.status -ne 403 -or
    $problema.title -ne 'Acceso no autorizado a recurso ajeno.' -or $null -ne $problema.saldo -or
    $bloqueada.Headers['Content-Type'] -notmatch 'application/problem\+json') {
    throw 'Fallo: acceso ajeno no bloqueado mediante ProblemDetail.'
}
Write-Output "HTTP $($bloqueada.StatusCode) $contenidoBloqueado"
# En data.sql Carlos es usuario 3 y su cuenta es 2; la cuenta 1 pertenece al administrador.
$propia = Invoke-WebRequest -Uri "$BaseUrl/api/cuentas/2/saldo" -Headers $headers
$saldo = $propia.Content | ConvertFrom-Json
if ($propia.StatusCode -ne 200 -or $saldo.cuentaId -ne 2 -or $saldo.saldo -ne 250000) {
    throw 'Fallo: no se obtuvo el saldo inicial de la cuenta propia.'
}
Write-Output "HTTP $($propia.StatusCode) $($propia.Content)"
Write-Output 'OK: IDOR bloqueado y consulta propia autorizada.'

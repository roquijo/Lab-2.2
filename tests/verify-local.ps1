# Verifica el camino feliz local: pedido, traceId, Prometheus, Tempo y Loki.
# El stack debe estar levantado. No sustituye las pruebas de Maven.

$ErrorActionPreference = "Stop"
$failures = @()

function Assert-True($condition, $message) {
    if (-not $condition) {
        $script:failures += $message
        Write-Host "FALLO: $message"
    } else {
        Write-Host "OK: $message"
    }
}

$health = Invoke-WebRequest -Uri "http://localhost:13133/" -UseBasicParsing -TimeoutSec 5
Assert-True ($health.StatusCode -eq 200) "el Collector responde en :13133"

$order = Invoke-RestMethod -Uri "http://localhost:8080/api/orders/ord-001" -TimeoutSec 10
Assert-True ($order.orderId -eq "ord-001") "service-a devuelve ord-001"
Assert-True ($null -ne $order.inventory.productSku) "la respuesta incluye inventario de service-b"
Assert-True ($order.traceId -match "^[0-9a-f]{32}$") "la respuesta trae un traceId hexadecimal"

$prom = Invoke-RestMethod -Uri "http://localhost:9090/api/v1/query?query=http_server_request_duration_seconds_count" -TimeoutSec 10
Assert-True ($prom.status -eq "success" -and $prom.data.result.Count -gt 0) "Prometheus tiene http_server_request_duration_seconds_count"

Start-Sleep -Seconds 5
$tempoOk = $false
for ($i = 0; $i -lt 6; $i++) {
    try {
        $tempo = Invoke-RestMethod -Uri "http://localhost:3200/api/traces/$($order.traceId)" -TimeoutSec 10
        $services = @()
        foreach ($batch in $tempo.batches) {
            foreach ($attr in $batch.resource.attributes) {
                if ($attr.key -eq "service.name") { $services += $attr.value.stringValue }
            }
        }
        if ($services -contains "service-a" -and $services -contains "service-b") {
            $tempoOk = $true
            break
        }
    } catch {
    }
    Start-Sleep -Seconds 3
}
Assert-True $tempoOk "Tempo muestra service-a y service-b en la misma traza"

$start = ([DateTimeOffset]::UtcNow.AddMinutes(-15).ToUnixTimeMilliseconds()) * 1000000
$end = ([DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) * 1000000
$encoded = [uri]::EscapeDataString("{service_name=`"service-a`"} | trace_id=`"$($order.traceId)`"")
$loki = Invoke-RestMethod -Uri "http://localhost:3100/loki/api/v1/query_range?query=$encoded&start=$start&end=$end&limit=5" -TimeoutSec 10
Assert-True ($loki.status -eq "success" -and $loki.data.result.Count -gt 0) "Loki encuentra logs de service-a con ese trace_id"

$logs = docker logs lab22-service-a --tail 200
Assert-True ($logs -match $order.traceId) "stdout JSON de service-a contiene el traceId"

if ($failures.Count -gt 0) {
    Write-Host "Verificación incompleta: $($failures.Count) fallo(s)"
    exit 1
}
Write-Host "Verificación local completada"

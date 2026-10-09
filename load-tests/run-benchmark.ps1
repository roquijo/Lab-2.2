# Ejecuta el mismo escenario k6 sin agente y con agente.
# Requiere el stack local ya construido y Docker en ejecución.
# Uso, desde la raíz de Lab 2.2:
#   powershell -File load-tests/run-benchmark.ps1

$ErrorActionPreference = "Stop"
$root = Resolve-Path (Join-Path $PSScriptRoot "..")
$compose = Join-Path $root "deploy/local/docker-compose.yaml"
$results = Join-Path $PSScriptRoot "results"
New-Item -ItemType Directory -Force -Path $results | Out-Null

function Wait-ServiceA {
    for ($i = 0; $i -lt 40; $i++) {
        try {
            $response = Invoke-WebRequest -Uri "http://localhost:8080/actuator/health/liveness" -UseBasicParsing -TimeoutSec 3
            if ($response.StatusCode -eq 200) { return }
        } catch {
        }
        Start-Sleep -Seconds 3
    }
    throw "service-a no quedó listo"
}

function Sample-Stats([string]$path, [int]$seconds) {
    $deadline = (Get-Date).AddSeconds($seconds)
    while ((Get-Date) -lt $deadline) {
        docker stats --no-stream --format "{{json .}}" |
            Select-String -Pattern "lab22-service-a|lab22-service-b|lab22-collector" |
            ForEach-Object { $_.Line } |
            Add-Content -Path $path
        Start-Sleep -Seconds 2
    }
}

function Invoke-Scenario([string]$enabled, [string]$summaryName, [string]$statsName) {
    $env:OTEL_ENABLED = $enabled
    docker compose -f $compose up -d --force-recreate --no-deps service-a service-b
    Wait-ServiceA
    $statsPath = Join-Path $results $statsName
    if (Test-Path $statsPath) { Remove-Item $statsPath }
    $durationSec = 40
    $sampler = Start-Job -ScriptBlock {
        param($path, $seconds)
        $deadline = (Get-Date).AddSeconds($seconds)
        while ((Get-Date) -lt $deadline) {
            docker stats --no-stream --format "{{json .}}" |
                Select-String -Pattern "lab22-service-a|lab22-service-b|lab22-collector" |
                ForEach-Object { $_.Line } |
                Add-Content -Path $path
            Start-Sleep -Seconds 2
        }
    } -ArgumentList $statsPath, $durationSec

    docker run --rm `
        -v "${PSScriptRoot}:/scripts" `
        -e BASE_URL=http://host.docker.internal:8080 `
        -e INSTRUMENTED=$(if ($enabled -eq "true") { "true" } else { "false" }) `
        -e VUS=5 `
        -e DURATION=30s `
        grafana/k6:0.57.0 run /scripts/k6-orders.js --summary-export "/scripts/results/$summaryName"
    Wait-Job $sampler | Out-Null
    Receive-Job $sampler | Out-Null
    Remove-Job $sampler
}

Invoke-Scenario "false" "baseline-summary.json" "baseline-stats.jsonl"
Invoke-Scenario "true" "instrumented-summary.json" "instrumented-stats.jsonl"
python load-tests/analyze_overhead.py

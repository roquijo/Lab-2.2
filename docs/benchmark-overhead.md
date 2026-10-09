# Benchmark y overhead

## Qué se compara

La misma imagen, los mismos pedidos `ord-001` a `ord-005`, el mismo MySQL y el mismo Compose. Cambia una variable: `OTEL_ENABLED`.

| Escenario | Proceso Java | Collector |
|---|---|---|
| Baseline | `java -jar` sin `-javaagent` | Sigue encendido, pero no recibe telemetría de las apps |
| Instrumentado | `java -javaagent:...` y exporta OTLP | Recibe trazas, métricas y logs |

Apagar solo el Collector, como hace el README del ejemplo, deja el SDK creando spans. Eso no mide el costo de instrumentar. Aquí el agente no se carga.

No hay `time.sleep` en el código de negocio. La latencia es la de HTTP, JDBC y, en el escenario instrumentado, el trabajo del agente.

## Carga

`load-tests/k6-orders.js` usa 5 usuarios virtuales durante 30 segundos, con una pausa de 100 ms. Es una corrida corta para repetirla en un portátil. Para una medición más estable:

```powershell
$env:VUS = "20"
$env:DURATION = "3m"
```

El script de esta carpeta fija 5 y 30 s a propósito, para que las dos pasadas quepan en una sesión. Si cambias la carga, cámbiala en los dos escenarios.

k6 corre en Docker (`grafana/k6:0.57.0`) y llama a `http://host.docker.internal:8080`. No hace falta instalar k6 en Windows.

## Cómo medirlo

Con el stack ya construido:

```powershell
powershell -File load-tests/run-benchmark.ps1
```

El script recrea service-a y service-b, espera el liveness, lanza k6 y muestrea `docker stats` de `lab22-service-a`, `lab22-service-b` y `lab22-collector`. Después ejecuta `load-tests/analyze_overhead.py`.

## Fórmulas

k6 entrega la latencia en milisegundos (`http_req_duration`: `med` es p50, más `p(95)` y `p(99)`), la tasa `http_reqs` y la tasa `http_req_failed`.

- Overhead absoluto = valor instrumentado − valor baseline.
- Overhead porcentual = (instrumentado − baseline) / baseline × 100.
- CPU y memoria son el promedio de las muestras de `docker stats` durante la corrida. La memoria se normaliza a MiB.
- El Collector se informa aparte. Su CPU no se suma a la de los servicios, porque en el baseline está ocioso y mezclarlo atribuiría al agente un proceso que no está dentro del JVM.

Un porcentaje de CPU es inestable si el baseline está cerca de cero. Por eso el informe conserva también el valor absoluto.

## Resultados

Corrida única del 2026-10-09 en este portátil, con Docker Desktop 28.5.1. `run-benchmark.ps1` vuelve a escribir los JSON y `overhead-report.md` en `load-tests/results/`. Esa carpeta no se versiona: la tabla de este documento es el registro que queda en el repositorio.

Condiciones iguales en los dos escenarios: imagen ya construida, MySQL con la misma semilla, 5 usuarios virtuales, 30 segundos, pausa de 100 ms, pedidos `ord-001` a `ord-005`, k6 0.57.0 contra `http://host.docker.internal:8080`. El baseline recreó los dos servicios con `OTEL_ENABLED=false`. El instrumentado los recreó con `OTEL_ENABLED=true` y el agente 2.31.1. El Collector, Prometheus, Tempo, Loki y Grafana siguieron encendidos en ambos.

| Métrica | Baseline | Instrumentado | Overhead absoluto | Overhead % |
|---|---:|---:|---:|---:|
| Latencia p50 (ms) | 8,6306 | 8,6653 | 0,0347 | 0,40 % |
| Latencia p95 (ms) | 14,7352 | 16,4087 | 1,6735 | 11,36 % |
| Latencia p99 (ms) | 21,6424 | 23,8405 | 2,1981 | 10,16 % |
| Throughput (req/s) | 44,2853 | 43,2927 | −0,9926 | −2,24 % |
| Solicitudes | 1331 | 1302 | −29 | −2,18 % |
| Tasa de errores | 0 | 0 | 0 | no aplica, baseline 0 |

k6 informa `http_req_failed` como `value` (0 significa 0 %). El script acepta ese campo cuando el export no trae `rate`.

| Contenedor | CPU baseline | CPU OTel | CPU absoluto | CPU % | Mem baseline | Mem OTel | Mem absoluto | Mem % |
|---|---:|---:|---:|---:|---:|---:|---:|---:|
| lab22-service-a | 23,98 % | 34,28 % | +10,30 pp | +42,97 % | 293,04 MiB | 403,50 MiB | +110,46 MiB | +37,69 % |
| lab22-service-b | 18,53 % | 33,49 % | +14,96 pp | +80,69 % | 266,23 MiB | 431,27 MiB | +165,03 MiB | +61,99 % |
| lab22-collector | 1,58 % | 0,91 % | −0,67 pp | −42,41 % | 57,25 MiB | 70,61 MiB | +13,36 MiB | +23,33 % |

Los checks de k6 pasaron al 100 % en las dos pasadas (5320/5320 y 5204/5204). En el baseline el check de `traceId` devuelve verdadero sin mirar el cuerpo, porque no hay agente. En el instrumentado exige un `traceId` de 32 hexadecimales distinto de cero, y también pasó.

### Cómo leer estas cifras

El p50 casi no se mueve (0,03 ms). Esa diferencia cabe en el ruido de una sola pasada de 30 segundos. El p95 y el p99 suben cerca de 1,7 ms y 2,2 ms, alrededor del 10 % al 11 %. El caudal baja un 2 %. No hubo errores HTTP en ninguno de los dos escenarios.

La memoria de los dos JVM sube de forma clara: el agente, sus colas y los buffers de exportación viven dentro del proceso. service-b crece más (+166 MiB, +62 %) que service-a (+110 MiB, +38 %). La CPU media de la ventana de 40 segundos sube 10 y 15 puntos porcentuales. `docker stats` puede pasar de 100 % si el contenedor usa más de un núcleo; el promedio de esta corrida no llegó a ese techo, aunque hubo muestras sueltas por encima durante el arranque del escenario instrumentado.

La CPU del Collector bajó en la pasada instrumentada. No se interpreta como un ahorro: el porcentaje de partida es bajo, la ventana es corta y el Collector del baseline todavía podía estar cerrando lotes de la sesión anterior. Lo que sí sube, y encaja con recibir telemetría, es su memoria: de 57 MiB a 71 MiB.

La ventana de `docker stats` dura 40 segundos y la de k6 30. Las primeras muestras incluyen el proceso recién recreado, antes de que la carga esté estable. Las dos pasadas usan la misma ventana, así que la comparación sigue siendo el mismo procedimiento, pero no es un perfil de estado estacionario de varios minutos.

No se repitió el experimento. Una segunda corrida puede mover el p95. Por eso el informe conserva los JSON y no redondea los porcentajes a una conclusión de capacidad de producción.

## Cómo bajar el costo sin apagar la visibilidad

- Subir `OTEL_METRIC_EXPORT_INTERVAL` por encima de 15 s si no necesitas métricas tan frescas.
- Bajar `OTEL_TRACES_SAMPLER_ARG` por debajo de 1 fuera del laboratorio. `parentbased_traceidratio` mantiene completa la traza de las peticiones que sí se muestrean, así que la correlación sigue siendo válida en esas peticiones.
- Dejar `debug` en `basic`. `detailed` escribe cada span y compite con la aplicación.
- No añadir spans manuales alrededor del SQL ni un segundo exporter Prometheus dentro del proceso.
- El `batch` del Collector y el `BatchSpanProcessor` del agente ya agrupan envíos. `SimpleSpanProcessor` sería un salto de overhead y no se usa.

# Análisis del repositorio del docente (`oTelLabs-main`)

Este documento revisa el laboratorio de referencia antes de construir Lab 2.2. El repositorio original no se modificó. Las citas apuntan a archivos de `oTelLabs-main` y describen lo que el código hace hoy, no lo que el README promete.

## Qué problema intenta resolver

El docente plantea dos servicios HTTP, una base relacional y un pipeline OpenTelemetry que debería permitir ver la misma operación en métricas, logs y trazas. El caso de negocio es consultar un pedido (`GET /order/{id}`), leer PostgreSQL y pedir el inventario a un segundo servicio.

Esa intención se conserva. La implementación de Lab 2.2 cambia el lenguaje, la base de datos y varios mecanismos que en el ejemplo están incompletos o se contradicen.

## Estructura real

| Ruta | Qué contiene | Estado observado |
|---|---|---|
| `service-a/main.py` | FastAPI, SDK de OpenTelemetry, consulta de pedidos y llamada HTTP | Código de aplicación y telemetría mezclados en un solo archivo |
| `service-b/main.py` | Inventario, caché en memoria y una espera aleatoria | La espera altera la latencia que luego se atribuye a la instrumentación |
| `otel-collector/collector-config.yaml` | Receivers, processors y exporters | Más completo que los servicios, pero con exporters de nube activos en local |
| `docker-compose.yaml` | Postgres, Collector, Jaeger, Tempo, Prometheus y Grafana | Varios destinos de scrape no existen |
| `grafana/` | Datasources y un dashboard | La correlación de logs apunta a Loki, que no está desplegado |
| `k8s/gcp/deployment.yaml` | Manifiestos GKE | Faltan ServiceAccount, ConfigMap del Collector y Postgres dentro del propio archivo |
| `k8s/aws/ecs-task-definition.json` | Una task definition de Fargate | Placeholders y un volumen EFS `fs-XXXX` |
| `benchmark/` | k6 y un script de comparación | El baseline no apaga el SDK |

No hay pruebas automatizadas de los servicios. `requirements.txt` acota FastAPI y el SDK, pero el agente de Java no aplica porque el ejemplo es Python.

## Instrumentación que sí está escrita

`service-a/main.py` crea un `Resource`, un `TracerProvider` con `BatchSpanProcessor` y un `MeterProvider` con exportación OTLP cada 15 segundos. También registra `FastAPIInstrumentor`, `HTTPXClientInstrumentor` y `Psycopg2Instrumentor`, y abre spans manuales `fetch.order.db` y `call.service-b.inventory`. El formatter de logs copia `trace_id` y `span_id` del contexto activo.

Esos son los conceptos que Lab 2.2 conserva: identidad del servicio en el resource, spans de negocio, propagación W3C y logs con el identificador de la traza.

## Diferencia entre el README y el código

El README describe un `PrometheusMetricReader` y un endpoint `/metrics`. En `service-a/main.py` el import existe, pero el reader no entra en `metric_readers`. El arranque del servidor Prometheus está comentado:

```132:134:service-a/main.py
async def lifespan(app: FastAPI):
    # No se necesita iniciar porque ya PrometheusmETRIC EN LINA 56 ya lo hizo en el serv
    # start_http_server(PROMETHEUS_PORT)
```

La línea 56 no inicia ese servidor: solo construye el exporter OTLP. Consecuencia: Prometheus no puede scrapear las aplicaciones. `prometheus/prometheus.yaml` además apunta a `service-a:9090` y `service-b:9091`, mientras `docker-compose.yaml` publica `9064` y `9065`. También declara un job `postgres-exporter:9187` y ese contenedor no existe.

El contador `http_requests_total` se incrementa al entrar al handler y otra vez al salir cuando la llamada tiene éxito. En un pedido correcto la serie sin etiqueta de estado y la serie `2xx` conviven, así que una disponibilidad calculada como `2xx / total` queda cerca del 50 % aunque no haya errores. Si el pedido no existe, la segunda suma no ocurre y el histograma de latencia tampoco se registra.

`service-b/main.py` duerme entre 10 ms y 150 ms antes de la consulta. Esa espera no es trabajo de base de datos ni de OpenTelemetry, pero entra en cualquier benchmark.

## Collector

El archivo `otel-collector/collector-config.yaml` usa la imagen `0.103.0`. El orden real de processors en el pipeline de trazas es `memory_limiter`, `resource`, `resourcedetection`, `filter/health`, `batch`. Ese orden es el recomendable. El comentario del mismo archivo dice lo contrario (`batch` primero). Hay que leer el bloque `service.pipelines`, no el comentario.

Otros problemas:

- El exporter `logging` quedó obsoleto. En Collector 0.162 el reemplazo es `debug`.
- `service.telemetry.metrics.address` se eliminó en la línea 0.16x. La configuración vigente usa `readers`.
- El exporter `googlecloud` está activo en el pipeline de logs y el compose monta una ruta de credenciales de otra máquina (`C:\Users\Equipo\...`) junto con el proyecto `cicdtraining-498421`. En otro equipo el Collector no arranca o intenta escribir en un proyecto ajeno.
- Jaeger y Tempo reciben las mismas trazas. Grafana, en cambio, configura `tracesToLogsV2` contra un datasource Loki que nadie levanta. La correlación cruzada queda descrita y no implementada.
- El healthcheck del Collector ejecuta `otelcol-contrib --version`. Eso comprueba que el binario existe, no que el pipeline acepte OTLP.

## Backends y dashboard

El dashboard consulta series `otelcol_http_requests_total`. Ese nombre solo aparecería si el exporter Prometheus usara el namespace `otelcol` y la aplicación emitiera `http_requests_total`. Como el endpoint de las apps no existe y el namespace prefija todo lo que sí pasa por el Collector, el panel no tiene una fuente coherente con el código actual.

`K6/resultados_k6.json` es una corrida real del 21 de agosto de 2026, modo `errores`, con 47,5 % de error. No es una comparación baseline contra instrumentado. La tabla de overhead del README está marcada como referencia, pero sus cifras no salen de ese JSON.

## Despliegue

En GKE el manifiesto referencia la cuenta `otel-collector-sa`, el ConfigMap `collector-config`, el secreto `gcp-credentials` y `postgres-svc`, y no los crea. La contraseña de Postgres viaja en el ConfigMap. Jaeger se expone con `LoadBalancer`, pero el Collector exporta por OTLP al puerto 4317 y el Service de Jaeger publica 14250.

En ECS la task definition deja `ACCOUNT_ID`, `fs-XXXX` y `SERVICE_B_DNS`. El volumen de configuración depende de EFS. No hay VPC, roles ni servicio: solo el JSON de la tarea.

## Qué se conserva y qué se rehace

| Concepto del ejemplo | Decisión en Lab 2.2 |
|---|---|
| Dos servicios y una operación que cruza HTTP y base de datos | Se conserva, en Java, con el pedido `ord-001` y el SKU como dato de negocio |
| Resource con servicio, versión y entorno | Variables `OTEL_SERVICE_NAME` y `OTEL_RESOURCE_ATTRIBUTES` del Java Agent |
| Span manual de negocio | `order.compose` e `inventory.lookup`, sin repetir el span SQL |
| Propagación del contexto | La hace el agente sobre el cliente HTTP. No se inyecta el header a mano |
| Collector con `memory_limiter` antes de `batch` | Se conserva y se separa por entorno |
| Prometheus y Grafana | Se conservan. Las métricas salen del exporter Prometheus del Collector |
| Jaeger y Tempo a la vez | Solo Tempo, porque Grafana correlaciona contra él. Jaeger duplicaba almacenamiento |
| Logs solo en stdout | Stdout JSON y, además, logs OTLP hacia Loki |
| PostgreSQL | MySQL 8.4, que es la base pedida para este laboratorio |
| Benchmark apagando solo el Collector | El mismo jar arranca con `OTEL_ENABLED=false` o con el agente |

El ejemplo es útil para ver el vocabulario del pipeline. No es una base copiable: varias rutas de telemetría están documentadas y no conectadas.

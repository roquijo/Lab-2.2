# Instrumentación OpenTelemetry

## Tres señales, tres preguntas

| Señal | Pregunta que responde | Dónde mirarla en este lab |
|---|---|---|
| Métrica | ¿Cuántas veces y con qué latencia ocurre? | Prometheus y el dashboard de Grafana |
| Traza | ¿Qué hizo esta petición concreta y en qué servicio se detuvo? | Tempo, buscando el `traceId` de la respuesta |
| Log | ¿Qué decidió el código en ese momento? | Loki o `docker logs`, filtrando por el mismo `traceId` |

Una métrica no identifica un pedido. Un log sin `trace_id` no se puede colgar de la traza. Una traza sin métricas no dice si el problema es general o aislado.

## SDK, API y agente

La aplicación depende solo de `opentelemetry-api` (`TelemetryConfig`). El SDK —providers, exporters, procesadores— lo instala el Java Agent 2.31.1 cuando el proceso arranca con `-javaagent`.

Se eligió 2.31.1 y no 2.32.0 porque 2.32.0 es el candidato de la versión 3.0 y todavía puede cambiar convenciones. El agente 2.31.1 corresponde al SDK 1.65.0, que es la versión del API en el POM.

No se empaqueta `opentelemetry-sdk` en el jar. Si el SDK de la aplicación y el del agente crean dos `TracerProvider`, aparecen spans duplicados.

Sin el agente (`OTEL_ENABLED=false`) el API queda en modo no-op: los métodos de negocio se ejecutan igual y no hay telemetría. Ese es el baseline del benchmark.

## Auto-instrumentación

El agente, sin anotaciones en el código, cubre:

- HTTP de entrada en Tomcat y Spring MVC. El span se llama como la ruta, por ejemplo `GET /api/orders/{orderId}`.
- HTTP de salida de `RestClient` a través de `HttpURLConnection`. Ahí se escribe `traceparent`.
- JDBC de MySQL. Cada `SELECT` o `UPDATE` de `JdbcTemplate` genera un span cliente con la sentencia saneada.
- Logback. Con el contexto activo, el agente también puede rellenar el MDC. `TraceMdc` lo hace de nuevo solo si la clave todavía no existe, para no pisar al agente.

`OTEL_JAVAAGENT_EXCLUDE_URLS=/actuator/.*` deja fuera los health checks. Si entraran, el SLI de disponibilidad bajaría por tráfico que no es de usuarios.

## Spans manuales

`OrderFulfillmentService.compose` abre `order.compose` como span `INTERNAL`. Atributos de negocio: `order.id`, `product.sku`, `inventory.available`, `order.stock_sufficient`. No copia `db.system` ni la sentencia SQL.

`InventoryService` abre `inventory.lookup` o `inventory.reserve`.

Como el método corre dentro del hilo de la petición, el span del agente es el padre. La prueba `OrderFulfillmentServiceTest.componePedidoYCuelgaElSpanDeNegocioDelSpanPadre` fija un padre de prueba y comprueba que el hijo conserva `traceId` y `parentSpanId`.

Un 404 de pedido no marca el span de negocio como `ERROR`: el servicio respondió de forma prevista. Un fallo de service-b sí llama a `recordException` y `StatusCode.ERROR`.

## Métricas

El agente emite `http.server.request.duration` (histograma en segundos). El Collector lo traduce a `http_server_request_duration_seconds` con `_bucket`, `_sum` y `_count`. De ahí salen tasa, latencia y errores. No se creó otro contador HTTP en la aplicación: habría dos series para la misma pregunta.

Sí hay contadores de negocio, de cardinalidad baja:

- `orders.composed` con el atributo `result` (`success`, `not_found`, `dependency_error`, `error`).
- `inventory.lookups` y `inventory.reservations`.

El atributo no es el id del pedido. Un id distinto por request haría crecer la serie hasta volverla inútil en Prometheus.

JVM: `jvm.memory.used` y `jvm.cpu.recent_utilization`. El intervalo de exportación es `OTEL_METRIC_EXPORT_INTERVAL=15000`.

## Logs

`logback-spring.xml` usa `LoggingEventCompositeJsonEncoder`. Cada línea tiene `timestamp`, `level`, `logger`, `message`, el MDC y los campos fijos `service`, `version` y `environment`.

Ejemplo de forma, con un `trace_id` real solo cuando la petición pasó por un span válido:

```json
{"timestamp":"2026-10-09T15:05:13.942-05:00","level":"INFO","logger":"edu.maestria.observabilidad.orders.service.OrderFulfillmentService","message":"Pedido compuesto orderId=ord-001 sku=LAPTOP-X1 sufficient=true","trace_id":"<32 hex>","span_id":"<16 hex>","service":"service-a","version":"1.0.0","environment":"local"}
```

Las pruebas de Maven no adjuntan el agente, así que esas líneas de test no traen `trace_id` salvo que el test abra un SDK y `TraceMdc` esté activo. La comprobación de un identificador real de punta a punta es `tests/verify-local.ps1`, contra el stack Docker.

## Muestreo y nivel

| Variable | Valor local | Para qué |
|---|---|---|
| `OTEL_TRACES_SAMPLER` | `parentbased_traceidratio` | Respeta la decisión del padre y, si no hay padre, aplica el ratio |
| `OTEL_TRACES_SAMPLER_ARG` | `1.0` | Conserva todas las trazas en el laboratorio |
| `OTEL_PROPAGATORS` | `tracecontext,baggage` | W3C, el mismo mecanismo que el ejemplo Python llamaba `traceparent` |
| `OTEL_LOGS_EXPORTER` | `otlp` | Además de stdout |
| `OTEL_METRICS_EXPORTER` | `otlp` | Hacia el Collector, no hacia un `/metrics` propio |

`parentbased_always_on` habría sido equivalente con ratio 1. Se dejó el sampler de ratio para que cambiar el volumen sea una variable de entorno y no un cambio de código.

# Correlación entre métricas, logs y trazas

La correlación no consiste en imprimir un UUID en un mensaje. Hace falta el mismo identificador de 32 hexadecimales en tres sitios, y un enlace de Grafana que sepa leerlo.

## Cómo nace el identificador

El agente crea el `trace_id` en service-a al recibir `GET /api/orders/{orderId}`. El span `order.compose` hereda ese contexto porque se abre con `makeCurrent()` en el mismo hilo. `RestClient` no añade la cabecera a mano: el agente intercepta `HttpURLConnection` y escribe `traceparent: 00-<trace_id>-<span_id>-01`. service-b extrae esa cabecera y sus spans quedan en la misma traza.

La respuesta JSON incluye `traceId` para poder copiarlo sin abrir los logs.

## Logs

Dos caminos, el mismo id:

1. **Stdout.** `TraceMdc` y el agente ponen `trace_id` y `span_id` en el MDC. El encoder JSON los escribe como campos. `docker logs lab22-service-a` debe contener el valor devuelto por la API.
2. **Loki.** El agente exporta el log por OTLP. `transform/logs` deja el id en un atributo (Loki lo guarda como structured metadata, consultable con `| trace_id="..."`) y también en el cuerpo con el prefijo `trace_id=`.

El datasource Loki de Grafana tiene un derived field:

```yaml
matcherRegex: "trace_id=([0-9a-f]{32})"
datasourceUid: tempo
url: "$${__value.raw}"
```

En un archivo de aprovisionamiento, Grafana sustituye `${...}` por variables de entorno antes de guardar el datasource. Un solo `$` deja la URL vacía. `$$` se convierte en el `$` que la interfaz necesita en el momento de la consulta. El mismo escape está en `tracesToLogsV2` y en la consulta de métricas del span.

Ese enlace abre la traza. Hay un segundo patrón por si el cuerpo conserva el JSON `"trace_id":"..."`. La API de Grafana, después de reiniciar con este archivo, debe devolver `url` igual a `${__value.raw}` y la consulta de Tempo con `${__trace.traceId}` todavía presente.

## De la traza a los logs

El datasource Tempo usa `tracesToLogsV2` con esta consulta:

```logql
{service_name=~"service-a|service-b"} | trace_id=`<trace id de la traza>`
```

`filterByTraceID` está en false porque la consulta ya filtra. La ventana es dos minutos alrededor del span. El nombre de servicio en Loki sale del resource `service.name` que el receptor OTLP de Loki promueve a etiqueta `service_name`.

## De la métrica a la traza

El exporter Prometheus tiene OpenMetrics activado, así que los histogramas pueden llevar exemplars con `trace_id`. El datasource Prometheus declara:

```yaml
exemplarTraceIdDestinations:
  - datasourceUid: tempo
    name: trace_id
```

Eso es el salto desde un punto del gráfico. Si una versión del agente no adjunta exemplar, el camino que sí queda garantizado por el código es: elegir el intervalo y el `service_name` en el panel, copiar la hora y buscar en Tempo las trazas de ese servicio. No se afirma que el exemplar exista hasta verlo en Explore.

## SLI que conecta con el resto

Disponibilidad = solicitudes HTTP 2xx de rutas `/api/` dividido entre todas las solicitudes de esas rutas, usando `increase(...[$__range])` sobre la ventana del dashboard. La serie es `http_server_request_duration_seconds_count` y la etiqueta de código es `http_response_status_code`. No se usa `rate()[5m]` con un mínimo artificial: cuando el contador deja de subir, esa forma dibuja 0 % aunque el periodo haya sido exitoso. El panel de errores 5xx suma un cero cuando no existe serie de fallos, para mostrar 0 % y no "No data". Un 404 de `ord-999` entra en el total y no en los 2xx, pero no es un error de servidor.

## Backend de trazas

Loki está en el compose y la consulta usa el atributo que el Collector escribe. Jaeger no participa: el uid de destino es `tempo`.

# OpenTelemetry Collector

Archivos:

- `observability/collector/collector-local.yaml` — el que monta Docker Compose.
- `observability/collector/collector-gcp.yaml` — Cloud Trace, Cloud Logging y Managed Prometheus.
- `observability/collector/collector-aws.yaml` — X-Ray, CloudWatch Logs y EMF.

Imagen: `otel/opentelemetry-collector-contrib:0.162.0`. Hace falta la distribución contrib porque `googlecloud`, `googlemanagedprometheus`, `awsxray`, `awscloudwatchlogs` y `awsemf` no están en el Collector core.

Los tres archivos pasaron `otelcol-contrib validate` en esta máquina. Eso comprueba el esquema. No comprueba que existan credenciales de nube.

## Receivers

Solo OTLP, en gRPC `0.0.0.0:4317` y HTTP `0.0.0.0:4318`. Las aplicaciones empujan métricas. No se configuró un receiver Prometheus scrapeando los microservicios: no exponen `/metrics` a propósito.

## Processors y su orden

En trazas el orden es `memory_limiter`, `resource`, `filter/health`, `batch`.

1. `memory_limiter` va primero. Con más de 400 MiB, o un pico de 100 MiB sobre el límite, rechaza datos nuevos en lugar de quedarse sin memoria. Esos rechazos aparecen como `otelcol_processor_refused_spans_total`.
2. `resource` inserta `deployment.environment=local` y `service.namespace=lab22` solo si el proceso todavía no los trae. `insert` no pisa lo que ya envió el agente.
3. `filter/health` tira spans cuya ruta es un actuator. Es una segunda barrera: el agente ya excluye esas URL.
4. `batch` va al final. Espera 5 segundos o 1024 elementos, con tope de 2048. Agrupar después de filtrar evita empaquetar spans que se van a descartar.

En GCP y AWS se suma `resourcedetection` después de `resource`, con `override: false`, para añadir atributos de la plataforma sin borrar los del servicio. En local no se activan los detectores `gcp` ni `ecs`: alargan el arranque buscando metadata que no existe.

Los logs locales pasan además por `transform/logs`, que copia `trace_id` y `span_id` al atributo y los antepone al cuerpo. Grafana enlaza la traza con la expresión `trace_id=([0-9a-f]{32})` sobre ese texto.

## Exporters locales

| Exporter | Destino | Señal |
|---|---|---|
| `otlp_grpc/tempo` | `tempo:4317`, TLS desactivado dentro de la red Docker | trazas |
| `prometheus` | `0.0.0.0:8889/metrics` | métricas, para que Prometheus haga scrape |
| `otlp_http/loki` | `http://loki:3100/otlp` | logs |
| `debug` | stdout del Collector, verbosidad `basic` y muestreo 2/500 | las tres, solo como rastro de diagnóstico |

`resource_to_telemetry_conversion` está deprecado en 0.162. En su lugar `resource_constant_labels.included` promociona `service.name`, `service.version`, `deployment.environment`, `service.namespace` y `cloud.provider` a etiquetas. `translation_strategy: UnderscoreEscapingWithSuffixes` produce nombres como `http_server_request_duration_seconds_count`. `enable_open_metrics: true` permite exemplars, y el datasource de Prometheus en Grafana los dirige a Tempo.

Los exporters OTLP tienen `retry_on_failure` (1 s, máximo 15 s, tope 120 s). Un Tempo caído no debe tumbar al Collector al primer error.

No hay exporter `logging`. En esta versión el componente vigente es `debug`.

## Métricas internas y salud

```yaml
service:
  telemetry:
    metrics:
      readers:
        - pull:
            exporter:
              prometheus:
                host: 0.0.0.0
                port: 8888
```

`address: 0.0.0.0:8888` ya no existe. El host tiene que ser `0.0.0.0`: el valor por defecto escucha en loopback y Prometheus, en otro contenedor, no lo vería.

Extensiones: `health_check` en `:13133`, `zpages` en `:55679` y `pprof` en `:1777` (sin publicar pprof al host). El healthcheck de Compose sigue siendo `otelcol-contrib --version` porque la imagen no trae shell ni curl. La comprobación útil es `http://localhost:13133/` desde el host, que es lo que hace `tests/verify-local.ps1`.

## GCP

`collector-gcp.yaml` exporta trazas y logs con `googlecloud` y métricas con `googlemanagedprometheus`. Exige `GCP_PROJECT_ID`, `CLOUD_REGION` y `DEPLOYMENT_ENVIRONMENT`. La cuenta de servicio del manifiesto GKE necesita `roles/cloudtrace.agent`, `roles/logging.logWriter` y `roles/monitoring.metricWriter`. Terraform los concede.

## AWS

`collector-aws.yaml` usa `awsxray`, `awscloudwatchlogs` (`/lab22/services`) y `awsemf` (`/lab22/metrics`, namespace `Lab22/Services`). La task role de Terraform permite `xray:PutTraceSegments`, `logs:PutLogEvents` y `cloudwatch:PutMetricData`. La imagen de ECS no es el Collector pelado: `deploy/aws/collector.Dockerfile` copia este YAML dentro de la imagen, porque Fargate no tiene un bind mount sencillo sin EFS.

## Si una señal no aparece

1. `curl http://localhost:13133/` — el proceso responde.
2. `docker logs lab22-collector` — busca `Exporting failed` o un exporter que no resolvió el host.
3. `http://localhost:55679/debug/tracez` — zpages del pipeline de trazas.
4. Logs del servicio: debe existir `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317` y el arranque debe decir `Arranque instrumentado`.
5. Si Prometheus no tiene series, abre `http://localhost:8889/metrics` y busca el nombre real. El dashboard usa el nombre traducido con sufijo `_seconds` y etiqueta `service_name`.

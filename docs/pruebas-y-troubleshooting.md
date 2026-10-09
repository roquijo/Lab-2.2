# Pruebas y diagnóstico

## Qué ya se ejecutó

`mvn test` en la raíz de Lab 2.2 compiló los dos módulos y pasó las pruebas de servicio, controlador y persistencia en H2. Eso cubre:

- el endpoint de pedidos y el de inventario;
- que service-a, con el cliente sustituido por un doble, pide el SKU guardado en la base;
- que la reserva descuenta `available` y aumenta `reserved`;
- que el span `order.compose` cuelga del span padre y que un fallo de service-b lo marca en error.

H2 no es MySQL. La comprobación del motor real es el contenedor `mysql:8.4` y `tests/verify-local.ps1`.

`docker compose config` aceptó el archivo. `otelcol-contrib validate` aceptó los tres YAML del Collector.

En esta máquina, el 2026-10-09, el stack local estaba arriba y `tests/verify-local.ps1` había pasado. Después del benchmark se volvió a consultar un pedido real. La evidencia de esa petición está en `docs/evidencias/`:

- `pedido-ord-001.json`: `ord-001`, SKU `LAPTOP-X1`, `stockSufficient=true`, `traceId=77fa223e7fd82dc6fb4ca2aea30f7f22`.
- `service-a-log-linea.txt` y `service-b-log-linea.txt`: el mismo `trace_id` en los dos stdout JSON. Los `span_id` son distintos (`6f4e7346db5b3b78` y `e971b873e68f1ccf`).
- `tempo-trace.json`: la misma traza, ya completa, con 13 spans de `service-a` y `service-b` (HTTP, `order.compose`, `inventory.lookup` y los `SELECT`). La primera lectura por id devolvió solo los spans de service-a; unos segundos después Tempo ya tenía los de service-b. Eso es la carrera del ingester, no una traza rota.
- `loki-lineas.txt`: dos líneas de Loki cuyo cuerpo empieza por `trace_id=77fa223e7fd82dc6fb4ca2aea30f7f22`.
- `sli-disponibilidad.json`: la consulta de 30 minutos devolvió `100`. `errores-5xx.json` devolvió `0`. `collector-up.json` devolvió `1`.

El benchmark de esa misma fecha está en `load-tests/results/`. No hay apply de Terraform ni despliegue en GKE o ECS: en el workspace no había credenciales ni, en el momento de la revisión, el binario `terraform`.

## Comandos

```powershell
mvn test
docker compose -f deploy/local/docker-compose.yaml config --quiet
docker compose -f deploy/local/docker-compose.yaml up -d --build
docker compose -f deploy/local/docker-compose.yaml ps
powershell -File tests/verify-local.ps1
powershell -File load-tests/run-benchmark.ps1
```

Consultas útiles cuando el stack está arriba:

```powershell
curl http://localhost:8080/api/orders/ord-001
curl "http://localhost:9090/api/v1/query?query=http_server_request_duration_seconds_count"
curl http://localhost:8889/metrics
curl http://localhost:13133/
docker logs lab22-service-a --tail 20
docker logs lab22-collector --tail 50
```

Tempo, sustituyendo el id:

```text
http://localhost:3200/api/traces/<traceId>
```

Loki:

```text
http://localhost:3100/loki/api/v1/query?query={service_name="service-a"}
```

Grafana Explore, datasource Loki, y en el resultado el campo derivado **Ver traza**.

## Fallos frecuentes

| Síntoma | Causa probable | Qué hacer |
|---|---|---|
| service-a reinicia y el log dice que no conecta a MySQL | El init de MySQL todavía no terminó, o el volumen viejo se creó con otra contraseña | `docker compose logs mysql`. Si la contraseña cambió, `docker compose down -v` y volver a subir |
| La respuesta no trae `traceId` | El contenedor arrancó en baseline | Revisa `OTEL_ENABLED` y que el log diga `Arranque instrumentado` |
| Hay `traceId` en el JSON y no en Loki | El Collector no exporta o Loki rechazó el OTLP | `docker logs lab22-collector` y `docker logs lab22-loki` |
| Prometheus no tiene la serie HTTP | El exporter aún no recibió el primer lote (15 s) o el nombre traducido es otro | Espera y abre `:8889/metrics`. Ajusta el dashboard al nombre real, no inventes la serie |
| Grafana marca un datasource en rojo | Tempo o Loki no arrancaron | `docker compose ps` y el log de ese servicio |
| El panel de disponibilidad sale vacío | La etiqueta no es `service_name` o el código no es `http_response_status_code` | En Explore, ejecuta `http_server_request_duration_seconds_count` sin filtros y mira las etiquetas |
| k6 no llega a la app | Desde el contenedor, `localhost` es el propio k6 | El script de benchmark usa `host.docker.internal` |
| `terraform` no existe | No está instalado | Los manifiestos igual se leen; el apply queda pendiente |
| Puertos ocupados | Otro compose del laboratorio docente | Para el otro stack o cambia el mapeo solo en local, y documenta el cambio |

## Lo que no debe darse por cumplido

Un archivo YAML no demuestra que Grafana enlace un log con una traza. Hace falta una petición real, el mismo `trace_id` en la respuesta, en stdout, en Loki y en Tempo. `verify-local.ps1` existe para esa comprobación y debe terminar en 0.

Tampoco se marca como desplegado GKE ni ECS. La validación de esos archivos, en esta sesión, es `otelcol-contrib validate` sobre sus Collector y la revisión del HCL. `terraform validate` exige el binario y `terraform init`, que no se ejecutaron porque Terraform no está instalado.

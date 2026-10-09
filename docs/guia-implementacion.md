# Guía de implementación

El orden es el que sigue el repositorio. Cada paso dice qué archivo mirar y cómo saber que funcionó.

## 1. Servicios de negocio

**Qué.** Dos procesos Spring Boot. service-a compone un pedido. service-b responde inventario y puede reservar unidades.

**Por qué.** Sin una regla de negocio, la telemetría no tiene nada que explicar. La regla aquí es `stockSufficient = available >= quantity`.

**Archivos.** `services/service-a` y `services/service-b`. El padre Maven es `pom.xml` (Spring Boot 3.5.16, Java 21, API OpenTelemetry 1.65.0).

**Comando.**

```powershell
mvn test
```

**Resultado.** Las pruebas de `OrderFulfillmentService` comprueban el span `order.compose` y que comparte `traceId` con su padre. `OrderFlowIntegrationTest` lee H2 y comprueba que service-a pide el SKU `LAPTOP-X1`. `InventoryPersistenceTest` reserva una unidad y deja `available` en 14.

**Fallo típico.** `getMeter(String, String)` no existe en el API 1.65. El proyecto usa `meterBuilder`.

## 2. Agente y logs

**Qué.** El contenedor descarga `opentelemetry-javaagent` 2.31.1 y `entrypoint.sh` lo activa solo si `OTEL_ENABLED` no es `false`.

**Por qué.** El agente cubre HTTP y JDBC sin meter el SDK en el jar. El mismo artefacto sirve para el baseline.

**Archivos.** `services/service-a/Dockerfile`, `entrypoint.sh`, `logback-spring.xml`, `TelemetryConfig.java`.

**Comando.** Lo ejecuta el build de Compose. En el log del contenedor debe aparecer `Arranque instrumentado`.

**Fallo típico.** Un `entrypoint.sh` con finales CRLF rompe el shebang en Linux. El Dockerfile ejecuta `sed` para quitar `\r`.

## 3. Collector y backends

**Qué.** OTLP entra al Collector y sale hacia Tempo, Prometheus y Loki.

**Archivos.** `observability/collector/collector-local.yaml`, `observability/prometheus/prometheus.yaml`, `observability/tracing/tempo.yaml`, `observability/loki/loki.yaml`.

**Comando.**

```powershell
docker run --rm --entrypoint /otelcol-contrib -v "${PWD}/observability/collector/collector-local.yaml:/etc/otelcol/config.yaml:ro" otel/opentelemetry-collector-contrib:0.162.0 validate --config=/etc/otelcol/config.yaml
```

**Resultado.** El comando termina en 0. En esta sesión también validó los YAML de GCP y AWS, pasando `GCP_PROJECT_ID` o `AWS_REGION`.

## 4. Compose

**Qué.** Levanta MySQL, los dos servicios, Collector, Tempo, Loki, Prometheus y Grafana.

**Archivo.** `deploy/local/docker-compose.yaml`.

**Comando**, desde la carpeta `Lab 2.2`:

```powershell
docker compose -f deploy/local/docker-compose.yaml up -d --build
docker compose -f deploy/local/docker-compose.yaml ps
curl http://localhost:8080/api/orders/ord-001
```

**Resultado esperado.** HTTP 200, `orderId` `ord-001`, `inventory.productSku` `LAPTOP-X1`, `stockSufficient` true y un `traceId` de 32 hexadecimales.

**Fallo típico.** El puerto 3000, 3306, 4317 u 8080 ocupado por otro laboratorio. El proyecto Compose se llama `lab22` para no chocar en nombres de contenedor, pero los puertos del host sí se pisan.

## 5. Comprobar la correlación

**Comando.**

```powershell
powershell -File tests/verify-local.ps1
```

El script pide el pedido, busca la serie en Prometheus, el log en Loki, la traza en Tempo (deben aparecer service-a y service-b) y el `traceId` en `docker logs`.

Grafana: `http://localhost:3000` con `admin` / `admin`. Dashboard **Lab 2.2 — Pedidos e inventario**.

## 6. Nube y benchmark

Esos pasos no forman parte del arranque local. Están en `docs/despliegue-gcp.md`, `docs/despliegue-aws.md` y `docs/benchmark-overhead.md`. No se ejecutaron despliegues cloud en esta implementación.

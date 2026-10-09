# Lab 2.2 — Observabilidad de pedidos e inventario

Dos microservicios Spring Boot consultan un pedido y su inventario en MySQL. OpenTelemetry recoge la traza de esa llamada, las métricas HTTP y JVM, y logs JSON con el mismo `trace_id`. En local, Grafana muestra las tres señales. En GKE y en ECS los manifiestos quedan listos; no se desplegaron porque este workspace no tiene credenciales de nube.

El repositorio `oTelLabs-main` se usó como referencia y no se modificó. El análisis está en `docs/analisis-repositorio-docente.md`.

## Requisitos

- JDK 21 y Maven 3.9, para las pruebas.
- Docker Desktop, para el stack local.
- Opcional, más adelante: `gcloud`, `kubectl`, Terraform y AWS CLI.

## Arranque local

Desde esta carpeta:

```powershell
docker compose -f deploy/local/docker-compose.yaml up -d --build
docker compose -f deploy/local/docker-compose.yaml ps
curl http://localhost:8080/api/orders/ord-001
powershell -File tests/verify-local.ps1
```

La primera construcción descarga el Java Agent y compila los dos jars dentro de Docker. MySQL tarda unos segundos en aceptar conexiones.

| URL | Uso |
|---|---|
| http://localhost:8080/api/orders/ord-001 | Pedido de demostración |
| http://localhost:8081/api/inventory/LAPTOP-X1 | Inventario directo |
| http://localhost:3000 | Grafana, usuario `admin`, contraseña `admin` |
| http://localhost:9090 | Prometheus |
| http://localhost:13133 | Salud del Collector |

Pedidos cargados: `ord-001` a `ord-005`. Reservar una unidad:

```powershell
curl -Method POST -ContentType "application/json" -Body '{"quantity":1}' http://localhost:8081/api/inventory/LAPTOP-X1/reservations
```

Para apagar:

```powershell
docker compose -f deploy/local/docker-compose.yaml down
```

`down -v` borra también la base.

## Pruebas sin Docker

```powershell
mvn test
```

## Dónde leer el diseño

| Documento | Contenido |
|---|---|
| `docs/arquitectura.md` | Componentes y recorrido de una petición |
| `docs/instrumentacion-opentelemetry.md` | Agente, spans, métricas y logs |
| `docs/collector.md` | Pipelines local, GCP y AWS |
| `docs/correlacion-cross-signal.md` | Cómo se salta de un log a una traza |
| `docs/guia-implementacion.md` | Orden de construcción |
| `docs/despliegue-gcp.md` | GKE, sin apply ejecutado |
| `docs/despliegue-aws.md` | ECS Fargate, sin apply ejecutado |
| `docs/benchmark-overhead.md` | Corrida real del 2026-10-09: p95 +11,36 %, memoria de los JVM entre +38 % y +62 % |
| `docs/pruebas-y-troubleshooting.md` | Comandos y fallos |
| `docs/decisiones-arquitectura.md` | Qué se descartó |

Las variables de ejemplo están en `.env.example`. No hay claves de nube en el repositorio.

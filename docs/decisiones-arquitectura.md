# Decisiones de arquitectura

## Lenguaje y framework

El ejemplo del docente es FastAPI. Este laboratorio usa Java 21 y Spring Boot 3.5.16 porque es el stack que ya se puede mantener y defender. Spring Boot 4.1.1 ya existe, pero cambia suficientes valores por defecto como para desviar el trabajo de la observabilidad. La línea 3.5 sigue publicada y el agente 2.31.1 la instrumenta.

Maven, no Gradle, por la misma razón.

## Agente en vez de SDK embebido

Incluir el SDK y además el agente duplica spans. El jar solo usa el API. El agente aporta HTTP de entrada, HTTP de salida, JDBC y el puente de logs. Los spans `order.compose`, `inventory.lookup` e `inventory.reserve` cubren lo que el agente no puede nombrar: la decisión de negocio.

Se descartó `@WithSpan` junto con spans manuales. Las dos técnicas a la vez generan dos spans con el mismo alcance.

## MySQL y JdbcTemplate

MySQL 8.4 es el motor pedido. JdbcTemplate deja la sentencia a la vista y el agente la instrumenta. JPA habría escondido el SQL sin aportar nada al objetivo.

No se copió la espera aleatoria de `service-b`. Medía el `sleep`, no la base de datos.

## Un solo backend de trazas

Jaeger y Tempo juntos duplican exportación y obligan a elegir un uid en Grafana. Tempo es el que encaja con Loki y con los exemplars de Prometheus. Se pierde la UI propia de Jaeger en el puerto 16686. La búsqueda queda en Grafana Explore.

## Loki además de stdout

Stdout demuestra el JSON en `docker logs`. No permite el salto desde la interfaz. Loki 3 recibe OTLP en `/otlp`, así que no hace falta el exporter `loki` antiguo ni Promtail. El processor `transform/logs` existe porque el cuerpo OTLP es el mensaje, no la línea JSON completa, y el derived field de Grafana necesita ver `trace_id=` en el texto.

## Métricas solo por el Collector

Micrometer en `/actuator/prometheus` y el agente a la vez publicarían dos histogramas HTTP. El actuator solo expone salud. Prometheus scrapea al Collector. La etiqueta de servicio sale de `resource_constant_labels`, el mecanismo vigente en Collector 0.162; `resource_to_telemetry_conversion` ya está deprecado.

## Muestreo en el SDK, no tail sampling

Tail sampling retiene la traza varios segundos y se decide cuando ya terminó. En un laboratorio corto esconde justo la petición que se acaba de hacer. El ratio de cabeza está en una variable de entorno y en local vale 1.

## Nube preparada y no aplicada

GKE y ECS forman parte del encargo, y no había credenciales. Terraform y los manifiestos describen recursos reales (red, cluster, roles, task, ALB), no un dibujo. El documento de cada nube dice que falta `apply`.

En ECS, MySQL va en la task por defecto para no crear una RDS de pago sin una decisión explícita. El precio es que el disco es efímero. `create_rds` deja la alternativa escrita.

En GKE el Collector es un Deployment de una réplica, no un DaemonSet. Con dos servicios de laboratorio un DaemonSet multiplica procesos y configuración sin cambiar el recorrido de las señales. El ejemplo usaba DaemonSet y además dejaba sin definir la cuenta de servicio.

## Benchmark

k6, no Locust, porque el script cabe en un contenedor oficial y el summary JSON ya trae percentiles. La corrida por defecto es corta. Alargarla es cambiar `VUS` y `DURATION`, no cambiar el código de negocio.

## Qué se acepta como límite

- CPU y memoria del dashboard son del JVM, no del cgroup. cAdvisor mediría el contenedor y añadiría otro componente para responder una pregunta que el agente ya responde sobre el proceso.
- El healthcheck interno del Collector no puede hacer HTTP.
- X-Ray y Cloud Monitoring no reproducen el dashboard de Grafana. La correlación de esos entornos se hace por el `trace_id` en su propia consola.
- H2 en las pruebas de Maven no sustituye al MySQL del compose.

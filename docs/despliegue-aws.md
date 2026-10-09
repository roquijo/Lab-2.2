# Despliegue en AWS (ECS Fargate)

## Qué se va a crear

`terraform/aws` crea una VPC `10.22.0.0/16` con dos subredes públicas, un Internet Gateway, un ALB, un cluster ECS, roles de ejecución y de tarea, log groups y una task Fargate. `create_rds` vale false: MySQL va dentro de la task para no levantar una instancia RDS sin pedirlo. Si lo pones en true, Terraform crea un `db.t4g.micro` MySQL 8.4 y la JDBC URL apunta a esa instancia; la plantilla deja fuera el contenedor MySQL.

La task escucha en una sola ENI. service-a llama a service-b y al Collector por `127.0.0.1`. El ALB reenvía el puerto 80 al 8080 de service-a y usa `/actuator/health/liveness`.

Telemetría, según `collector-aws.yaml`:

| Señal | Destino | Permiso en la task role |
|---|---|---|
| Trazas | AWS X-Ray (`awsxray`) | `xray:PutTraceSegments`, `xray:PutTelemetryRecords` |
| Logs OTLP | CloudWatch Logs `/lab22/services` | `logs:PutLogEvents` |
| Métricas | CloudWatch EMF, namespace `Lab22/Services` | `cloudwatch:PutMetricData` |

Los logs de stdout de cada contenedor van además a `/lab22/service-a`, `/lab22/service-b`, `/lab22/collector` y `/lab22/mysql` por el driver `awslogs`. Esos logs de contenedor son el equivalente de `docker logs`. La correlación con X-Ray usa el `trace_id` del JSON; X-Ray no ofrece el derived field de Grafana.

La contraseña de MySQL entra por SSM SecureString `/lab22/db-password`, no por el JSON. El rol de ejecución solo puede leer ese parámetro.

## Imagen del Collector

Fargate no monta el YAML del repositorio. Hay que hornear la configuración:

```powershell
docker build -f deploy/aws/collector.Dockerfile -t lab22-collector:1.0.0 .
```

El contexto es la raíz de Lab 2.2. La imagen base es el Collector contrib 0.162.0 y el archivo copiado es `observability/collector/collector-aws.yaml`.

Las imágenes de los servicios se construyen con los mismos Dockerfile del entorno local. El agente ya va dentro.

## Comandos, cuando tengas cuenta

```powershell
aws ecr create-repository --repository-name lab22/service-a
aws ecr create-repository --repository-name lab22/service-b
aws ecr create-repository --repository-name lab22/collector
# login, tag y push de las tres imágenes

cd terraform/aws
copy terraform.tfvars.example terraform.tfvars
# edita las URI de ECR y db_password
terraform init
terraform plan
terraform apply
```

La salida `alb_dns` es la URL del pedido: `http://<alb_dns>/api/orders/ord-001`.

X-Ray: busca el servicio `service-a` y comprueba un segmento de service-b en la misma traza. CloudWatch Logs Insights sobre `/lab22/service-a`:

```sql
fields @timestamp, @message
| filter @message like /trace_id/
| sort @timestamp desc
| limit 20
```

El id debe coincidir con el de la traza de X-Ray. X-Ray a veces muestra el id con un guion después del primer grupo; el valor OTel son 32 hexadecimales seguidos.

## Limpieza

```powershell
cd terraform/aws
terraform destroy
```

`skip_final_snapshot` está en true solo en la RDS opcional. Destruye el balanceador, la task y los log groups que crea este stack. No borra los repositorios ECR: hazlo a mano si ya no los quieres, porque pueden seguir costando por las imágenes.

## Limitaciones

- MySQL dentro de la task pierde los datos al reemplazarla. Sirve para el laboratorio, no para guardar pedidos reales. Para persistencia hay que poner `create_rds = true` y cargar `deploy/local/mysql/init.sql` en esa instancia.
- El healthcheck del Collector es `--version`, igual que en local, porque la imagen no tiene curl. `dependsOn` usa `START`, no `HEALTHY`.
- EMF no es Prometheus. El dashboard de Grafana del repositorio no se conecta solo a CloudWatch. En AWS la exploración de métricas es la consola de CloudWatch o un workspace AMP, que este laboratorio no aprovisiona para no sumar otro backend.
- Las subredes son públicas y la task tiene IP pública para poder sacar imágenes y hablar con X-Ray sin un NAT. En producción irían en subredes privadas con NAT o VPC endpoints.

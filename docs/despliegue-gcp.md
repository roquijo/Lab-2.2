# Despliegue en GCP (GKE)

Este despliegue no se aplicó. No hay cuenta ni proyecto configurados en el workspace. Los archivos se pueden revisar; `terraform apply` y `kubectl apply` quedan para cuando existan credenciales.

## Qué se va a crear

Terraform (`terraform/gcp`) crea:

- red `lab22` y subred `10.22.0.0/20`;
- un clúster GKE zonal (más barato que uno regional) con Workload Identity;
- un node pool de 2 máquinas `e2-standard-2`;
- Artifact Registry `lab22`;
- la cuenta `otel-collector` con `cloudtrace.agent`, `logging.logWriter` y `monitoring.metricWriter`, y el binding de Workload Identity hacia `lab22/otel-collector`.

Los manifiestos (`deploy/gcp/k8s`) crean el namespace, MySQL, el Collector y los dos servicios. El Collector usa `observability/collector/collector-gcp.yaml` mediante un ConfigMap de Kustomize. Las trazas van a Cloud Trace, los logs a Cloud Logging y las métricas a Google Cloud Managed Service for Prometheus.

MySQL vive en el clúster como StatefulSet de laboratorio. No es Cloud SQL. La contraseña no está en el ConfigMap: se copia desde `mysql-secret.example.yaml`.

## Preparación

1. Instala Terraform 1.6 o superior, `gcloud` y `kubectl`.
2. `gcloud auth login` y `gcloud auth application-default login`.
3. Copia `terraform/gcp/terraform.tfvars.example` a `terraform.tfvars` y pon un `project_id` real. No subas ese archivo si contiene datos de tu cuenta; el ejemplo ya usa un marcador.
4. En `deploy/gcp/k8s/kustomization.yaml` cambia `REGION` y `PROJECT_ID` de las imágenes.
5. En `collector.yaml` cambia `PROJECT_ID` de la anotación de Workload Identity.
6. En el ConfigMap `lab22-env` de `services.yaml` pon el mismo project id.
7. Copia `mysql-secret.example.yaml` a `mysql-secret.yaml`, cambia las contraseñas y aplícalo. Ese archivo está ignorado por git.

## Comandos

```powershell
cd terraform/gcp
terraform init
terraform plan
terraform apply

gcloud container clusters get-credentials lab22 --zone us-central1-a --project TU_PROJECT_ID

cd ../..
docker build -f services/service-a/Dockerfile -t REGION-docker.pkg.dev/TU_PROJECT_ID/lab22/service-a:1.0.0 .
docker build -f services/service-b/Dockerfile -t REGION-docker.pkg.dev/TU_PROJECT_ID/lab22/service-b:1.0.0 .
gcloud auth configure-docker REGION-docker.pkg.dev
docker push REGION-docker.pkg.dev/TU_PROJECT_ID/lab22/service-a:1.0.0
docker push REGION-docker.pkg.dev/TU_PROJECT_ID/lab22/service-b:1.0.0

kubectl apply -f deploy/gcp/k8s/mysql-secret.yaml
kubectl apply -k deploy/gcp/k8s
kubectl -n lab22 get pods
```

El contexto de build es la raíz de Lab 2.2, igual que en Compose.

## Cómo comprobarlo

- `kubectl -n lab22 get pods` con todos los pods Ready.
- La IP del Service `service-a` (`type: LoadBalancer`) responde `GET /api/orders/ord-001`.
- En Cloud Trace aparece una traza con service-a y service-b.
- En Cloud Logging, el log JSON del pedido contiene el mismo `trace_id`. Cloud Logging no usa el derived field de Grafana; el vínculo se hace buscando el campo.
- Managed Prometheus recibe las métricas del exporter `googlemanagedprometheus`.

## Limpieza

```powershell
kubectl delete namespace lab22
cd terraform/gcp
terraform destroy
```

`deletion_protection` del clúster está en false para que el destroy del laboratorio no se quede bloqueado. No uses esa opción en un clúster compartido.

## Errores probables

- El Collector reinicia porque `GCP_PROJECT_ID` sigue en `REEMPLAZAR_PROJECT_ID`.
- Workload Identity falla si el nombre del Kubernetes ServiceAccount no es `otel-collector` en el namespace `lab22`.
- El primer arranque de MySQL tarda y service-a reinicia hasta que el StatefulSet acepta conexiones. Es esperable una vez.
- Cloud Monitoring no entiende todas las métricas OTLP como lo hace Prometheus. Por eso el exporter de métricas es `googlemanagedprometheus` y no el exporter genérico de métricas de `googlecloud`.

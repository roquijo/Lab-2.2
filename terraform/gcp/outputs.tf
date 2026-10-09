output "cluster_name" {
  value = google_container_cluster.lab22.name
}

output "get_credentials" {
  value = "gcloud container clusters get-credentials ${google_container_cluster.lab22.name} --zone ${var.zone} --project ${var.project_id}"
}

output "artifact_registry" {
  value = "${var.region}-docker.pkg.dev/${var.project_id}/lab22"
}

output "collector_service_account" {
  value = google_service_account.collector.email
}

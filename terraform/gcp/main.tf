resource "google_artifact_registry_repository" "lab22" {
  location      = var.region
  repository_id = "lab22"
  format        = "DOCKER"
  description   = "Imágenes de service-a y service-b del laboratorio 2.2"
}

resource "google_service_account" "collector" {
  account_id   = "otel-collector"
  display_name = "OpenTelemetry Collector Lab 2.2"
}

resource "google_project_iam_member" "collector_roles" {
  for_each = toset([
    "roles/cloudtrace.agent",
    "roles/logging.logWriter",
    "roles/monitoring.metricWriter",
  ])
  project = var.project_id
  role    = each.value
  member  = "serviceAccount:${google_service_account.collector.email}"
}

resource "google_service_account_iam_member" "workload_identity" {
  service_account_id = google_service_account.collector.name
  role               = "roles/iam.workloadIdentityUser"
  member             = "serviceAccount:${var.project_id}.svc.id.goog[lab22/otel-collector]"
}

resource "google_compute_network" "lab22" {
  name                    = "lab22"
  auto_create_subnetworks = false
}

resource "google_compute_subnetwork" "lab22" {
  name          = "lab22"
  ip_cidr_range = "10.22.0.0/20"
  region        = var.region
  network       = google_compute_network.lab22.id
}

resource "google_container_cluster" "lab22" {
  name     = var.cluster_name
  location = var.zone

  network    = google_compute_network.lab22.name
  subnetwork = google_compute_subnetwork.lab22.name

  remove_default_node_pool = true
  initial_node_count       = 1
  deletion_protection      = false

  workload_identity_config {
    workload_pool = "${var.project_id}.svc.id.goog"
  }
}

resource "google_container_node_pool" "lab22" {
  name     = "lab22-pool"
  location = var.zone
  cluster  = google_container_cluster.lab22.name

  node_count = var.node_count

  node_config {
    machine_type    = var.machine_type
    service_account = google_service_account.collector.email
    oauth_scopes    = ["https://www.googleapis.com/auth/cloud-platform"]

    labels = {
      app = "lab22"
    }
  }
}

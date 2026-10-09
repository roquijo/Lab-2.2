variable "project_id" {
  description = "ID del proyecto de Google Cloud. No tiene valor por defecto a propósito."
  type        = string
}

variable "region" {
  description = "Región del registro de artefactos."
  type        = string
  default     = "us-central1"
}

variable "zone" {
  description = "Zona del clúster. Un clúster zonal reduce el costo del laboratorio."
  type        = string
  default     = "us-central1-a"
}

variable "cluster_name" {
  type    = string
  default = "lab22"
}

variable "node_count" {
  type    = number
  default = 2
}

variable "machine_type" {
  type    = string
  default = "e2-standard-2"
}

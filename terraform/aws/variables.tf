variable "region" {
  type    = string
  default = "us-east-1"
}

variable "service_a_image" {
  description = "URI completa de la imagen de service-a en ECR."
  type        = string
}

variable "service_b_image" {
  description = "URI completa de la imagen de service-b en ECR."
  type        = string
}

variable "db_password" {
  description = "Contraseña de MySQL del laboratorio. Márcala como sensible y no la subas a git."
  type        = string
  sensitive   = true
}

variable "collector_image" {
  description = "Imagen del Collector con collector-aws.yaml embebido. Ver deploy/aws/collector.Dockerfile."
  type        = string
}

variable "create_rds" {
  description = "Si es true crea una instancia RDS MySQL. El valor por defecto deja MySQL dentro de la tarea para no generar costo de RDS sin una decisión explícita."
  type        = bool
  default     = false
}

variable "alb_parameter_name" {
  description = "The parameter name to derive the ALB details from."
  type        = string
}

variable "app_container_name" {
  description = "The name of the primary application container"
  type        = string
  default     = "ogcapi-java-container"
}

variable "app_name" {
  description = "The name of the application e.g. sample-django-app"
  type        = string
}

variable "app_port" {
  description = "The port to the application container."
  type        = number
  default     = 3456
}

variable "app_hostnames" {
  description = "Hostnames to associate with the application"
  type        = list(string)
}

variable "container_vars" {
  description = "Map of key/pair values to pass to the container definition."
  type        = map(any)
}

variable "cpu" {
  description = "The CPU capacity to allocate to the task."
  type        = number
  default     = 512
}

variable "ecr_registry" {
  description = "The registry to pull docker images from."
  type        = string
}

variable "ecr_repository" {
  description = "The repository to pull the image from."
  type        = string
}

variable "environment" {
  description = "Environment name to prepend/append to resource names"
  type        = string
}

variable "image" {
  description = "The digest/tag of the docker image to pull from ECR"
  type        = string
  default     = "ogcapi:latest"
}

variable "memory" {
  description = "The CPU capacity to allocate to the task."
  type        = number
  default     = 1024
}

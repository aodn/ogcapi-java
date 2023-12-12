# ssm parameters
variable "alb_parameter_name" {
  description = "The parameter name to derive the ALB details from."
  type        = string
}

variable "ecr_parameter_name" {
  description = "The parameter name to derive the ALB details from."
  type        = string
}

variable "rds_parameter_name" {
  description = "The parameter name to derive the database host from."
  type        = string
}

# general variables
variable "app_name" {
  description = "The name of the application e.g. sample-django-app"
  type        = string
}

variable "app_hostnames" {
  description = "Hostnames to associate with the application"
  type        = list(string)
}

variable "container_name" {
  description = "The name of the primary application container"
  type        = string
  default     = "app"
}

variable "container_port" {
  description = "The port to expose to the load balancer on the container"
  type        = number
  default     = 80
}

variable "environment" {
  description = "Environment name to prepend/append to resource names"
  type        = string
}

variable "image" {
  description = "The digest/tag of the docker image to pull from ECR"
  type        = string
}

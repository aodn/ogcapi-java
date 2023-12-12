# Container environment variables
variable "allowed_hosts" {
  description = "Hosts allowed to access the application container (i.e. 127.0.0.1)"
  type        = string
}

variable "allowed_cidr_nets" {
  description = "Subnet CIDR's allowed to access the application container"
  type        = string
}

variable "django_secret_key" {
  description = "The secret key for django app"
  type        = string
}

variable "db_host" {
  description = "Override variable for database host"
  type        = string
}

variable "db_name" {
  description = "The name of the database"
  type        = string
}

variable "db_user" {
  description = "The user to connect to the database with"
  type        = string
}

variable "db_secret_name" {
  description = "The name of the secret to fetch DB login credentials from"
  type        = string
}

variable "db_secret_region" {
  description = "The region to fetch the secret from"
  type        = string
  default     = "ap-southeast-2"
}

variable "s3_storage_bucket_name" {
  description = "Name of the S3 bucket to use"
  type        = string
}

variable "s3_storage_bucket_region" {
  description = "The bucket region"
  type        = string
}

include "global" {
  path   = "./global.hcl"
  expose = true
}

inputs = {
  app_name    = get_env("APP_NAME")
  environment = local.global.environment

  # fetch the ssm parameter names
  alb_parameter_name = get_env("ALB_PARAMETER_NAME")
  ecr_parameter_name = get_env("ECR_PARAMETER_NAME")
  rds_parameter_name = get_env("RDS_PARAMETER_NAME")

  # DNS hostnames to associate with the container
  app_hostnames = ["api-${local.global.environment}"]

  # get docker environment variable values with default fallback values
  allowed_hosts            = get_env("ALLOWED_HOSTS", "*")
  allowed_cidr_nets        = get_env("ALLOWED_CIDR_NETS", "")
  django_secret_key        = get_env("DJANGO_SECRET_KEY", "changeme")
  db_host                  = get_env("DB_HOST", "")
  db_name                  = get_env("DB_NAME", "api")
  db_user                  = get_env("DB_USER", "api")
  db_secret_name           = get_env("DB_SECRET_NAME", "/rds/stefan-db/primary/evaluation/api")
  db_secret_region         = get_env("DB_SECRET_REGION", "ap-southeast-2")
  s3_storage_bucket_name   = get_env("S3_STORAGE_BUCKET_NAME", "")
  s3_storage_bucket_region = get_env("S3_STORAGE_BUCKET_REGION", "")
}

locals {
  global = include.global.locals
}

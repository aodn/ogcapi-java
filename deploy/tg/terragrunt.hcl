include "global" {
  path   = "./global.hcl"
  expose = true
}

inputs = {
  app_name    = get_env("APP_NAME")
  environment = local.global.environment

  # fetch the ssm parameter names
  // alb_parameter_name = get_env("ALB_PARAMETER_NAME")
  // ecr_parameter_name = get_env("ECR_PARAMETER_NAME")

  # DNS hostnames to associate with the container
  app_hostnames = ["api-${local.global.environment}"]

  # get docker environment variable values with default fallback values
  // allowed_hosts            = get_env("ALLOWED_HOSTS", "*")
  // allowed_cidr_nets        = get_env("ALLOWED_CIDR_NETS", "")
}

locals {
  global = include.global.locals
}

include "global" {
  path   = "./global.hcl"
  expose = true
}

inputs = {
  app_name    = get_env("APP_NAME")
  environment = local.global.environment

  # fetch the ssm parameter names
  alb_parameter_name = get_env("ALB_PARAMETER_NAME")

  # DNS hostnames to associate with the container
  app_hostnames = ["api-${local.global.environment}"]

  # container-specific environment variables
  container_vars = local.container_vars

  ecr_registry   = get_env("ECR_REGISTRY")
  ecr_repository = get_env("ECR_REPOSITORY")
}

locals {
  container_var_defaults = yamldecode(file("../container/vars.yaml"))
  # get any overrides from the environment (e.g. GitHub deployment variables)
  container_vars = { for k, v in local.container_var_defaults : k => can(get_env(upper(k))) ? get_env(upper(k)) : v }
  global         = include.global.locals
}

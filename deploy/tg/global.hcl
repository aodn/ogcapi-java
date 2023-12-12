locals {
  aws_account  = get_env("AWS_ACCOUNT_ID")
  aws_region   = get_env("AWS_REGION")
  environment  = get_env("ENVIRONMENT")
  project_name = "sample-django-app"
  state_bucket = "tfstate-${local.aws_account}-${local.aws_region}"
  state_key    = "apps/${local.project_name}/${local.environment}/ecs.tfstate"
}

generate "providers" {
  path      = "providers.tf"
  if_exists = "overwrite_terragrunt"
  contents  = <<EOF
provider "aws" {
  region              = "${local.aws_region}"
  allowed_account_ids = ["${local.aws_account}"]
  default_tags {
    tags = {
      "Environment" = "apps"
      "ManagedBy" = "Apps - ${local.state_bucket}/${local.state_key}"
      "Owner" = "Platform Engineering"
      "Project" = "AODN Applications"
      "Repository" = "aodn/sample-django-app"
    }
  }
}
EOF
}

remote_state {
  backend = "s3"
  generate = {
    path      = "backend.tf"
    if_exists = "overwrite_terragrunt"
  }
  config = {
    bucket                      = local.state_bucket
    key                         = local.state_key
    region                      = local.aws_region
    dynamodb_table              = local.state_bucket
    skip_credentials_validation = true
    skip_metadata_api_check     = true
    skip_region_validation      = true
    disable_bucket_update       = true
    encrypt                     = true
  }
}

terraform {
  source = "${get_repo_root()}//deploy/tf"
}

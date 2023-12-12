locals {
  # alb values
  alb_dns_name           = nonsensitive(data.aws_ssm_parameter.alb_dns_name.value)
  alb_https_listener_arn = nonsensitive(data.aws_ssm_parameter.alb_https_listener_arn.value)
  alb_zone_id            = nonsensitive(data.aws_ssm_parameter.alb_zone_id.value)

  # core values
  vpc_id               = nonsensitive(data.aws_ssm_parameter.vpc_id.value)
  vpc_cidr             = nonsensitive(data.aws_ssm_parameter.vpc_cidr.value)
  domain_name          = nonsensitive(data.aws_ssm_parameter.zonename.value)
  domain_zone_id       = nonsensitive(data.aws_ssm_parameter.zoneid.value)
  public_subnets       = split(",", nonsensitive(data.aws_ssm_parameter.public_subnets.value))
  public_subnet_cidrs  = nonsensitive(data.aws_ssm_parameter.public_subnet_cidrs.value)
  private_subnets      = split(",", nonsensitive(data.aws_ssm_parameter.private_subnets.value))
  private_subnet_cidrs = nonsensitive(data.aws_ssm_parameter.private_subnet_cidrs.value)

  # ecr values
  ecr_repository_url = nonsensitive(data.aws_ssm_parameter.ecr_repository_url.value)

  # rds values
  rds_url = nonsensitive(data.aws_ssm_parameter.rds_url.value)
}

# alb parameters
data "aws_ssm_parameter" "alb_dns_name" {
  name = "/apps/alb/${var.alb_parameter_name}/alb_dns_name"
}

data "aws_ssm_parameter" "alb_https_listener_arn" {
  name = "/apps/alb/${var.alb_parameter_name}/alb_https_listener_arn"
}

data "aws_ssm_parameter" "alb_zone_id" {
  name = "/apps/alb/${var.alb_parameter_name}/alb_zone_id"
}

# core parameters
data "aws_ssm_parameter" "vpc_id" {
  name = "/core/vpc/vpc_id"
}

data "aws_ssm_parameter" "vpc_cidr" {
  name = "/core/vpc/vpc_cidr"
}

data "aws_ssm_parameter" "public_subnets" {
  name = "/core/vpc/subnets_public"
}

data "aws_ssm_parameter" "public_subnet_cidrs" {
  name = "/core/vpc/subnets_public_cidr"
}

data "aws_ssm_parameter" "private_subnets" {
  name = "/core/vpc/subnets_private"
}

data "aws_ssm_parameter" "private_subnet_cidrs" {
  name = "/core/vpc/subnets_private_cidr"
}

data "aws_ssm_parameter" "zonename" {
  name = "/core/dnszone/zone_domain"
}

data "aws_ssm_parameter" "zoneid" {
  name = "/core/dnszone/zone_id"
}

# ecr parameters
data "aws_ssm_parameter" "ecr_repository_url" {
  name = "/apps/ecr/${var.ecr_parameter_name}/ecr_repository_url"
}

# rds parameters
data "aws_ssm_parameter" "rds_url" {
  name = "/rds/${var.rds_parameter_name}/rds_url"
}

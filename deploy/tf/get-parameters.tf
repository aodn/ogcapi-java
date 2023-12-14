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
  name = "/core/vpc_id"
}

data "aws_ssm_parameter" "vpc_cidr" {
  name = "/core/vpc_cidr"
}

data "aws_ssm_parameter" "public_subnets" {
  name = "/core/subnets_public"
}

data "aws_ssm_parameter" "public_subnet_cidrs" {
  name = "/core/subnets_public_cidr"
}

data "aws_ssm_parameter" "private_subnets" {
  name = "/core/subnets_private"
}

data "aws_ssm_parameter" "private_subnet_cidrs" {
  name = "/core/subnets_private_cidr"
}

data "aws_ssm_parameter" "zonename" {
  name = "/core/zone_domain"
}

data "aws_ssm_parameter" "zoneid" {
  name = "/core/zone_id"
}

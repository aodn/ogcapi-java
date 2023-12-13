locals {
  # set container definition variables with default fallback values from ssm if available
  app_vars = {}

  app_container_vars   = [for k, v in local.app_vars : { name = upper(k), value = v }]
  # ecr_registry         = split("/", local.ecr_repository_url)[0]
}

module "ecs" {
  source  = "terraform-aws-modules/ecs/aws"
  version = "~> 5.7.0"

  # Cluster Configuration
  cluster_name = "${var.app_name}-${var.environment}"
  cluster_configuration = {
    name  = "containerInsights"
    value = "enabled"
  }
  create_task_exec_iam_role = true
  fargate_capacity_providers = {
    FARGATE = {
      default_capacity_provider_strategy = {
        weight = 50
      }
    }
    FARGATE_SPOT = {
      default_capacity_provider_strategy = {
        weight = 50
      }
    }
  }

  # Service Configuration
  services = {

    "${var.app_name}-${var.environment}" = {
      capacity_provider_strategy = {
        dedicated = {
          base              = 0
          capacity_provider = "FARGATE"
          weight            = 100
        }
        #        spot = {
        #          base              = 0
        #          capacity_provider = "FARGATE_SPOT"
        #          weight            = 100
        #        }
      }

      # allow ECS exec commands on containers (e.g. to get a shell session)
      enable_execute_command = true

      # resources
      cpu    = 512
      memory = 1024

      # wait for service to reach steady state
      wait_for_steady_state = true

      # Container definition(s)
      container_definitions = {
        app = {
          name  = var.container_name
          image = "450356697252.dkr.ecr.ap-southeast-4.amazonaws.com/ogcapi:latest"
          health_check = {}
          readonly_root_filesystem = false
          essential                = true
          memory_reservation       = 256
          environment              = local.app_container_vars
          port_mappings = [
            {
              name          = var.container_name
              containerPort = 80
              hostPort      = 80
            }
          ]
        }
      }

      deployment_circuit_breaker = {
        enable   = true
        rollback = true
      }

      load_balancer = {
        service = {
          target_group_arn = aws_lb_target_group.app.arn
          container_name   = var.container_name
          container_port   = var.container_port
        }
      }

      subnet_ids = local.private_subnets

      security_group_rules = {
        ingress_vpc = {
          type        = "ingress"
          from_port   = var.container_port
          to_port     = var.container_port
          protocol    = "tcp"
          cidr_blocks = [local.vpc_cidr]
        }
        egress_all = {
          type        = "egress"
          from_port   = 0
          to_port     = 0
          protocol    = "-1"
          cidr_blocks = ["0.0.0.0/0"]
        }
      }
    }
  }
}

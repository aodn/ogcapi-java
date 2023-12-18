locals {
  app_container_vars   = [for k, v in var.container_vars : { name = upper(k), value = v }]

  container_definitions = local.app_container_definition
  app_container_definition = {
    app = {
      name = var.app_container_name
      image = "${var.ecr_registry}/${var.ecr_repository}:${var.ecr_tag}"
      health_check = {
        command = ["CMD-SHELL", "uwsgi-is-ready --stats-socket /tmp/statsock > /dev/null 2>&1 || exit 1"]
      }
      readonly_root_filesystem = false
      essential                = true
      memory_reservation       = 256
      environment              = local.app_container_vars
      port_mappings = [
        {
          name          = var.app_container_name
          containerPort = var.app_port
          hostPort      = var.app_port
        }
      ]
      mount_points = [
        {
          readOnly      = false
          containerPath = "/vol/web"
          sourceVolume  = "static"
        }
      ]
    }
  }
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
        env_strategy = {
          base              = 0
          capacity_provider = "FARGATE"
          weight            = 100
        }
      }

      # allow ECS exec commands on containers (e.g. to get a shell session)
      enable_execute_command = true

      # resources
      cpu    = var.cpu
      memory = var.memory

      # do not force a new deployment unless the image digest has changed
      force_new_deployment = false

      # # wait for service to reach steady state
      # wait_for_steady_state = true

      # Container definition(s)
      container_definitions = local.container_definitions

      deployment_circuit_breaker = {
        enable   = true
        rollback = true
      }

      load_balancer = {
        service = {
          target_group_arn = aws_lb_target_group.app.arn
          container_name   = var.app_container_name
          container_port   = var.app_port
        }
      }

      subnet_ids = local.private_subnets

      security_group_rules = {
        ingress_vpc = {
          type        = "ingress"
          from_port   = var.app_port
          to_port     = var.app_port
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

      timeouts = {
        create = "10m"
        update = "5m"
        delete = "10m"
      }

      volume = {
        static = {}
      }
    }
  }
}

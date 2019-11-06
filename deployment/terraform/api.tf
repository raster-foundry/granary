#
# Security Group Resources
#
resource "aws_security_group" "alb" {
  name   = "sg${var.project}APILoadBalancer"
  vpc_id = data.terraform_remote_state.core.outputs.vpc_id

  tags = {
    Name    = "sg${var.project}APILoadBalancer",
    Project = var.project
  }
}

#
# ALB Resources
#
resource "aws_lb" "api" {
  name            = "alb${var.project}API"
  security_groups = [aws_security_group.alb.id]
  subnets         = data.terraform_remote_state.core.outputs.public_subnet_ids

  enable_http2 = true

  tags = {
    Name    = "alb${var.project}API"
    Project = var.project
  }
}

resource "aws_lb_target_group" "api" {
  name = "tg${var.project}API"

  health_check {
    healthy_threshold   = 3
    interval            = 30
    matcher             = 200
    protocol            = "HTTP"
    timeout             = 3
    path                = "/api/hello/world"
    unhealthy_threshold = 2
  }

  port     = 80
  protocol = "HTTP"
  vpc_id   = data.terraform_remote_state.core.outputs.vpc_id

  tags = {
    Name    = "tg${var.project}API"
    Project = var.project
  }
}

resource "aws_lb_listener" "api_redirect" {
  load_balancer_arn = aws_lb.api.arn
  port              = 80
  protocol          = "HTTP"

  default_action {
    type = "redirect"

    redirect {
      port        = 443
      protocol    = "HTTPS"
      status_code = "HTTP_301"
    }
  }
}

resource "aws_lb_listener" "api" {
  load_balancer_arn = aws_lb.api.id
  port              = 443
  protocol          = "HTTPS"
  certificate_arn   = data.terraform_remote_state.core.outputs.ssl_certificate_arn

  default_action {
    target_group_arn = aws_lb_target_group.api.id
    type             = "forward"
  }
}

#
# ECS Resources
#
resource "aws_ecs_task_definition" "api" {
  family = "${var.project}API"

  container_definitions = templatefile("${path.module}/task-definitions/api.json.tmpl", {
    image = "quay.io/raster-foundry/granary-api:${var.image_tag}"

    postgres_url      = "jdbc:postgresql://${data.terraform_remote_state.core.outputs.database_fqdn}/"
    postgres_name     = var.rds_database_name
    postgres_user     = data.terraform_remote_state.core.outputs.database_username
    postgres_password = data.terraform_remote_state.core.outputs.database_password

    granary_log_level    = var.api_log_level
    granary_tracing_sink = var.api_tracing_sink

    papertrail_endpoint = data.terraform_remote_state.core.outputs.papertrail_endpoint

    project = var.project
  })
}

resource "aws_ecs_task_definition" "api_migrations" {
  family = "${var.project}APIMigrations"

  container_definitions = templatefile("${path.module}/task-definitions/api_migrations.json.tmpl", {
    image = "quay.io/raster-foundry/granary-api-migrations:${var.image_tag}"

    flyway_url      = "jdbc:postgresql://${data.terraform_remote_state.core.outputs.database_fqdn}/${var.rds_database_name}"
    flyway_user     = data.terraform_remote_state.core.outputs.database_username
    flyway_password = data.terraform_remote_state.core.outputs.database_password

    papertrail_endpoint = data.terraform_remote_state.core.outputs.papertrail_endpoint

    project = var.project
  })
}

resource "aws_ecs_service" "api" {
  name                               = "${var.project}API"
  cluster                            = data.terraform_remote_state.core.outputs.ecs_cluster_name
  task_definition                    = aws_ecs_task_definition.api.arn
  desired_count                      = var.desired_count
  deployment_minimum_healthy_percent = var.deployment_min_percent
  deployment_maximum_percent         = var.deployment_max_percent
  iam_role                           = data.terraform_remote_state.core.outputs.ecs_service_role_name

  ordered_placement_strategy {
    type  = "spread"
    field = "attribute:ecs.availability-zone"
  }

  load_balancer {
    target_group_arn = aws_lb_target_group.api.arn
    container_name   = "api"
    container_port   = 8080
  }

  depends_on = [
    "aws_lb_listener.api",
  ]
}

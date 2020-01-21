#
# Security Group Resources
#
resource "aws_security_group" "alb" {
  name   = "sg${var.project}APILoadBalancer"
  vpc_id = var.vpc_id

  tags = {
    Name    = "sg${var.project}APILoadBalancer",
    Project = var.project
  }
}

resource "aws_security_group" "api" {
  name   = "sg${var.project}APIEcsService"
  vpc_id = var.vpc_id

  tags = {
    Name    = "sg${var.project}APIEcsService",
    Project = var.project
  }
}

#
# ALB Resources
#
resource "aws_lb" "api" {
  name            = "alb${var.project}API"
  security_groups = flatten([
      aws_security_group.alb.id,
      aws_security_group.alb_whitelist_ec2.*.id,
  ])
  subnets         = var.vpc_public_subnet_ids

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
  vpc_id   = var.vpc_id

  target_type = "ip"

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
  certificate_arn   = module.cert.arn

  default_action {
    target_group_arn = aws_lb_target_group.api.id
    type             = "forward"
  }
}

#
# ECS Resources
#
resource "aws_ecs_cluster" "api" {
  name = "ecs${var.project}Cluster"
}

resource "aws_ecs_task_definition" "api" {
  family = "${var.project}API"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.fargate_api_cpu
  memory                   = var.fargate_api_memory

  task_role_arn      = aws_iam_role.ecs_task_role.arn
  execution_role_arn = aws_iam_role.ecs_task_execution_role.arn

  container_definitions = templatefile("${path.module}/task-definitions/api.json.tmpl", {
    image = "quay.io/raster-foundry/granary-api:${var.image_tag}"

    api_host = "https://${var.r53_public_hosted_zone_record}/api"

    postgres_url      = "jdbc:postgresql://${var.rds_database_hostname}/"
    postgres_name     = var.rds_database_name
    postgres_user     = var.rds_database_username
    postgres_password = var.rds_database_password

    granary_log_level    = var.api_log_level
    granary_tracing_sink = var.api_tracing_sink
    granary_auth_enabled = var.granary_auth_enabled

    project = var.project
    aws_region = var.aws_region
  })
}

resource "aws_ecs_task_definition" "api_migrations" {
  family = "${var.project}APIMigrations"
  network_mode             = "awsvpc"
  requires_compatibilities = ["FARGATE"]
  cpu                      = var.fargate_api_migrations_cpu
  memory                   = var.fargate_api_migrations_memory

  execution_role_arn = aws_iam_role.ecs_task_execution_role.arn

  container_definitions = templatefile("${path.module}/task-definitions/api_migrations.json.tmpl", {
    image = "quay.io/raster-foundry/granary-api-migrations:${var.image_tag}"

    flyway_url      = "jdbc:postgresql://${var.rds_database_hostname}/${var.rds_database_name}"
    flyway_user     = var.rds_database_username
    flyway_password = var.rds_database_password

    project = var.project
    aws_region = var.aws_region
  })
}

resource "aws_ecs_service" "api" {
  name            = "${var.project}API"
  cluster         = aws_ecs_cluster.api.name
  task_definition = aws_ecs_task_definition.api.arn

  desired_count                      = var.desired_count
  deployment_minimum_healthy_percent = var.deployment_min_percent
  deployment_maximum_percent         = var.deployment_max_percent

  launch_type = "FARGATE"

  network_configuration {
    security_groups = [aws_security_group.api.id]
    subnets         = var.vpc_private_subnet_ids
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

#
# CloudWatch Resources
#
resource "aws_cloudwatch_log_group" "api" {
  name              = "log${var.project}API"
  retention_in_days = 30
}

resource "aws_cloudwatch_log_group" "api_migrations" {
  name              = "log${var.project}APIMigrations"
  retention_in_days = 30
}

variable "project" {
  default = "Granary"
  type    = string
}

variable "environment" {
  default = "Production"
  type    = string
}

variable "aws_region" {
  default = "us-east-1"
  type    = string
}

variable "alb_ingress_cidr_blocks" {
  type    = list(string)
  default = ["0.0.0.0/0"]
}

variable "alb_ingress_ipv6_cidr_blocks" {
  type    = list(string)
  default = ["::/0"]
}

variable "r53_public_hosted_zone" {
  type = string
}

variable "vpc_id" {
  type = string
}

variable "vpc_private_subnet_ids" {
  type = list(string)
}

variable "vpc_public_subnet_ids" {
  type = list(string)
}

variable "rds_security_group_id" {
  type = string
}

variable "rds_database_hostname" {
  type = string
}

variable "rds_database_port" {
  default = 5432
  type    = number
}

variable "rds_database_username" {
  type = string
}

variable "rds_database_password" {
  type = string
}

variable "rds_database_name" {
  type = string
}

variable "fargate_api_cpu" {
  type = number
}

variable "fargate_api_memory" {
  type = number
}

variable "fargate_api_migrations_cpu" {
  type = number
}

variable "fargate_api_migrations_memory" {
  type = number
}

variable "api_log_level" {
  type = string
}

variable "api_tracing_sink" {
  type = string
}

variable "desired_count" {
  type = number
}

variable "deployment_min_percent" {
  type = number
}

variable "deployment_max_percent" {
  type = number
}

variable "image_tag" {
  type = string
}

variable "aws_ecs_task_execution_role_policy_arn" {
  default = "arn:aws:iam::aws:policy/service-role/AmazonECSTaskExecutionRolePolicy"
  type    = string
}

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

variable "rds_database_name" {
  type = string
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

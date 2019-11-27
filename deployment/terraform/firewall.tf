#
# RDS security group resources
#
resource "aws_security_group_rule" "postgresql_container_instance_ingress" {
  type      = "ingress"
  from_port = 5432
  to_port   = 5432
  protocol  = "tcp"

  security_group_id        = var.rds_security_group_id
  source_security_group_id = aws_security_group.api.id
}

resource "aws_security_group_rule" "postgresql_container_instance_egress" {
  type      = "egress"
  from_port = 5432
  to_port   = 5432
  protocol  = "tcp"

  security_group_id        = var.rds_security_group_id
  source_security_group_id = aws_security_group.api.id
}

#
# API ALB security group resources
#
resource "aws_security_group_rule" "alb_http_ingress" {
  type             = "ingress"
  from_port        = 80
  to_port          = 80
  protocol         = "tcp"
  cidr_blocks      = var.alb_ingress_cidr_blocks
  ipv6_cidr_blocks = var.alb_ingress_ipv6_cidr_blocks

  security_group_id = aws_security_group.alb.id
}

resource "aws_security_group_rule" "alb_https_ingress" {
  type             = "ingress"
  from_port        = 443
  to_port          = 443
  protocol         = "tcp"
  cidr_blocks      = var.alb_ingress_cidr_blocks
  ipv6_cidr_blocks = var.alb_ingress_ipv6_cidr_blocks

  security_group_id = aws_security_group.alb.id
}

resource "aws_security_group_rule" "alb_container_instance_egress" {
  type      = "egress"
  from_port = 0
  to_port   = 65535
  protocol  = "tcp"

  security_group_id        = aws_security_group.alb.id
  source_security_group_id = aws_security_group.api.id
}

#
# Container instance security group resources
#
resource "aws_security_group_rule" "container_instance_https_egress" {
  type        = "egress"
  from_port   = 443
  to_port     = 443
  protocol    = "tcp"
  cidr_blocks = ["0.0.0.0/0"]

  security_group_id = aws_security_group.api.id
}

resource "aws_security_group_rule" "container_instance_postgresql_egress" {
  type      = "egress"
  from_port = 5432
  to_port   = 5432
  protocol  = "tcp"

  security_group_id        = aws_security_group.api.id
  source_security_group_id = var.rds_security_group_id
}

resource "aws_security_group_rule" "container_instance_alb_all_ingress" {
  type      = "ingress"
  from_port = 0
  to_port   = 65535
  protocol  = "tcp"

  security_group_id        = aws_security_group.api.id
  source_security_group_id = aws_security_group.alb.id
}

data "aws_ip_ranges" "ec2" {
  regions  = ["${var.aws_region}"]
  services = ["ec2"]
}

locals {
  ec2_cidr_block_chunks = "${chunklist(data.aws_ip_ranges.ec2.cidr_blocks, 40)}"
}

resource "aws_security_group" "alb_whitelist_ec2" {
  vpc_id = "${var.vpc_id}"

  egress {
    from_port       = 0
    to_port         = 65535
    protocol        = "tcp"
    security_groups = ["${module.container_service_cluster.container_instance_security_group_id}"]
  }

  tags {
    Name        = "sgAPIServerLoadBalancer"
    Project     = "${var.project}"
    Environment = "${var.environment}"
  }
}

resource "aws_security_group_rule" "alb_api_server_ec2_https_ingress" {
  count = "${length(local.ec2_cidr_block_chunks)}"

  type              = "ingress"
  from_port         = 443
  to_port           = 443
  protocol          = "tcp"
  cidr_blocks       = ["${local.ec2_cidr_block_chunks[count.index]}"]
  security_group_id = "${aws_security_group.alb_whitelist_ec2.*.id[count.index]}"
}

resource "aws_security_group_rule" "alb_api_server_container_instance_all_egress" {
  type      = "egress"
  from_port = 0
  to_port   = 65535
  protocol  = "tcp"

  security_group_id        = "${aws_security_group.alb.id}"
  source_security_group_id = "${module.container_service_cluster.container_instance_security_group_id}"
}

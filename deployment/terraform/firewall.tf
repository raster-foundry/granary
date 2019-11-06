#
# API ALB security group resources
#
resource "aws_security_group_rule" "alb_http_ingress" {
  type             = "ingress"
  from_port        = 80
  to_port          = 80
  protocol         = "tcp"
  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]

  security_group_id = aws_security_group.alb.id
}

resource "aws_security_group_rule" "alb_https_ingress" {
  type             = "ingress"
  from_port        = 443
  to_port          = 443
  protocol         = "tcp"
  cidr_blocks      = ["0.0.0.0/0"]
  ipv6_cidr_blocks = ["::/0"]

  security_group_id = aws_security_group.alb.id
}

resource "aws_security_group_rule" "alb_container_instance_egress" {
  type      = "egress"
  from_port = 0
  to_port   = 65535
  protocol  = "tcp"

  security_group_id        = aws_security_group.alb.id
  source_security_group_id = data.terraform_remote_state.core.outputs.container_instance_security_group_id
}

#
# Container instance security group resources
#
resource "aws_security_group_rule" "container_instance_alb_ingress" {
  type      = "ingress"
  from_port = 0
  to_port   = 65535
  protocol  = "tcp"

  security_group_id        = data.terraform_remote_state.core.outputs.container_instance_security_group_id
  source_security_group_id = aws_security_group.alb.id
}

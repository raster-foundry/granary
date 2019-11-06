#
# Public DNS resources
#
resource "aws_route53_record" "api" {
  zone_id = data.terraform_remote_state.core.outputs.route53_public_hosted_zone_id
  name    = "granary.${data.terraform_remote_state.core.outputs.route53_public_hosted_zone_name}"
  type    = "A"

  alias {
    name                   = aws_lb.api.dns_name
    zone_id                = aws_lb.api.zone_id
    evaluate_target_health = true
  }
}

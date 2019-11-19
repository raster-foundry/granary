#
# Public DNS resources
#
data "aws_route53_zone" "external" {
  name = var.r53_public_hosted_zone
}

resource "aws_route53_record" "api" {
  zone_id = data.aws_route53_zone.external.zone_id
  name    = "granary.${var.r53_public_hosted_zone}"
  type    = "A"

  alias {
    name                   = aws_lb.api.dns_name
    zone_id                = aws_lb.api.zone_id
    evaluate_target_health = true
  }
}

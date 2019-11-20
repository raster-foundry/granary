#
# ACM resources
#
module "cert" {
  source = "github.com/azavea/terraform-aws-acm-certificate?ref=marioapbrito"

  domain_name               = aws_route53_record.api.name
  subject_alternative_names = ["*.${aws_route53_record.api.name}"]
  hosted_zone_id            = data.aws_route53_zone.external.zone_id
  validation_record_ttl     = 60
}

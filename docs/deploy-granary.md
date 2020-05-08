---
title: Deploy Granary alongside an existing application
id: deploy-granary
---

This tutorial will guide you through adding Granary to existing infrastructure
that includes an RDS instance. This step will require
[Terraform](https://learn.hashicorp.com/terraform/getting-started/install.html)
and a clone of the [Granary repository](https://github.com/raster-foundry/granary/).

## Configuring the deployment

Granary is not designed to be deployed as a stand-alone application.
Instead, it assumes that you have some other application that needs
a job runner. With that in mind, you'll need to fill in some
AWS configuration based on values from your already deployed application.

The values you'll need to fill in are:

- [`aws_key_name`](#aws-key-name)
- [`r53_public_hosted_zone`](#r53-public-hosted-zone)
- [`r53_public_hosted_zone_record`](#r53-public-hosted-zone-record)
- [`vpc_id`](#vpc-id)
- [`vpc_private_subnet_ids`](#vpc-private-subnet-ids)
- [`vpc_public_subnet_ids`](#vpc-public-subnet-ids)
- [`rds_security_group_id`](#rds-security-group-id)
- [`rds_database_hostname`](#rds-database-hostname)
- [`rds_database_username`](#rds-database-username)
- [`rds_database_password`](#rds-database-password)
- [`rds_database_name`](#rds-database-name)
- [`project`](#project) if you don't want the name to be `GranaryDemo`
- [`aws_region`](#aws-region) if you don't want to deploy in `us-east-1`

A complete Terraform variables file containing the variables to be filled in
and other necessary variables is shown below. Descriptions of each variable mentioned
above can be found below the Terraform variables file. Copy the variables shown here
into `deployment/terraform/variables.tf` in your Clone of the Granary repository,
filling in appropriate values for the variables listed above.

```terraform
project = "GranaryDemo"

environment = "Production"

aws_region = "us-east-1"

aws_key_name = ""

r53_public_hosted_zone = ""

r53_public_hosted_zone_record = ""

vpc_id = ""

vpc_private_subnet_ids = [
]

vpc_public_subnet_ids = [
]

rds_security_group_id = ""

rds_database_hostname = ""

rds_database_username = "granarydemo"

rds_database_password = ""

rds_database_name = "granarydemo"

batch_gpu_ce_instance_types = [
  "p3 family",
  "c5d family"
]

alb_ingress_cidr_blocks = ["0.0.0.0/0"]

alb_ingress_ipv6_cidr_blocks = []

fargate_api_cpu = "512"

fargate_api_memory = "1024"

fargate_api_migrations_cpu = "256"

fargate_api_migrations_memory = "512"

api_log_level = "info"

api_tracing_sink = "xray"

desired_count = 2

deployment_min_percent = 100

deployment_max_percent = 200

batch_gpu_container_instance_volume_size = 30

batch_gpu_ce_desired_vcpus = "0"

batch_gpu_ce_min_vcpus = "0"

batch_gpu_ce_max_vcpus = "128"

batch_gpu_ce_spot_fleet_bid_precentage = "60"
```

### `aws_key_name`
### `project`
### `aws_region`
### `r53_public_hosted_zone`
### `r53_public_hosted_zone_record`
### `vpc_id`
### `vpc_private_subnet_ids`
### `vpc_public_subnet_ids`
### `rds_security_group_id`
### `rds_database_hostname`
### `rds_database_username`
### `rds_database_password`
### `rds_database_name`

## Deploying your Granary service

With variables filled in, the next step is to create resources for your
Granary service. We'll deploy resources using three simple terraform
steps:

- [`init`](#init) -- This step makes sure the Terraform configuration is syntactically
  valid and has all the variables it needs.
- [`plan`](#plan) -- This step creates a graph of all of the resources to be created
  and their dependencies on each other.
- [`apply`](#apply) -- This step interacts with AWS to create the planned resources.

### `init`

### `plan`

### `apply`

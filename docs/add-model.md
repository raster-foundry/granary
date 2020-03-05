---
title: Add a new model
id: add-a-new-model
---

This tutorial will guide you through adding Granary to existing infrastructure
that includes an RDS instance and adding a new model to your deployed Granary
service.

## Deploying Granary alongside an existing RDS-backed service

## Adding a new model

### Create a job definition for your new model

With your running Granary instance now available, we add another model and kick
off some predictions. We'll use a public container image to create a job definition,
then create a model in Granary referring to that job definition. For this step, you'll
need to install the [`terraform`](https://www.terraform.io/downloads.html) CLI.

Create a new directory called `granary-models` and a directory in that directory
called `job-definitions`. In `granary-models/batch.tf`, add the following Terraform
configuration to describe the AWS Batch job definition that we'd like to create.

```terraform
resource "aws_batch_job_definition" "calculate_water" {
  name = "job${var.project}CalculateWater"
  type = "container"

  container_properties = templatefile("${path.module}/job-definitions/granary-water.json", {})
}
```

In `granary-models/config.tf`, add the following Terraform configuration that tells Terraform
what provider (you can mentally substitute "cloud service") and backend to use.
"persistenc

```terraform
provider "aws" {
  version = "~> 2.44.0"
  region  = var.aws_region
}

terraform {
  backend "s3" {
    region  = "us-east-1"
    encrypt = "true"
  }
}
```

In `granary-models/variables.tf`, add the following variables to tell Terraform what project name
to use to substitute in the names of resources and in what region to create and destroy AWS
resources.

```terraform
variable "project" {
  default = "GranaryDemo"
  type    = string
}

variable "aws_region" {
  default = "us-east-1"
  type    = string
}
```

You can change them if you'd like, but the rest of this tutorial will assume that you used
the default values.

That configuration refers to a template file in `job-definitions` that doesn't
exist yet. Put the following template in `granary-models/calculate-water.json`.

```json
{
    "image": "quay.io/raster-foundry/granary-calculate-water:eeec5da",
    "vcpus": 2,
    "memory": 2048,
    "command": [
        "Ref::RED_BAND",
        "Ref::GREEN_BAND",
        "Ref::OUTPUT_LOCATION",
        "Ref::WEBHOOK_URL"
    ],
    "volumes": [],
    "environment": [],
    "mountPoints": [],
    "privileged": false,
    "ulimits": [],
    "resourceRequirements": []
}
```

Finally, we'll create the job definition for the water model. From the `granary-models` directory,
initialize Terraform with:

```bash
$ terraform init \
    -backend-config="bucket=<an s3 bucket you can write to>" \
	-backed-config="key=terraform/granary-demo/state"
```

Initializing Terraform checks that your Terraform configuration in this directory is correct.

Next, check to make sure that Terraform is going to do what you want it to do:

```bash
$ terraform plan -out=tfplan
```

If the proposed resources look good to you (they should just be to create one new job
definition), follow up with

```bash
$ terraform apply -plan=tfplan
```

### Create your new model in Granary

This step will require the [`httpie`](https://httpie.org/doc#installation) command line HTTP
client.

With your `jobGranaryDemoCalculateWater` job definition now present in AWS Batch, you can
create a Granary model that refers to it. The JSON representation of the Granary model
we're going to create looks like this:

// TODO verify the job queue name resulting from the basic deploy

```json
{
    "$schema": "http://json-schema.org/draft-07/schema",
    "$id": "http://example.com/example.json",
    "type": "object",
    "title": "The Root Schema",
    "description": "The root schema comprises the entire JSON document.",
    "required": [
        "RED_BAND",
        "GREEN_BAND",
        "OUTPUT_LOCATION",
        "WEBHOOK_URL"
    ],
    "properties": {
        "RED_BAND": {
            "$id": "#/properties/RED_BAND",
            "type": "integer",
            "title": "The Red_band Schema",
            "description": "An explanation about the purpose of this instance.",
            "default": 0,
            "examples": [
                1
            ]
        },
        "GREEN_BAND": {
            "$id": "#/properties/GREEN_BAND",
            "type": "integer",
            "title": "The Green_band Schema",
            "description": "An explanation about the purpose of this instance.",
            "default": 0,
            "examples": [
                2
            ]
        },
        "OUTPUT_LOCATION": {
            "$id": "#/properties/OUTPUT_LOCATION",
            "type": "string",
            "title": "The Output_location Schema",
            "description": "An explanation about the purpose of this instance.",
            "default": "",
            "examples": [
                "s3://coolbucket/foo.tif"
            ]
        },
        "WEBHOOK_URL": {
            "$id": "#/properties/WEBHOOK_URL",
            "type": "string",
            "title": "The Webhook_url Schema",
            "description": "An explanation about the purpose of this instance.",
            "default": "",
            "examples": [
                "https://granary.rasterfoundry.com"
            ]
        }
    }
}
```

To create the model, save that json to `model.json`, then:

```bash
$ cat model.json | http https://granary.yourdomain.com/api/models
```

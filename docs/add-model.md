---
title: Add a new model
id: add-a-new-model
---

This tutorial will guide you through adding a new model to your deployed Granary
service.

## Create a job definition for your new model

With a running Granary instance available, we'll add another model and kick
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
exist yet. Let's create it! Put the following template in `granary-models/calculate-water.json`.

```json
{
    "image": "quay.io/raster-foundry/granary-calculate-water:eeec5da",
    "vcpus": 2,
    "memory": 2048,
    "command": [
        "Ref::NIR_BAND",
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

If the proposed resources look good to you (the only planned change should be to create one
new job definition), follow up with

```bash
$ terraform apply -plan=tfplan
```

## Create your new model in Granary

This step will require the [`httpie`](https://httpie.org/doc#installation) command line HTTP
client.

With your `jobGranaryDemoCalculateWater` job definition now present in AWS Batch, you can
create a Granary model that refers to it. The JSON representation of the Granary model
we're going to create looks like this:

```json
{
  "name": "Calculate Water",
  "validator": {
    "schema": {
      "$schema": "http://json-schema.org/draft-07/schema",
      "$id": "http://example.com/example.json",
      "type": "object",
      "title": "The Root Schema",
      "description": "The root schema comprises the entire JSON document.",
      "required": [
        "NIR_BAND",
        "GREEN_BAND",
        "OUTPUT_LOCATION"
      ],
      "properties": {
        "NIR_BAND": {
          "$id": "#/properties/NIR_BAND",
          "type": "string",
          "format": "uri",
          "title": "The NIR_band Schema",
          "description": "A URI pointing to data from a near infrared band",
          "examples": [
            "s3://cool-bucket/nir.tiff"
          ]
        },
        "GREEN_BAND": {
          "$id": "#/properties/GREEN_BAND",
          "type": "string",
          "format": "uri",
          "title": "The Green_band Schema",
          "description": "A URI pointing to data from a green band",
          "examples": [
            "s3://cool-bucket/green.tiff"
          ]
        },
        "OUTPUT_LOCATION": {
          "$id": "#/properties/OUTPUT_LOCATION",
          "type": "string",
          "format": "uri",
          "title": "The output_location Schema",
          "description": "A uri pointing to where to store the result of running this model",
          "default": "",
          "examples": [
            "s3://cool-bucket/foo.tif"
          ]
        }
      }
    }
  },
  "jobDefinition": "jobGranaryDemoCalculateWater",
  "jobQueue": "queueGranaryDemoGPU"
}
```

Note that the `WEBHOOK_URL` isn't present in the schema here. The reason for that is that the server
will create an ID for the webhook when predictions are created. Since there's no way for a user to
know that value in advance, the server fills it in and updates the parameters for the AWS Batch job
accordingly.

To create the model, save that json to `model.json`, then:

```bash
$ cat model.json | http https://granary.yourdomain.com/api/models
```

## Create a prediction for your model

In the last step, you created a model in your deployed Granary service. In this step, you'll
use that model to create a prediction. You'll also see what happens if you try to create a prediction
with arguments the model doesn't recognize or poorly formatted arguments. This step also requires
[`httpie`](https://httpie.org/doc#installation).

Creating a prediction is simpler than creating a model. Predictions require only two arguments to create:
a model ID and JSON of some arguments. Because of the `schema` of the model we created in the previous step,
our arguments must conform to the shape:

```json
{
    "NIR_BAND": "s3://foo/bar.tiff",
    "GREEN_BAND": "s3://foo/baz.tiff",
    "OUTPUT_LOCATION": "s3://this/could/be/any/writeable/uri.tif"
}
```

To create a prediction, we'll use the separated bands for an L2C Sentinel-2 image hosted on AWS:

```json
{
    "modelId": "id-of-the-model-you-created",
    "arguments": {
        "NIR_BAND": "s3://sentinel-s2-l2a/tiles/40/U/EB/2020/2/17/0/R20m/B05.jp2",
        "GREEN_BAND": "s3://sentinel-s2-l2a/tiles/40/U/EB/2020/2/17/0/R20m/B03.jp2",
        "OUTPUT_LOCATION": "s3://your-bucket/prefix/input.jp2"
    }
}
```

Finally, create the prediction:

```bash
$ cat prediction.json | http https://granary.yourdomain.com/api/predictions
```

If everything went well, you'll get a response telling you that the job has started.

Now let's make some things go wrong on purpose. One thing that AWS Batch will let you try to do is
create `SubmitJob` requests without arguments that the job definition requires. Let's try to do something
similar with the model from the first step. In `prediction.json`, let's make a simple typo and substitute
`GREN` for `GREEN`, so it now reads:

```json
{
    "modelId": "id-of-the-model-you-created",
    "arguments": {
        "NIR_BAND": "s3://sentinel-s2-l2a/tiles/40/U/EB/2020/2/17/0/R20m/B05.jp2",
        "GREN_BAND": "s3://sentinel-s2-l2a/tiles/40/U/EB/2020/2/17/0/R20m/B03.jp2",
        "OUTPUT_LOCATION": "s3://your-bucket/your/prefix/water-model-output.geojson"
    }
}
```

`POST`-ing that to the `predictions` endpoint, the server will helpfully tell you:

```json
{
    "msg": "#: required key [GREEN_BAND] not found"
}
```

Similarly, if you forget the correct format for the bands (for example, if you submit
integer band indices instead of URI pointers to separate bands), the server will
helpfully tell you:

```json
{
    "msg": "#: 2 schema violations found#/RED_BAND: expected type: String, found: Integer#/GREEN_BAND: expected type: String, found: Integer"
}
```

Mixtures of errors are similarly well handled, in case there's more than one problem with the
prediction's arguments and you don't want to fix one thing at a time:

```json
{
    "msg": "#: 2 schema violations found#: required key [GREEN_BAND] not found#/RED_BAND: expected type: String, found: Integer"
}
```

## Inspect the prediction

Eventually, the prediction should complete. You'll be able to tell it's done, because one of two things will
be the case when you hit `/api/predictions/<prediction id>/`

- its status will be `"SUCCESSFUL"` and it will have a value in its `outputLocation` field
- its status will be `"FAILED"` and it will have a value in its `statusReason` field

Because this is the demo model, it should be the first one. Inspect the `outputLocation` field to find the path
to the model's output, download it, and open it in QGIS. If you used the NIR and green bands from the example,
you'll see that there's not a lot of water predicted in this image. If you toss the geojson output into
[geojson.io](http://geojson.io) or [QGIS](https://www.qgis.org/en/site/), you can see that there's not too much
water on the Earth there either, though clearly the model is missing some though. However, model sophistication
is not the point of this example.

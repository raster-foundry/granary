---
title: Add a new model
id: add-a-new-model
---

This tutorial will guide you through adding a new model to your deployed Granary
service.

## Deploying Granary alongside an existing RDS-backed service

## Adding a new model

These three steps will explain how to create AWS resources for a new model, how to create
the new model in Granary, and how to submit a prediction for that new model.

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
  "jobQueue": "granaryDemoCpuJobQueue"
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

## Creating a prediction for your model

In the last step, you created a model in your deployed Granary service. In this step, you'll
use that model to create a prediction. You'll also see what happens if you try to create a prediction
with arguments the model doesn't recognize or poorly formatted arguments. This step also requires
[`httpie`](https://httpie.org/doc#installation).

### Create the `Prediction` in Granary

Creating a prediction is simpler than creating a model. Predictions require only two arguments to create:
a model ID and JSON of some arguments. Because of the `schema` of the model we created in the previous step,
our arguments must conform to the shape:

```json
{
    "NIR_BAND": "s3://foo/bar.tiff",
    "GREEN_BAND": "s3://foo/baz.tiff",
    "OUTPUT_LOCATION": "s3://this/could/be/any/uri.tif"
}
```

To create a prediction, we'll use the separated bands for a Landsat 8 image hosted on AWS. The

```json
{
    "modelId": "id-of-the-model-you-created",
    "arguments": {
        "NIR_BAND": "s3://landsat-pds/c1/L8/047/027/LC08_L1TP_047027_20200220_20200225_01_T1/LC08_L1TP_047027_20200220_20200225_01_T1_B5.TIF",
        "GREEN_BAND": "s3://landsat-pds/c1/L8/047/027/LC08_L1TP_047027_20200220_20200225_01_T1/LC08_L1TP_047027_20200220_20200225_01_T1_B3.TIF",
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
        "NIR_BAND": "s3://landsat-pds/c1/L8/047/027/LC08_L1TP_047027_20200220_20200225_01_T1/LC08_L1TP_047027_20200220_20200225_01_T1_B5.TIF",
        "GREN_BAND": "s3://landsat-pds/c1/L8/047/027/LC08_L1TP_047027_20200220_20200225_01_T1/LC08_L1TP_047027_20200220_20200225_01_T1_B3.TIF",
        "OUTPUT_LOCATION": "s3://your-bucket/your-prefix/output.tiff"
    }
}
```

`POST`-ing that to the `predictions` endpoint, the server will helpfully tell you:

```json
{
    "msg": "#: required key [GREEN_BAND] not found"
}
```

Similarly, if you forget the correct format for the bands (maybe you convince yourself they should
be band indices instead of pointers to separated bands of a multi-band tiff, which is _definitely_ not
a mistake I made while putting together this tutorial), the server will helpfully tell you:

```json
{
    "msg": "#: 2 schema violations found#/RED_BAND: expected type: String, found: Integer#/GREEN_BAND: expected type: String, found: Integer"
}
```

Mixtures of errors are similarly well handled, in case you're the sort of person who likes to make a few
kinds of mistakes at once (again, _definitely_ not something I did while putting together this
tutorial):

```json
{
    "msg": "#: 2 schema violations found#: required key [GREEN_BAND] not found#/RED_BAND: expected type: String, found: Integer"
}
```

### Inspecting the prediction

Eventually, the prediction should complete. You'll be able to tell it's done, because one of two things will
be the case when you hit `/api/predictions/<prediction id>/`

- its status will be `"SUCCESSFUL"` and it will have a value in its `outputLocation` field
- its status will be `"FAILED"` and it will have a value in its `statusReason` field

Because this is the demo model, it should be the first one.

...does this produce a tif? If a tif, talk through downloading it and opening it in QGIS. If it's like a count
or something or segmentation? I don't remember what this produces. But anyway make appropriate choices based on the
model output

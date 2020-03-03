---
title: Introduction
id: introduction
---

# What is `granary`?

Granary is a job runner for cloud-based geospatial machine learning.
Its goal is to simplify running models and to track and serve
the results of predictions. It puts a REST API between you and AWS Batch
to simplify interactions that otherwise involve repeatedly checking AWS SDK
documentation.

## Background info

To begin with, here are some IDs and imports that we'll be using throughout the examples.
They are present for consistency and compilation throughout the rest of this
document. You can ignore them.

```scala mdoc
import com.rasterfoundry.granary.datamodel._
import io.circe.syntax._
import java.time.Instant
import java.util.UUID

val modelId = UUID.fromString("@MODEL_ID@")
val predictionId = UUID.fromString("@PREDICTION_ID@")
val webhookId = UUID.fromString("@WEBHOOK_ID@")
val invocationTime = Instant.ofEpochMilli("@INVOCATION_TIME@".toLong)
```

The `modelId` is a faked ID that you can imagine represents a stored `Model`
in the database. The same is true for the `predictionId`, but for a `Prediction`.
The `webhookId` represents a single-use webhook that can called to indicate
the success or failure of a prediction.
The `invocationTime` is a pretend time that this prediction was
kicked off.

## What's a `Model`?

A model is a bundle of a human-readable name, some AWS Batch configuration,
and an argument validator. `model`s in Granary correspond to containers that
have been configured to run via AWS Batch job definitions. The major difference
between using Granary and hand rolling `SubmitJob` requests is the `Validator`.

To create a model, `POST` JSON like this to `/api/models`:

```scala mdoc
Model.Create(
  "A descriptive model name",
  Validator(().asJson), // a Validator -- more on this below
  "perfectAccuracyModel:1", // an AWS Batch job definition
  "veryExpensiveOnDemandQueue" // an AWS Batch job queue
).asJson.spaces2
```

If that's successful, here's what the response will look like:

```scala mdoc
Model(
  modelId,
  "A descriptive model name",
  Validator(().asJson),
  "perfectAccuracyModel:1",
  "veryExpensiveOnDemandQueue"
).asJson.spaces2
```

## What's a `Validator`?

A `Validator` uses [JSON Schema](http://json-schema.org/) Draft 7 to ensure that
when you try to run your job, you have the correct arguments. For example, in the
example above the `Validator` is the empty json object `{}`. This means that
providing any arguments will fail. That model is not a very useful model. We could
instead require a green band, a red band, and a GeoTIFF location with the following
JSON schema:

```json
{
  "definitions": {},
  "$schema": "http://json-schema.org/draft-07/schema#",
  "$id": "http://example.com/root.json",
  "type": "object",
  "title": "The Root Schema",
  "required": [
    "greenBand",
    "redBand",
    "tiffLocation"
  ],
  "properties": {
    "greenBand": {
      "$id": "#/properties/greenBand",
      "type": "integer",
      "title": "The Greenband Schema",
      "default": 0,
      "examples": [
        3
      ]
    },
    "redBand": {
      "$id": "#/properties/redBand",
      "type": "integer",
      "title": "The Redband Schema",
      "default": 0,
      "examples": [
        2
      ]
    },
    "tiffLocation": {
      "$id": "#/properties/tiffLocation",
      "type": "string",
      "title": "The Tifflocation Schema",
      "default": "",
      "examples": [
        "s3://cool-bucket/image.tiff"
      ],
      "format": "uri"
    }
  }
}
```

With this JSON schema attached to our model, it's impossible to create model runs
with improper or improperly formatted arguments.

Over time, your model's input requirements may change. In that case it makes
sense to make a new model with a different validator. Since writing JSON schema
by hand is tedious, it's helpful to use [`json-schema.net`](https://jsonschema.net/)
to generate schemas from examples, then edit those to match your needs.

## What's a `Prediction`?

A `Prediction` is a single run of a model with specific inputs. Predictions are
created when you `POST` a model id and arguments to `/api/predictions`.

```scala mdoc
Prediction.Create(
  modelId, // the modelId to associate with this prediction
  ().asJson // the arguments to pass to the model
).asJson.spaces2
```

The model's JSON schema is used to validate the prediction's arguments.
If validation passes, Granary will insert a record for this prediction and submit
a job to AWS Batch with the resources configured on the model. If that was successful,
you'll receive a response that looks like this:

```scala mdoc
Prediction(
  predictionId,
  modelId,
  invocationTime,
  ().asJson,
  JobStatus.Started,
  None,
  None,
  Some(webhookId)
).asJson.spaces2
```

The `webhookId` in the response points to a single-use webhook for updating the prediction.
This webhook can be accessed at `/api/predictions/{predictionId}/results/{webhookId}` and
accepts two kinds of messages:

```scala mdoc
// JSON of the message to send if the prediction failed
PredictionFailure("everything went wrong").asJson.spaces2

// JSON of the message to send if the prediction succeeded
PredictionSuccess("s3://where/the/results/live.json").asJson.spaces2
```

In the ideal case, the container for running the model in batch will submit results when it
is done or fails. This strategy will not cover cases in which the model cannot perform
error-handling though, for instance, `OutOfMemory` errors and cases in which a spot
instance gets cycled out from under your running model. Because the space of things that
can go wrong is nearly infinite, Granary itself doesn't provide any facilities for handling
those sorts of errors or for retrying predictions. Additionally, if your model has retrying
logic, it's your responsibility to make sure that it doesn't `POST` to the results webhook
until it has exhausted its retries, since the first `POST` to the webhook will make it
inaccessible for the rest of time.

# Can I just see some API docs?

Sure! Bring up the [dev environment](./development.md), then hit `localhost:8080/api/docs/docs.yaml`.

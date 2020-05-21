---
title: Introduction
id: introduction
---


```scala mdoc:invisible
import com.rasterfoundry.granary.datamodel._
import com.azavea.stac4s._
import io.circe.literal._
import io.circe.syntax._
import java.time.Instant
import java.util.UUID

val taskId = UUID.fromString("@TASK_ID@")
val predictionId = UUID.fromString("@PREDICTION_ID@")
val webhookId = UUID.fromString("@WEBHOOK_ID@")
val invocationTime = Instant.ofEpochMilli("@INVOCATION_TIME@".toLong)
val jsonSchema = json"""
{
    "$$schema": "http://json-schema.org/draft-07/schema",
    "$$id": "http://example.com/example.json",
    "type": "object",
    "title": "The Root Schema",
    "description": "The root schema comprises the entire JSON document.",
    "required": [
        "foo"
    ],
    "properties": {
        "foo": {
            "$$id": "#/properties/foo",
            "type": "integer",
            "title": "The Foo Schema",
            "description": "An explanation about the purpose of this instance.",
            "default": 0,
            "examples": [
                3
            ]
        }
    }
}
"""
val jsonPayload = json"""{"foo": 4}"""
```

## What is `granary`?

Granary is a job runner for cloud-based geospatial machine learning.
Its goal is to simplify running tasks and to track and serve
the results of predictions. It puts a REST API between you and AWS Batch
to simplify interactions that otherwise involve repeatedly checking AWS SDK
documentation. You can see an
[OpenAPI Spec](https://swagger.io/docs/specification/about/)
for Granary [here](https://granary.rasterfoundry.com/api/docs/docs.yaml).

![](/granary/img/granary-api.png)

## What's a `Task`?

A task is a bundle of a human-readable name, some AWS Batch configuration,
and an argument validator. `Task`s in Granary correspond to containers that
have been configured to run via AWS Batch job definitions. The major difference
between using Granary and hand rolling `SubmitJob` requests is the `Validator`.

To create a task, `POST` JSON like this to `/api/tasks`:

```scala mdoc:passthrough
println("```json")
println {
  Task.Create(
    "A descriptive task name",
    Validator(jsonSchema), // a Validator -- more on this below
    "perfectAccuracyTask:1", // an AWS Batch job definition
    "veryExpensiveOnDemandQueue" // an AWS Batch job queue
  ).asJson.spaces2
}
println("```")
```

If that's successful, here's what the response will look like:

```scala mdoc:passthrough
println("```json")
println {
  Task(
    taskId,
    "A descriptive task name",
    Validator(jsonSchema),
    "perfectAccuracyTask:1",
    "veryExpensiveOnDemandQueue"
  ).asJson.spaces2
}
println("```")
```

## What's a `Validator`?

A `Validator` uses [JSON Schema](http://json-schema.org/) Draft 7 to ensure that
when you try to run your job, you have the correct arguments. For example, in the
example above the `Validator` expects a JSON object with a key `foo` and some numeric
value. A more realistic (if more verbose) schema for geospatial applications is shown
below, requiring a green band, a red band, and a GeoTIFF location:

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

With this JSON schema attached to our task, it's impossible to create task runs
with improper or improperly formatted arguments.

Over time, your task's input requirements may change. In that case it makes
sense to make a new task with a different validator. Since writing JSON schema
by hand is tedious, it's helpful to use [`json-schema.net`](https://jsonschema.net/)
to generate schemas from examples, then edit those to match your needs.

## What's a `Prediction`?

A `Prediction` is a single run of a task with specific inputs. Predictions are
created when you `POST` a task id and arguments to `/api/predictions`.

```scala mdoc:passthrough
println("```json")
println {
  Prediction.Create(
    taskId, // the taskId to associate with this prediction
    jsonPayload, // the arguments to pass to the task
  ).asJson.spaces2
}
println("```")
```

The task's JSON schema is used to validate the prediction's arguments.
If validation passes, Granary will insert a record for this prediction and submit
a job to AWS Batch with the resources configured on the task. If that was successful,
you'll receive a response that looks like this:

```scala mdoc:passthrough
println("```json")
println {
  Prediction(
    predictionId,
    taskId,
    invocationTime,
    jsonPayload,
    JobStatus.Started,
    None,
    Nil,
    Some(webhookId)
  ).asJson.spaces2
}
println("```")
```

The `webhookId` in the response points to a single-use webhook for updating the prediction.
This webhook can be accessed at `/api/predictions/{predictionId}/results/{webhookId}` and
accepts two kinds of messages.

If the `prediction` failed, clients should send messages like this:

```scala mdoc:passthrough
// JSON of the message to send if the prediction failed
println("```json")
println {
  PredictionFailure("everything went wrong").asJson.spaces2
}
println("```")
```

If the `prediction` succeeded, clients should send messages like this:

```scala mdoc:passthrough
// JSON of the message to send if the prediction succeeded
println("```json")
println {
  PredictionSuccess(
    List(
      StacItemAsset(
        "s3://where/the/results/live.json",
  	    Some("Prediction Results"),
  	    Some("results for a very important prediction"),
  	    List(StacAssetRole.Data),
  	    Some(`application/json`)
  	  )
	)
  ).asJson.spaces2
}
println("```")
```

In the ideal case, the container for running the task in batch will submit results when it
is done or fails. This strategy will not cover cases in which the task cannot perform
error-handling though, for instance, `OutOfMemory` errors and cases in which a spot
instance gets cycled out from under your running task. Because the space of things that
can go wrong is nearly infinite, Granary itself doesn't provide any facilities for handling
those sorts of errors or for retrying predictions. Additionally, if your task has retrying
logic, it's your responsibility to make sure that it doesn't `POST` to the results webhook
until it has exhausted its retries, since the first `POST` to the webhook will make it
inaccessible for the rest of time.

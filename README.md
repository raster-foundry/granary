# granary [![CircleCI](https://circleci.com/gh/raster-foundry/granary.svg?style=svg)](https://circleci.com/gh/raster-foundry/granary) [![Docker Repository on Quay](https://quay.io/repository/raster-foundry/granary-api/status "Docker Repository on Quay")](https://quay.io/repository/raster-foundry/granary-api)
An API project that uses http4s and doobie

## Requirements

- `docker`
- `docker-compose`

## Quick Setup
```
./scripts/update
```

Then `ssh` into the machine when that is complete and start the server with `./scripts/server`. In another shell inside the VM you should be able to make a request:

`http :8080/api/hello/world`

## Developing

It's easier to test changes with the API with [HTTPie](https://httpie.org/) and the
[HTTPie JWT Auth plugin](https://github.com/teracyhq/httpie-jwt-auth) in order to make
authentication with APIs as seamless as possible. All following commands assume
you have those available.

To add a token for use with HTTPie, use `./scripts/dbshell` to insert a token like this:
`insert into tokens (id) values (uuid_generate_v4());`

### Nexus Repository Manager

When developing at the Azavea office, strongly consider use of the Nexus proxy. This is automatically configured in project setup. If you are not connected to the `vpn` you will need to disable it by deleting or moving `.sbtopts`.

### Project Organization

By default the backend is organized into 3 subprojects:
 - api: handles all routes, authentication, and services
 - datamodel: case classes for data the api operates on
 - database: handles database interaction with models

### Migrations
This project uses [flyway](https://flywaydb.org/) for migrations. The migrations are stored in the database subproject (`database/src/main/resources/db/migrations`). For information on the naming and formatting conventions consult the [documentation](https://flywaydb.org/documentation/migrations#naming). Each migration should be `sql` and generally follows the format of `V<number>__<description>.aql`.

Running migrations and other tasks are managed through `./scripts/migrate`.

### Development workflow
Usually at least two terminals should be open for doing development. The first terminal is where `sbt` should be run (`./scripts/console sbt`). In this terminal tests can be run, projects compiled, and the server assembled.

The other terminal is where the server should be run `./scripts/server` or other one-off commands. To see changes you made to the API live you will need to first `assemble` the `jar` for the `api` server.

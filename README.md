# granary [![CircleCI](https://circleci.com/gh/raster-foundry/granary.svg?style=svg)](https://circleci.com/gh/raster-foundry/granary) [![Docker Repository on Quay](https://quay.io/repository/raster-foundry/granary-api/status "Docker Repository on Quay")](https://quay.io/repository/raster-foundry/granary-api) [![Scala Steward badge](https://img.shields.io/badge/Scala_Steward-helping-blue.svg?style=flat&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAA4AAAAQCAMAAAARSr4IAAAAVFBMVEUAAACHjojlOy5NWlrKzcYRKjGFjIbp293YycuLa3pYY2LSqql4f3pCUFTgSjNodYRmcXUsPD/NTTbjRS+2jomhgnzNc223cGvZS0HaSD0XLjbaSjElhIr+AAAAAXRSTlMAQObYZgAAAHlJREFUCNdNyosOwyAIhWHAQS1Vt7a77/3fcxxdmv0xwmckutAR1nkm4ggbyEcg/wWmlGLDAA3oL50xi6fk5ffZ3E2E3QfZDCcCN2YtbEWZt+Drc6u6rlqv7Uk0LdKqqr5rk2UCRXOk0vmQKGfc94nOJyQjouF9H/wCc9gECEYfONoAAAAASUVORK5CYII=)](https://scala-steward.org)
An API project that uses http4s and doobie

**UNMAINTAINED**: This application is not currently being maintained. Its dependencies and code are more or less frozen at whatever was auto-mergeable up to 2021/04/16.

## Requirements

- `docker`
- `docker-compose`

## Quick Setup
```
nvm use
./scripts/setup
./scripts/update
```

Then start the server with `[AWS_PROFILE=<profile>] [AWS_REGION=<region>] ./scripts/server`.
You should then be able to hit `localhost:8080` in a browser.

## Developing

### API development

It's easier to test changes with the API with [HTTPie](https://httpie.org/) and the
[HTTPie JWT Auth plugin](https://github.com/teracyhq/httpie-jwt-auth) in order to make
authentication with APIs as seamless as possible. All following commands assume
you have those available.

To add a token for use with HTTPie, use `./scripts/dbshell` to insert a token like this:
`insert into tokens (id) values (uuid_generate_v4());`

#### Nexus Repository Manager

When developing at the Azavea office, strongly consider use of the Nexus proxy. This is automatically configured in project setup. If you are not connected to the `vpn` you will need to disable it by deleting or moving `.sbtopts`.

#### Project Organization

By default the backend is organized into 3 subprojects:
 - api: handles all routes, authentication, and services
 - datamodel: case classes for data the api operates on
 - database: handles database interaction with models

#### Migrations
This project uses [flyway](https://flywaydb.org/) for migrations. The migrations are stored in the database subproject (`database/src/main/resources/db/migrations`). For information on the naming and formatting conventions consult the [documentation](https://flywaydb.org/documentation/migrations#naming). Each migration should be `sql` and generally follows the format of `V<number>__<description>.aql`.

Running migrations and other tasks are managed through `./scripts/migrate`.

#### Development workflow
Usually at least two terminals should be open for doing development. The first terminal is where `sbt` should be run (`./scripts/console sbt`). In this terminal tests can be run, projects compiled, and the server assembled.

The other terminal is where the server should be run `./scripts/server` or other one-off commands. To see changes you made to the API live you will need to first `assemble` the `jar` for the `api` server.

### UI Development

You can find files for the user interface under [`granary-ui/`](./granary-ui). To get set up with Elm, you'll
want to make sure a few things are on your path. If you followed the [Quick Setup](#quick-setup) instructions, you have these already.

- [`elm-format`](https://github.com/avh4/elm-format)
- [`elm`](https://guide.elm-lang.org/install/elm.html)

Currently, the entire UI application lives in a single file. The reason for that is the note under
[Culture Shock](https://guide.elm-lang.org/webapps/structure.html) in the Elm guide, specifically:

> In JavaScript, the longer your file is, the more likely you have some sneaky mutation that will cause a really difficult bug. But in Elm, that is not possible! Your file can be 2000 lines long and that still cannot happen.

That may change as we separate pages.

#### First steps in this app

Like any of our recent frontend applications, start with `nvm use`. Whenever you make changes that you'd
like to see served, run `./scripts/update --frontend`. This will:

- recompile the application if that somehow hasn't already happened via your editor
- build the `index.html` page to serve
- copy everything in `granary-ui/public` into the magic directory under `api/src/main/resources/`.

Hot reloading is disabled because we're serving the application from Http4s instead of with Webpack,
but you don't have to restart the server when you make changes and compilation is _really_ fast, so
it's really not so bad.

#### Editor setup

There are a number of good editor integrations available for Elm. The one I'm
most used to is the [VSCode plugin](https://marketplace.visualstudio.com/items?itemName=Elmtooling.elm-ls-vscode), which
uses the Language Server Protocol and therefore feels very familiar to Scala/TypeScript development in VSCode.

If you don't want to use VSCode, there are a number of other plugins listed [here](https://github.com/elm/editor-plugins).
Anecdotally, the community seems really to like the IntelliJ plugin. I think @pcaisse has had some luck with the Vim plugin.
I've had some luck with the emacs plugin in my old Spacemacs days, but it's been a while so I have no idea what that's like
at this point.

You may end up in an annoying situation where the VSCode plugin and `nvm` stop getting along and VSCode can't find
your elm binaries. In that case, you can specify the path to `elm-format` and `elm` in the settings:

- press `Ctrl + ,`
- search `elm`
- use `which foo` to find the path that `nvm` knows about and fill it in in the appropriate box

#### Adding a dependency

Elm dependencies are fully managed. If you want to add one, use
`elm-install <maintainer>/<package-name>`. For example, to install the 
`elm-ui-framework` pacakge, you'd enter:

```bash
$ elm install Orasund/elm-ui-framework
```

You'll then be prompted to confirm that Elm's plan is a good one, then you're
good to go.

#### Elm getting started

If you'd like a quick introduction to Elm, the [Architecture](https://guide.elm-lang.org/architecture/)
and [Types](https://guide.elm-lang.org/types/) sections of the guide should be
helpful. They'll at least help you get used to what the syntax looks like.
If you'd like to learn about the Elm runtime, the [Commands and Subscriptions](https://guide.elm-lang.org/effects/)
section is a good guide.

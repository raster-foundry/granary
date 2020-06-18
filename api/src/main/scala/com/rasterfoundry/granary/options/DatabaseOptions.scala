package com.rasterfoundry.granary.api.options

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.monovore.decline._
import com.monovore.decline.refined._
import doobie.implicits._
import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.numeric.PosInt
import eu.timepit.refined.types.string.NonEmptyString
import com.rasterfoundry.granary.database.{Config, DatabaseConfig}

trait DatabaseOptions {

  private val driverOpt: Opts[NonEmptyString] = Opts
    .option[NonEmptyString]("db-driver", help = "Driver to use to connect to database")
    .withDefault(refineMV("org.postgresql.Driver"))

  private val jdbcUrlHelp =
    "Url to a running PostgreSQL database without database name (use --db-name to specify a name)"

  private val jdbcUrl: Opts[NonEmptyString] =
    Opts.option[NonEmptyString]("db-url", help = jdbcUrlHelp) orElse Opts.env[NonEmptyString](
      "POSTGRES_URL",
      help = jdbcUrlHelp
    ) withDefault (refineMV("jdbc:postgresql://database.service.internal"))

  private val databaseNameHelp = "Name of the database to connect to"

  private val databaseName: Opts[String] =
    (Opts.option[NonEmptyString]("db-name", help = databaseNameHelp) orElse Opts
      .env[NonEmptyString]("POSTGRES_NAME", help = databaseNameHelp)) map { _.value } withDefault (
      "granary"
    )

  private val databaseUserHelp = "Username to use when connecting to the database"

  private val databaseUser: Opts[NonEmptyString] =
    Opts.option[NonEmptyString]("db-user", help = databaseUserHelp) orElse Opts
      .env[NonEmptyString]("POSTGRES_USER", help = databaseUserHelp) withDefault (refineMV(
      "granary"
    ))

  private val databasePasswordHelp = "Password to use when connecting to the database"

  private val databasePassword: Opts[NonEmptyString] =
    Opts.option[NonEmptyString]("db-password", help = databasePasswordHelp) orElse Opts
      .env[NonEmptyString]("POSTGRES_PASSWORD", help = databasePasswordHelp) withDefault (refineMV(
      "granary"
    ))

  private val databaseStatementTimeoutHelp = "Maximum runtime for database statements"

  private val databaseStatementTimeout: Opts[PosInt] =
    Opts.option[PosInt]("db-statement-timeout", help = databaseStatementTimeoutHelp) orElse Opts
      .env[PosInt](
        "POSTGRES_STATEMENT_TIMEOUT",
        help = databaseStatementTimeoutHelp
      ) withDefault (refineMV(30000))

  private val databasePoolSizeHelp = "Size of the database connection pool"

  private val databasePoolSize: Opts[PosInt] =
    Opts.option[PosInt]("db-pool-size", help = databasePoolSizeHelp) orElse Opts.env[PosInt](
      "POSTGRES_DB_POOL_SIZE",
      help = databasePoolSizeHelp
    ) withDefault (refineMV(5))

  def databaseConfig(implicit
      contextShift: ContextShift[IO]
  ): Opts[DatabaseConfig] =
    (
      driverOpt,
      jdbcUrl,
      databaseName,
      databaseUser,
      databasePassword,
      databaseStatementTimeout,
      databasePoolSize
    ).mapN(
      DatabaseConfig.apply
    ).validate(
      "Unable to connect to database. Please ensure database is configured and listening on selected port"
    )((config: DatabaseConfig) => {
      val xa = Config.nonHikariTransactor[IO](config)
      fr"SELECT 1".query[Int].unique.transact(xa).attempt.unsafeRunSync match {
        case Right(_) => true
        case _        => false
      }
    })
}

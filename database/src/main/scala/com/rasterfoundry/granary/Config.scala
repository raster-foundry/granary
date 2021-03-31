package com.rasterfoundry.granary.database

import cats.effect.Async
import com.zaxxer.hikari.HikariConfig
import doobie.Transactor
import eu.timepit.refined._
import eu.timepit.refined.auto._
import eu.timepit.refined.types.string.NonEmptyString
import eu.timepit.refined.types.numeric.PosInt
import scala.util.Properties

case class DatabaseConfig(
    driver: NonEmptyString = refineMV("org.postgresql.Driver"),
    connectionUrl: NonEmptyString = refineMV("jdbc:postgresql://database.service.internal/"),
    databaseName: String = "granary",
    databaseUser: NonEmptyString = refineMV("granary"),
    databasePassword: NonEmptyString = refineMV("granary"),
    statementTimeout: PosInt = refineMV(30000),
    maximumPoolSize: PosInt = refineMV(5)
)

object Config {

  def nonHikariTransactor[F[_]: Async](
      conf: DatabaseConfig
  ): Transactor[F] =
    Transactor.fromDriverManager[F](
      conf.driver,
      conf.connectionUrl.value + conf.databaseName,
      conf.databaseUser,
      conf.databasePassword
    )

  private[database] def nonHikariTransactor[F[_]: Async](
      dbName: String
  ): Transactor[F] =
    nonHikariTransactor[F](DatabaseConfig(databaseName = dbName))

  def hikariConfig(conf: DatabaseConfig): HikariConfig = {
    val hikariConfig = new HikariConfig()
    hikariConfig.setPoolName("granary-pool")
    hikariConfig.setMaximumPoolSize(conf.maximumPoolSize)
    hikariConfig.setConnectionInitSql(s"SET statement_timeout = ${conf.statementTimeout.value};")
    hikariConfig.setJdbcUrl(conf.connectionUrl.value + conf.databaseName)
    hikariConfig.setUsername(conf.databaseUser)
    hikariConfig.setPassword(conf.databasePassword)
    hikariConfig.setDriverClassName(conf.driver)
    hikariConfig
  }

  val environment = Properties.envOrElse("ENVIRONMENT", "development")
}

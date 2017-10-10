package com.example.reservation.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator
import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.example.reservation.api.ReservationService
import com.example.reservation.impl.crud.{ReservationDao, ReservationPublisher}
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaComponents
import com.lightbend.lagom.scaladsl.persistence.jdbc.JdbcPersistenceComponents
import com.lightbend.lagom.scaladsl.playjson.EmptyJsonSerializerRegistry
import com.softwaremill.macwire._
import play.api.LoggerConfigurator
import play.api.db.HikariCPComponents
import play.api.db.slick.{DefaultSlickApi, SlickApi}

class ReservationLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new ReservationApplication(context) {
      override def serviceLocator: ServiceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new ReservationApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[ReservationService])
}

abstract class ReservationApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with JdbcPersistenceComponents
    with LagomKafkaComponents
    with HikariCPComponents
    with AhcWSComponents {

  LoggerConfigurator(environment.classLoader).foreach(_.configure(environment))

  lazy val reservationPublisher = wire[ReservationPublisher]

  // Defined rather than using SlickComponents because of https://github.com/playframework/play-slick/issues/422
  lazy val slickApi: SlickApi = wire[DefaultSlickApi]

  lazy val reservationDao = wire[ReservationDao]

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[ReservationService](wire[ReservationServiceImpl])

  persistentEntityRegistry.register(wire[ReservationEntity])

  // Register the JSON serializer registry
  override lazy val jsonSerializerRegistry = ReservationSerializerRegistry

}

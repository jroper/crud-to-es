package com.example.search.impl

import com.lightbend.lagom.scaladsl.api.ServiceLocator.NoServiceLocator
import com.lightbend.lagom.scaladsl.server._
import com.lightbend.lagom.scaladsl.devmode.LagomDevModeComponents
import play.api.libs.ws.ahc.AhcWSComponents
import com.example.search.api.SearchService
import com.example.reservation.api.ReservationService
import com.lightbend.lagom.scaladsl.broker.kafka.LagomKafkaClientComponents
import com.softwaremill.macwire._
import play.api.LoggerConfigurator

class SearchLoader extends LagomApplicationLoader {

  override def load(context: LagomApplicationContext): LagomApplication =
    new SearchApplication(context) {
      override def serviceLocator = NoServiceLocator
    }

  override def loadDevMode(context: LagomApplicationContext): LagomApplication =
    new SearchApplication(context) with LagomDevModeComponents

  override def describeService = Some(readDescriptor[SearchService])
}

abstract class SearchApplication(context: LagomApplicationContext)
  extends LagomApplication(context)
    with LagomKafkaClientComponents
    with AhcWSComponents {

  LoggerConfigurator(environment.classLoader).foreach(_.configure(environment))

  // Bind the service that this server provides
  override lazy val lagomServer = serverFor[SearchService](wire[SearchServiceImpl])

  lazy val searchService = serviceClient.implement[ReservationService]
}

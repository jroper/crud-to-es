package com.example.reservation.api

import java.time.LocalDate
import java.util.UUID

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.broker.Topic
import com.lightbend.lagom.scaladsl.api.broker.kafka.{KafkaProperties, PartitionKeyStrategy}
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}

object ReservationService  {
  val TopicName = "reservations"
}

/**
  * The reservation service interface.
  * <p>
  * This describes everything that Lagom needs to know about how to serve and
  * consume the ReservationService.
  */
trait ReservationService extends Service {

  def reserve(listingId: UUID): ServiceCall[Reservation, ReservationAdded]
  def getCurrentReservations(listingId: UUID): ServiceCall[NotUsed, Seq[Reservation]]

  def reservationEvents: Topic[ReservationAdded]

  override final def descriptor = {
    import Service._
    // @formatter:off
    named("reservation")
      .withCalls(
        restCall(Method.POST, "/api/listing/:id/reservation", reserve _),
        restCall(Method.GET, "/api/listing/:id/reservations", getCurrentReservations _)
      )
      .withTopics(
        topic(ReservationService.TopicName, reservationEvents _)
          .addProperty(
            KafkaProperties.partitionKeyStrategy,
            PartitionKeyStrategy[ReservationAdded](_.listingId.toString)
          )
      )
      .withAutoAcl(true)
    // @formatter:on
  }
}

case class Reservation(checkin: LocalDate, checkout: LocalDate) {
  def conflictsWith(other: Reservation): Boolean = {
    if (checkout.isBefore(other.checkin) || checkout == other.checkin) {
      false
    } else if (checkin.isAfter(other.checkout) || checkin == other.checkout) {
      false
    } else {
      true
    }

  }
}

object Reservation {
  implicit val format: Format[Reservation] = Json.format
}

case class ReservationAdded(listingId: UUID, reservationId: UUID, reservation: Reservation)

object ReservationAdded {
  implicit val format: Format[ReservationAdded] = Json.format
}


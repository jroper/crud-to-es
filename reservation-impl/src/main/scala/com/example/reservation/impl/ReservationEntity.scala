package com.example.reservation.impl

import java.time.LocalDate
import java.time.chrono.ChronoLocalDate
import java.util.UUID

import com.example.reservation.api.Reservation
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import com.lightbend.lagom.scaladsl.persistence.{AggregateEvent, AggregateEventTag, PersistentEntity}
import com.lightbend.lagom.scaladsl.persistence.PersistentEntity.ReplyType
import com.lightbend.lagom.scaladsl.playjson.{JsonSerializer, JsonSerializerRegistry}
import play.api.libs.json._

import scala.collection.immutable.Seq

sealed trait ReservationEvent extends AggregateEvent[ReservationEvent] {
  def aggregateTag = ReservationEvent.Tag
}

object ReservationEvent {
  val Tag = AggregateEventTag[ReservationEvent]
}

case class ReservationAdded(
  reservationId: UUID,
  listingId: UUID,
  reservation: Reservation
) extends ReservationEvent

object ReservationAdded {
  implicit val format: Format[ReservationAdded] = Json.format
}

sealed trait ReservationCommand[R] extends ReplyType[R]

case class AddReservation(
  reservation: Reservation
) extends ReservationCommand[ReservationAdded]

object AddReservation {
  implicit val format: Format[AddReservation] = Json.format
}

case object GetCurrentReservations extends ReservationCommand[Seq[Reservation]] {
  implicit val format: Format[GetCurrentReservations.type] =
    Format(Reads(_ => JsSuccess(GetCurrentReservations)), Writes(_ => JsString("get")))
}

case class ReservationState(reservations: Seq[Reservation])

object ReservationState {
  implicit val format: Format[ReservationState] = Json.format
  val empty = ReservationState(Nil)
}

object ReservationSerializerRegistry extends JsonSerializerRegistry {
  override def serializers: Seq[JsonSerializer[_]] = Seq(
    JsonSerializer[ReservationAdded],
    JsonSerializer[AddReservation],
    JsonSerializer[GetCurrentReservations.type],
    JsonSerializer[ReservationState]
  )
}

class ReservationEntity extends PersistentEntity {

  override type Command = ReservationCommand[_]
  override type Event = ReservationEvent
  override type State = ReservationState

  private lazy val listingId = UUID.fromString(entityId)

  override def initialState: ReservationState = ReservationState.empty

  override def behavior: Behavior = {
    case ReservationState(reservations) =>
      Actions().onCommand[AddReservation, ReservationAdded] {

        case (AddReservation(reservation), ctx, _) =>
          // Validate start/end dates
          val now = LocalDate.now()
          if (reservation.checkout.isBefore(reservation.checkin) || reservation.checkout == reservation.checkin) {
            ctx.commandFailed(BadRequest("Checkout date must be after checkin date"))
            ctx.done
          } else if (reservation.checkin.isBefore(now)) {
            ctx.commandFailed(BadRequest("Cannot make a reservation for the past"))
            ctx.done
            // Check that it doesn't overlap with any existing reservations
          } else if (reservations.exists(_.conflictsWith(reservation))) {
            ctx.commandFailed(BadRequest("Listing is already booked for those dates"))
            ctx.done
          } else {
            ctx.thenPersist(ReservationAdded(UUID.randomUUID(), listingId, reservation))(ctx.reply)
          }
      }.onReadOnlyCommand[GetCurrentReservations.type, Seq[Reservation]] {
        case (GetCurrentReservations, ctx, _) =>
          val now = LocalDate.now()
          ctx.reply(reservations.dropWhile(_.checkout.isBefore(now)))
      }.onEvent {
        case (ReservationAdded(_, _, reservation), state) =>
          ReservationState((reservations :+ reservation)
            .sortBy(_.checkin.asInstanceOf[ChronoLocalDate]))
      }

  }
}


package com.example.reservation.impl

import java.util.UUID

import akka.stream.scaladsl.Source
import com.example.reservation.api
import com.example.reservation.api.ReservationService
import com.example.reservation.impl.crud.{ReservationDao, ReservationPublisher}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{EventStreamElement, PersistentEntityRef, PersistentEntityRegistry}

import scala.collection.immutable
import scala.concurrent.ExecutionContext

/**
  * Implementation of the ReservationService.
  */
class ReservationServiceImpl(dao: ReservationDao, reservationPublisher: ReservationPublisher,
  persistentEntityRegistry: PersistentEntityRegistry)(implicit ec: ExecutionContext) extends ReservationService {

  private def reservationEntity(listingId: UUID): PersistentEntityRef[ReservationCommand[_]] = {
    persistentEntityRegistry.refFor[ReservationEntity](listingId.toString)
  }

  override def reserve(listingId: UUID) = ServiceCall { reservation =>
    reservationEntity(listingId)
      .ask(AddReservation(reservation))
      .map { added =>
        api.ReservationAdded(added.listingId, added.reservationId, added.reservation)
      }
  }

  override def getCurrentReservations(listingId: UUID) = ServiceCall { _ =>
    reservationEntity(listingId).ask(GetCurrentReservations)
  }

  override def reservationEvents =
    TopicProducer.taggedStreamWithOffset(immutable.Seq(ReservationEvent.Tag)) { (tag, offset) =>
      persistentEntityRegistry.eventStream(tag, offset).map {
        case EventStreamElement(_, ReservationAdded(listingId, reservationId, reservation), offset) =>
          api.ReservationAdded(listingId, reservationId, reservation) -> offset
      }

    }

}

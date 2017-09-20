package com.example.reservation.impl

import java.util.UUID

import akka.stream.scaladsl.Source
import com.example.reservation.api
import com.example.reservation.api.ReservationService
import com.example.reservation.impl.crud.{ReservationDao, ReservationPublisher}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.lightbend.lagom.scaladsl.broker.TopicProducer
import com.lightbend.lagom.scaladsl.persistence.{PersistentEntityRef, PersistentEntityRegistry}

import scala.collection.immutable
import scala.concurrent.ExecutionContext

/**
  * Implementation of the ReservationService.
  */
class ReservationServiceImpl(dao: ReservationDao, reservationPublisher: ReservationPublisher,
  persistentEntityRegistry: PersistentEntityRegistry)(implicit ec: ExecutionContext) extends ReservationService {

  override def reserve(listingId: UUID) = ServiceCall { reservation =>

    val dbReservation = crud.Reservation(UUID.randomUUID(), listingId,
      reservation.checkin, reservation.checkout)

    for {
      _ <- dao.addReservation(dbReservation)
      added = api.ReservationAdded(listingId, dbReservation.reservationId, reservation)
      _ <- reservationPublisher.publish(added)
    } yield added

  }

  override def getCurrentReservations(listingId: UUID) = ServiceCall { _ =>
    dao.getCurrentReservations(listingId).map { reservations =>
      reservations.map { reservation =>
        api.Reservation(reservation.checkin, reservation.checkout)
      }
    }
  }

  override def reservationEvents = TopicProducer.singleStreamWithOffset { _ =>
    Source.maybe
  }

}

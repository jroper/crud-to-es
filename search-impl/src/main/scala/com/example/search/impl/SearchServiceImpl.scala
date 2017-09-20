package com.example.search.impl

import java.time.LocalDate
import java.util.UUID

import akka.Done
import akka.actor.{Actor, ActorSystem, Props, Status}
import com.lightbend.lagom.scaladsl.api.ServiceCall
import com.example.search.api.{ListingSearchResult, SearchService}
import com.example.reservation.api.{ReservationAdded, ReservationService}
import com.lightbend.lagom.scaladsl.api.transport.NotFound
import akka.pattern.ask
import akka.stream.scaladsl.Flow
import akka.util.Timeout
import scala.concurrent.duration._

/**
  * Implementation of the SearchService.
  */
class SearchServiceImpl(reservationService: ReservationService, actorSystem: ActorSystem) extends SearchService {

  import SearchActor._
  private val searchActor = actorSystem.actorOf(Props[SearchActor])
  implicit val searchActorTimeout = Timeout(10.seconds)

  reservationService.reservationEvents.subscribe.atLeastOnce(Flow[ReservationAdded].mapAsync(1) { reservation =>
    (searchActor ? reservation).mapTo[Done]
  })

  override def searchListings(checkin: LocalDate, checkout: LocalDate) = ServiceCall { _ =>
    (searchActor ? Search(checkin, checkout)).mapTo[Seq[ListingSearchResult]]
  }

  override def listingName(listingId: UUID) = ServiceCall { _ =>
    (searchActor ? ListingName(listingId)).mapTo[String]
  }
}

private object SearchActor {
  case class Search(checkin: LocalDate, checkout: LocalDate)
  case class ListingName(listingId: UUID)
}

private class SearchActor extends Actor {
  import SearchActor._

  val listings: Seq[ListingSearchResult] = Seq(
    ListingSearchResult(UUID.randomUUID(), "Beach house with wonderful views", "beachhouse.jpeg", 280),
    ListingSearchResult(UUID.randomUUID(), "Villa by the water", "villa.jpeg", 350),
    ListingSearchResult(UUID.randomUUID(), "Budget hotel convenient to town centre", "hotel.jpeg", 120),
    ListingSearchResult(UUID.randomUUID(), "Quaint country B&B", "bnb.jpeg", 180)
  )
  // Not at all an efficient (or persistent) index, but this is a demo and this code isn't the subject of the demo
  var reservations: Map[UUID, (ListingSearchResult, Set[ReservationAdded])] = {
    listings.map { listing =>
      listing.listingId -> (listing, Set.empty[ReservationAdded])
    }.toMap
  }

  override def receive = {

    case reservation: ReservationAdded =>
      reservations.get(reservation.listingId) match {
        case Some((listing, res)) =>
          if (res.forall(_.reservationId != reservation.reservationId)) {
            reservations += (listing.listingId -> ((listing, res + reservation)))
          }
          sender() ! Done
        case None =>
          // Ignore
          sender() ! Done
      }

    case Search(checkin, checkout) =>
      sender() ! reservations.values.collect {
        case (listing, res) if res.forall(reservationDoesNotConflict(checkin, checkout)) => listing
      }.toList

    case ListingName(listingId) =>
      reservations.get(listingId) match {
        case Some((listing, _)) => sender() ! listing.listingName
        case None => sender() ! Status.Failure(NotFound(s"Listing $listingId not found"))
      }
  }

  private def reservationDoesNotConflict(checkin: LocalDate, checkout: LocalDate)(reservationAdded: ReservationAdded): Boolean = {
    val rCheckin = reservationAdded.reservation.checkin
    val rCheckout = reservationAdded.reservation.checkout

    if (checkout.isBefore(rCheckin) || checkout == rCheckin) {
      true
    } else if (checkin.isAfter(rCheckout) || checkin == rCheckout) {
      true
    } else {
      false
    }
  }
}

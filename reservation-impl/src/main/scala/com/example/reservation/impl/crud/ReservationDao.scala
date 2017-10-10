package com.example.reservation.impl.crud

import java.sql.Date
import java.time.LocalDate
import java.util.UUID

import akka.Done
import com.example.reservation.api
import com.lightbend.lagom.scaladsl.api.transport.BadRequest
import org.slf4j.LoggerFactory
import play.api.db.slick.{DbName, SlickApi}
import slick.driver.JdbcProfile
import slick.jdbc.meta.MTable

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration._
import scala.util.{Failure, Success}

class ReservationDao(slickApi: SlickApi, publisher: ReservationPublisher)(implicit ec: ExecutionContext) {

  private val log = LoggerFactory.getLogger(getClass)
  private val dbConfig = slickApi.dbConfig[JdbcProfile](DbName("default"))

  import dbConfig.driver.api._
  import dbConfig.db

  def addReservation(reservation: Reservation): Future[Done] = {
    // Validate reservation dates
    val now = LocalDate.now()
    if (reservation.checkout.isBefore(reservation.checkin) || reservation.checkout == reservation.checkin) {
      throw BadRequest("Checkout date must be after checkin date")
    } else if (reservation.checkin.isBefore(now)) {
      throw BadRequest("Cannot make a reservation for the past")
    }

    db.run(
      (for {
        hasConflicts <- conflictingReservationsQuery(reservation).result
        _ <- if (hasConflicts) {
          throw BadRequest("Listing is already booked for those dates")
        } else {
          reservations += reservation
        }
      } yield {
        Await.result(publisher.publish(api.ReservationAdded(
          reservation.listingId,
          reservation.reservationId,
          api.Reservation(reservation.checkin, reservation.checkout)
        )), 5.seconds)
        
        Done
      }).transactionally
    )
  }

  def getCurrentReservations(listingId: UUID): Future[Seq[Reservation]] = {
    db.run(
      reservations
        .filter(_.listingId === listingId)
        .filter(_.checkout >= LocalDate.now())
        .sortBy(_.checkin)
        .result
    )
  }

  private def conflictingReservationsQuery(reservation: Reservation) = {
    reservations
      .filter(_.listingId === reservation.listingId)
      .filter(_.checkout > reservation.checkin)
      .filter(_.checkin < reservation.checkout)
      .exists
  }

  private implicit val localDateToDate = MappedColumnType.base[LocalDate, Date](Date.valueOf, _.toLocalDate)

  private implicit val uuidToString = MappedColumnType.base[UUID, String](_.toString, UUID.fromString)

  private class Reservations(tag: Tag) extends Table[Reservation](tag, "reservations") {
    def reservationId = column[UUID]("id", O.PrimaryKey)
    def listingId = column[UUID]("listing_id")
    def checkin = column[LocalDate]("checkin")
    def checkout = column[LocalDate]("checkout")
    override def * = (reservationId, listingId, checkin, checkout).<>(Reservation.tupled, Reservation.unapply _)
  }
  private val reservations = TableQuery[Reservations]

  private def ensureTableExists() = {
    db.run(
      MTable.getTables(None, None, Some("reservations"), None).headOption.flatMap { table =>
        if (table.isEmpty) {
          reservations.schema.create.map(_ => true)
        } else {
          DBIO.successful(false)
        }
      }
    ).onComplete {
      case Success(true) =>
        log.info("Successfully created reservations table")
      case Success(false) =>
        log.info("Reservations table already created")
      case Failure(error) =>
        log.error("Error creating reservations table", error)
    }
  }

  ensureTableExists()
}

case class Reservation(reservationId: UUID, listingId: UUID, checkin: LocalDate, checkout: LocalDate)
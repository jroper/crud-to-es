package com.example.web.controllers

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.{Locale, UUID}

import com.example.reservation.api.{Reservation, ReservationService}
import com.example.search.api.SearchService
import play.api.data.Form
import play.api.data.Forms._
import play.api.mvc.{Action, Controller}
import com.example.web.views
import play.api.i18n.{I18nSupport, MessagesApi}

import scala.concurrent.{ExecutionContext, Future}

class Main(searchService: SearchService, reservationService: ReservationService, override val messagesApi: MessagesApi)(implicit ec: ExecutionContext) extends Controller with I18nSupport {

  private val searchForm = Form(mapping(
    "checkin" -> localDate,
    "checkout" -> localDate
  )(SearchForm.apply)(SearchForm.unapply))

  private val reservationForm = Form(
    mapping(
      "listingId" -> uuid,
      "checkin" -> localDate,
      "checkout" -> localDate
    )(ReservationForm.apply)(ReservationForm.unapply)
  )

  def index = Action { implicit rh =>
    Ok(views.html.index(searchForm.fill(SearchForm(LocalDate.now(), LocalDate.now().plus(1, ChronoUnit.WEEKS)))))
  }

  def search = Action.async { implicit rh =>
    searchForm.bindFromRequest().fold(
      errors => Future.successful(Ok(views.html.index(errors))),
      form => {
        searchService.searchListings(form.checkin, form.checkout).invoke().map { listings =>
          Ok(views.html.list(listings, form, None))
        }
      }
    )
  }

  def book = Action.async { implicit rh =>
    reservationForm.bindFromRequest().fold(
      errors => Future.successful(BadRequest), // Shouldn't happen, all fields are hidden
      form => {
        reservationService.reserve(form.listingId)
          .invoke(Reservation(form.checkin, form.checkout))
          .map { added =>
            Ok(views.html.reservationAdded(added))
          }
      }
    )
  }

  def reservations(listingId: UUID) = Action.async { implicit rh =>
    val currentReservationsFuture = reservationService.getCurrentReservations(listingId).invoke()
    val listingNameFuture = searchService.listingName(listingId).invoke()

    for {
      listingName <- listingNameFuture
      reservations <- currentReservationsFuture
    } yield {
      Ok(views.html.reservations(listingName, reservations))

    }
  }

}

case class SearchForm(checkin: LocalDate, checkout: LocalDate)
case class ReservationForm(listingId: UUID, checkin: LocalDate, checkout: LocalDate)

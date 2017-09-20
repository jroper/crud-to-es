package com.example.search.api

import java.time.LocalDate
import java.util.UUID

import akka.NotUsed
import com.lightbend.lagom.scaladsl.api.deser.PathParamSerializer
import com.lightbend.lagom.scaladsl.api.transport.Method
import com.lightbend.lagom.scaladsl.api.{Service, ServiceCall}
import play.api.libs.json.{Format, Json}

/**
  * The search service interface.
  *
  * This describes everything that Lagom needs to know about how to serve and
  * consume the search service service.
  */
trait SearchService extends Service {

  def searchListings(checkin: LocalDate, checkout: LocalDate): ServiceCall[NotUsed, Seq[ListingSearchResult]]
  def listingName(listingId: UUID): ServiceCall[NotUsed, String]

  override final def descriptor = {
    import Service._

    implicit val datePathParamSerializer: PathParamSerializer[LocalDate] = {
      PathParamSerializer.required[LocalDate]("LocalDate")(LocalDate.parse)(_.toString)
    }

    named("search")
      .withCalls(
        restCall(Method.GET, "/api/search?checkin&checkout", searchListings _),
        restCall(Method.GET, "/api/listing/:listingId/name", listingName _)
      ).withAutoAcl(true)
  }
}

case class ListingSearchResult(listingId: UUID, listingName: String, image: String, price: Int)

object ListingSearchResult {
  implicit val format: Format[ListingSearchResult] = Json.format
}
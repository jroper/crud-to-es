package com.example.reservation.impl.crud

import akka.Done
import akka.actor.ActorSystem
import akka.kafka.ProducerMessage.Message
import akka.kafka.ProducerSettings
import akka.kafka.scaladsl.Producer
import akka.stream.Materializer
import akka.stream.scaladsl.{Sink, Source}
import com.example.reservation.api.{ReservationAdded, ReservationService}
import org.apache.kafka.clients.producer.ProducerRecord
import org.apache.kafka.common.serialization.StringSerializer
import play.api.libs.json.Json

import scala.concurrent.Future

/**
  * Lagom does not yet support imperative, non guaranteed publishing to Kafka. This allows it.
  */
class ReservationPublisher(system: ActorSystem)(implicit mat: Materializer) {

  private val producerSettings = ProducerSettings(system, new StringSerializer, new StringSerializer)
    .withBootstrapServers(system.settings.config.getString("lagom.broker.kafka.brokers"))
  private val kafkaProducer = producerSettings.createKafkaProducer()

  def publish(reservationAdded: ReservationAdded): Future[Done] = {
    Source.single(
      Message(new ProducerRecord(
        ReservationService.TopicName,
        reservationAdded.listingId.toString,
        Json.stringify(Json.toJson(reservationAdded))
      ), Done)
    ).via(Producer.flow(producerSettings, kafkaProducer))
     .map(_.message.passThrough)
     .runWith(Sink.head)
  }
}

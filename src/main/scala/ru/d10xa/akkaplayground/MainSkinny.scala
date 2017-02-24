package ru.d10xa.akkaplayground

import java.util.concurrent.Executors

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.contrib.throttle.Throttler.SetTarget
import akka.contrib.throttle.TimerBasedThrottler
import com.typesafe.config.ConfigFactory
import skinny.http._
import akka.pattern.pipe
import akka.pattern.ask
import akka.contrib.throttle.Throttler.RateInt
import akka.util.Timeout

import concurrent.duration._
import scala.collection.immutable.Seq
import scala.concurrent.ExecutionContext
import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success

/**
 * Blocking http client
 */
object MainSkinny {

  case class SendHttp(m: Long)
  case class PrintIt(string: String)

  class HttpActor(url: String) extends Actor with ActorLogging {
    //    implicit val executionContext = context.dispatcher
    implicit val ec = ExecutionContext.fromExecutor(Executors.newWorkStealingPool(1000))

    override def receive: Receive = {
      case SendHttp(m) =>
        val request = Request(url)
          .queryParam("time", m)
          .userAgent("skinny-http")
        val origin = sender()
        HTTP.asyncGet(request)
          .map(response => (response, origin)) pipeTo self
      case str: String =>
        log.info(s"$str")
      case (r: Response, origin: ActorRef) =>
        origin ! r.asString.toInt
    }
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val port = config.getInt("sleep-server.port")
    val url = s"http://localhost:$port/sleep"

    val system = ActorSystem("skinny-system")

    val httpActor = system.actorOf(Props(new HttpActor(url)))
    val throttler = system.actorOf(Props(
      classOf[TimerBasedThrottler],
      1 msgsPer 1.second
    ))
    throttler ! SetTarget(Some(httpActor))

    val numbers: Seq[Int] = (1 to 700).map(_ => 2000 + Random.nextInt(1000))
    implicit val timeout: Timeout = 10.seconds
    implicit val executionContext = system.dispatcher
    val responsesFuture: Future[Seq[Int]] =
      Future.sequence(numbers.map { n => (httpActor ? SendHttp(n)).mapTo[Int] })
    (1 to 3).foreach(i => throttler ! s"$i ...")
    responsesFuture.onComplete {
      case Success(responses: Seq[Int]) =>
        val sum1 = numbers.sum
        val sum2 = responses.sum
        println(s"numbers count = ${numbers.size}")
        println(s"sum1 = $sum1")
        println(s"responses count = ${responses.size}")
        println(s"sum2 = $sum2")
        system.terminate()
      case Failure(e) =>
        println(e)
        system.terminate()
    }

  }
}

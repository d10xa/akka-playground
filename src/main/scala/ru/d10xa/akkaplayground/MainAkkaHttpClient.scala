package ru.d10xa.akkaplayground

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.contrib.throttle.Throttler.RateInt
import akka.contrib.throttle.Throttler.SetTarget
import akka.contrib.throttle.TimerBasedThrottler
import akka.pattern.pipe
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration._
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.model.StatusCodes
import akka.stream.ActorMaterializer
import akka.util.ByteString

import scala.concurrent.Future
import scala.util.Failure
import scala.util.Random
import scala.util.Success

/**
 * Non blocking http client
 */
object MainAkkaHttpClient {

  case class SendHttp(m: Long)
  case class PrintIt(string: String)

  class HttpActor(url: String) extends Actor with ActorLogging {
    implicit val executionContext = context.dispatcher
    implicit val materializer = ActorMaterializer()
    val http = Http(context.system)
    var counter: Int = 0
    var firstRequestTime: Long = 0

    override def receive: Receive = {
      case SendHttp(m) =>
        if(firstRequestTime == 0) {
          firstRequestTime = System.currentTimeMillis()
        }
        val origin: ActorRef = sender()
        log.info(s"SendHttp sender ${sender()}")
        http.singleRequest(HttpRequest(uri = s"$url?time=$m"))
          .pipeTo(self)(origin)
      case HttpResponse(StatusCodes.OK, headers, entity, _) =>
        val origin = sender()
        entity.dataBytes.runFold(ByteString(""))(_ ++ _).map(_.utf8String.toInt).foreach { body =>
          log.info(s"HttpResponse #${counter += 1; counter} $origin " +
            s"$body ${System.currentTimeMillis() - firstRequestTime}")
          origin ! body
        }
    }
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val port = config.getInt("sleep-server.port")
    val url = s"http://localhost:$port/sleep"

    val system = ActorSystem("akka-client-system")

    val httpActor = system.actorOf(Props(new HttpActor(url)))
    val throttler = system.actorOf(Props(
      classOf[TimerBasedThrottler],
      1 msgsPer 1.second
    ))
    throttler ! SetTarget(Some(httpActor))

    implicit val timeout: Timeout = 30.seconds
    implicit val executionContext = system.dispatcher
    import akka.pattern.ask
    val numbers: Seq[Int] = (1 to 2000).map(_ => 2000 + Random.nextInt(1000))

    val responsesFuture: Future[Seq[Int]] =
      Future.sequence(numbers.map { n => (httpActor ? SendHttp(n)).map {case i: Int => i} })
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

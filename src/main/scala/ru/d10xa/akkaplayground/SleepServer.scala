package ru.d10xa.akkaplayground

import akka.actor.Actor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.LoggingReceive
import akka.http.scaladsl.Http
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.Directives._
import akka.stream.ActorMaterializer
import akka.pattern.ask
import akka.util.Timeout
import com.typesafe.config.ConfigFactory

import scala.concurrent.duration.FiniteDuration
import scala.io.StdIn
import concurrent.duration._

object SleepServer {

  case class Tick(duration: FiniteDuration)
  case object Tack

  class TickScheduler extends Actor {
    implicit val executionContext = context.dispatcher
    override def receive: Receive = LoggingReceive {
      case Tick(duration) =>
        context.system.scheduler.scheduleOnce(duration, sender(), Tack)
    }
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val port = config.getInt("sleep-server.port")
    implicit val system = ActorSystem("sleep-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher
    val tickActor = system.actorOf(Props[TickScheduler], "tick-tack")

    val route =
      (path("sleep") & parameter('time.as[Long].?)) { (time: Option[Long]) =>
        get {
          time match {
            case Some(t) =>
              val duration = t.millis
              implicit val tt = Timeout.durationToTimeout(duration.plus(1.second))
              onComplete(tickActor ? Tick(duration)) { _ =>
                complete(t.toString)
              }
            case None =>
              complete(0.toString)
          }
        }
      }

    val bindingFuture = Http().bindAndHandle(route, "localhost", port)

    println(s"Server online at http://localhost:8080/\nPress RETURN to stop...")
    StdIn.readLine() // let it run until user presses return
    bindingFuture
      .flatMap(_.unbind()) // trigger unbinding from the port
      .onComplete(_ => system.terminate()) // and shutdown when done
  }
}

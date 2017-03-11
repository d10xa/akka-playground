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
import concurrent.duration._
import scala.concurrent.Await

object SleepServer {

  case class Tick(duration: FiniteDuration)
  case object Tack

  class TickScheduler extends Actor {
    implicit val executionContext = context.dispatcher
    override def receive: Receive = LoggingReceive {
      case Tick(duration) =>
        context.system.scheduler.scheduleOnce(duration, sender(), Tack)
    }

    override def postStop(): Unit = {
      println("stop TickScheduler")
    }
  }

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load()
    val port = config.getInt("sleep-server.port")
    val host = config.getString("sleep-server.host")
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

    def shutdownAction(binding: Http.ServerBinding) = {
      binding.unbind()
      system.terminate()
      Await.result(system.whenTerminated, 5.seconds)
      println("exit ok")
    }

    val bindingFuture =
      Http().bindAndHandle(route, host, port)

    bindingFuture.foreach { binding =>
      sys.addShutdownHook(shutdownAction(binding))
    }

    println(s"Server online at http://$host:$port")
  }
}

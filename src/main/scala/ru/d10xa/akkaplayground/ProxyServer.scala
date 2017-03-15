package ru.d10xa.akkaplayground

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import com.typesafe.config.ConfigFactory

import scala.concurrent.Await
import scala.concurrent.duration._

object ProxyServer {

  def main(args: Array[String]): Unit = {
    val config = ConfigFactory.load().getConfig("proxy-server")
    val port = config.getInt("port")
    val host = config.getString("host")
    val targetHost = config.getString("targetHost")
    val targetPort = config.getInt("targetPort")

    implicit val system = ActorSystem("proxy-system")
    implicit val materializer = ActorMaterializer()
    implicit val executionContext = system.dispatcher

    val proxy: Route = Route { context =>
      val request = context.request.withUri(context.request.uri.copy(path = Uri.Path("/sleep")))
      println(s"Origin request ${context.request}")
      println(s"Modified request $request")

      val flow = Http(system).outgoingConnection(targetHost, targetPort)
      Source.single(request)
        .via(flow)
        .runWith(Sink.head)
        .flatMap(r => context.complete(r))
    }

    val route = path("proxy") { proxy }

    def shutdownAction(binding: Http.ServerBinding) = {
      binding.unbind()
      system.terminate()
      Await.result(system.whenTerminated, 5.seconds)
    }

    val bindingFuture =
      Http().bindAndHandle(route, host, port)

    bindingFuture.foreach { binding =>
      sys.addShutdownHook(shutdownAction(binding))
    }

    println(s"Proxy online at http://$host:$port")
  }
}

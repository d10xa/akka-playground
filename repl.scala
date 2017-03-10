import akka.actor.ActorSystem
import akka.actor._
import akka.pattern._
import akka.util.Timeout
import akka.stream.ActorMaterializer
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import ru.d10xa.akkaplayground._
import concurrent.duration._

implicit val system = ActorSystem("repl-system")
implicit val materializer = ActorMaterializer()
implicit val timeout: Timeout = 3.seconds

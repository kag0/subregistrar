package gl.uh.subregistrar

import java.io.ByteArrayInputStream

import akka.actor.ActorSystem
import akka.http.scaladsl.server.HttpApp
import cats.data.EitherT
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gl.uh.subregistrar.clients.{CloudflareClient, SheetsPersistenceClient}
import gl.uh.subregistrar.errors.Error
import gl.uh.subregistrar.misc._
import gl.uh.subregistrar.models.CreateNameServerRequest
import gl.uh.subregistrar.security.UserAuthn
import gl.uh.subregistrar.services.{NameService, UserService}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config

import scala.concurrent.Future

object Server extends HttpApp with PlayJsonSupport {
  implicit lazy val system = ActorSystem()
  implicit lazy val ec = system.dispatcher
  type F[A] = EitherT[Future, Error, A]

  val config = ConfigFactory.load

  val cloudflare = new CloudflareClient(
    serverConfig.sld,
    serverConfig.cloudflareZoneId,
    serverConfig.cloudflareToken
  )
  val persistence = new SheetsPersistenceClient(
    serverConfig.sheetId,
    serverConfig.googleCredential
  )
  val nameService = new NameService[F](persistence, cloudflare)
  val userService = new UserService[F]()

  implicit val userAuth = UserAuthn("testUser")

  // Format: OFF
  override val routes = concat(
    path("names")(
      get(complete(nameService.listNames))
    ),
    path("names" / Segment)(name =>
      get(complete(nameService.retrieveName(name))) ~
      post(registerName(name)) ~
      delete(complete(nameService.deregisterName(name)))
    ),
    path("names" / Segment / "nameServers")(name =>
      post(createNameServer(name))
    ),
    path("names" / Segment / "nameServers" / Segment)((name, recordId) =>
      delete(complete(nameService.deleteNameServer(name, recordId)))
    ),
    path("token" / Segment)(provider =>
      post(complete(userService.logIn(provider, ???)))
    )
  )
  // Format: ON

  def registerName(name: String) = extractRequestEntity { e =>
    e.discardBytes()
    complete(nameService.registerName(name))
  }

  def createNameServer(name: String) =
    entity(as[CreateNameServerRequest]) { req =>
      complete(nameService.createNameServer(name, req.nameServer))
    }
}

case class ServerConfig(
    sld: String,
    cloudflareZoneId: String,
    cloudflareToken: String,
    sheetId: String,
    googleCredential: GoogleCredentials
)
object ServerConfig {
  def fromConfig(config: Config) = ServerConfig(
    config.getString("sld"),
    config.getString("cloudflareZoneId"),
    config.getString("cloudflareToken"),
    config.getString("sheetId"),
    GoogleCredentials.fromStream(
      new ByteArrayInputStream(config.getString("googleCredential").getBytes)
    )
  )
}

object Main extends App {
  Server.startServer("localhost", 8080)
}

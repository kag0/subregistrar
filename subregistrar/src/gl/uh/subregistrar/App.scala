package gl.uh.subregistrar

import java.io.ByteArrayInputStream

import akka.actor.ActorSystem
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Authority
import akka.http.scaladsl.server.HttpApp
import akka.http.scaladsl.server.directives.Credentials.Provided
import black.door.jose.jwk.P256KeyPair
import cats.arrow.FunctionK
import cats.data.EitherT
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gl.uh.subregistrar.clients.{
  CloudflareClient,
  OIdCClient,
  SheetsPersistenceClient
}
import gl.uh.subregistrar.errors.Error
import gl.uh.subregistrar.misc._
import gl.uh.subregistrar.models.CreateNameServerRequest
import gl.uh.subregistrar.security.UserAuthn
import gl.uh.subregistrar.services.{
  NameService,
  OIdCKeyResolver,
  Provider,
  UserService
}
import com.typesafe.config.ConfigFactory
import com.typesafe.config.Config
import CorsDirectives._

import scala.concurrent.Future

object Server extends HttpApp with PlayJsonSupport {
  implicit lazy val system = ActorSystem()
  implicit lazy val ec = system.dispatcher
  type F[A] = EitherT[Future, Error, A]

  implicit val etToF = new FunctionK[F, F] {
    def apply[A](fa: F[A]): F[A] = fa
  }

  val config = ConfigFactory.load
  val serverConfig = ServerConfig()
  val tokenSigningKey = P256KeyPair.generate.copy(alg = Some("ES256"))

  val cloudflare = new CloudflareClient(
    serverConfig.sld,
    serverConfig.cloudflareZoneId,
    serverConfig.cloudflareToken
  )
  val persistence = new SheetsPersistenceClient(
    serverConfig.sheetId,
    serverConfig.domainPage,
    serverConfig.userPage,
    serverConfig.googleCredential
  )
  val nameService = new NameService[F](persistence, cloudflare)
  val oIdCService = new OIdCClient()
  val userService = new UserService[F](
    tokenSigningKey,
    serverConfig.oIdCProviders,
    persistence,
    oIdCService
  )
  import userService.authenticateDirective

  // Format: OFF
  override val routes = cors(concat(
    path("names")(
      get(listNames)
    ),
    path("names" / Segment)(name =>
      get(complete(nameService.retrieveName(name))) ~
      post(registerName(name)) ~
      delete(deregisterName(name))
    ),
    path("names" / Segment / "nameServers")(name =>
      post(createNameServer(name))
    ),
    path("names" / Segment / "nameServers" / Segment)((name, recordId) =>
      delete(deleteNameServer(name, recordId))
    ),
    path("token")(
      post(logIn)
    )
  ))
  // Format: ON

  val logIn = formFields("code", "provider", "redirect_uri")(
    (code, provider, redirectUri) =>
      complete(userService.logIn(provider, code, redirectUri))
  )

  def listNames =
    authenticateDirective(implicit auth => complete(nameService.listNames))

  def registerName(name: String) =
    authenticateDirective(implicit auth =>
      extractRequestEntity { e =>
        e.discardBytes()
        complete(nameService.registerName(name))
      }
    )

  def deregisterName(name: String) =
    authenticateDirective(implicit auth =>
      complete(nameService.deregisterName(name))
    )

  def createNameServer(name: String) =
    authenticateDirective(implicit auth =>
      entity(as[CreateNameServerRequest]) { req =>
        complete(nameService.createNameServer(name, req.nameServer))
      }
    )

  def deleteNameServer(name: String, recordId: String) =
    authenticateDirective(implicit auth =>
      complete(nameService.deleteNameServer(name, recordId))
    )
}

case class ServerConfig(
    sld: String,
    cloudflareZoneId: String,
    cloudflareToken: String,
    sheetId: String,
    domainPage: String,
    userPage: String,
    googleCredential: GoogleCredentials,
    oIdCProviders: Seq[Provider]
)
object ServerConfig {
  def fromConfig(config: Config) = ServerConfig(
    config.getString("sld"),
    config.getString("cloudflareZoneId"),
    config.getString("cloudflareToken"),
    config.getString("sheetId"),
    config.getString("domainPage"),
    config.getString("userPage"),
    GoogleCredentials.fromStream(
      new ByteArrayInputStream(config.getString("googleCredential").getBytes)
    ),
    ???
  )
}

object Main extends App {
  Server.startServer("localhost", 8080)
}

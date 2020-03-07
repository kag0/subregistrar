package gl.uh.subregistrar

import akka.http.scaladsl.server.HttpApp
import cats.{Monad, MonadError}
import cats.data.EitherT
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.Future
import gl.uh.subregistrar.errors.Error
import cats.implicits._
import gl.uh.subregistrar.services.{CloudflareService, NameService, UserService}
import gl.uh.subregistrar.services.{CloudflareService, NameService, PersistenceService, UserService}

object Server extends HttpApp with PlayJsonSupport {
  implicit val ec = systemReference.get().dispatcher
  implicitly[Monad[Future]]
  type F[A] = EitherT[Future, Error, A]
  val cloudflare: CloudflareService[F] = ???
  val persistence: PersistenceService[F] = ???
  val nameService = new NameService[F](persistence, cloudflare)
  val userService = new UserService[F]()

  override val routes = concat(
    path("names")(
      get(complete(nameService.listNames(???))) ~
      post(complete(nameService.registerName(???))) ~
      delete(complete(nameService.deregisterName(???)))
    ),
    path("names" / Segment)( name =>
      get(complete(nameService.retrieveName(name)))
    ),
    path("names" / Segment / "nameServers")( name =>
      post(complete(nameService.createNameServer(name, ???)))
    ),
    path("names" / Segment / "nameServers" / Segment)( (name, recordId) =>
      delete(complete(nameService.deleteNameServer(name, recordId)))
    ),
    path("token" / Segment)( provider =>
      post(complete(userService.logIn(provider, ???)))
    )
  )
}

object Main extends App {
  Server.startServer("localhost", 8080)
}


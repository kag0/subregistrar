package gl.uh.subregistrar

import akka.http.scaladsl.marshalling.ToResponseMarshaller
import akka.http.scaladsl.model.HttpResponse
import akka.http.scaladsl.server.HttpApp
import cats.{Monad, MonadError}
import cats.data.EitherT
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport

import scala.concurrent.Future
import gl.uh.subregistrar.errors.Error
import cats.implicits._
import gl.uh.subregistrar.clients.{CloudflareClient, SheetsPersistenceClient}
import gl.uh.subregistrar.services.{
  CloudflareService,
  NameService,
  PersistenceService,
  UserService
}
import gl.uh.subregistrar.misc._

object Server extends HttpApp with PlayJsonSupport {
  implicit val system = systemReference.get()
  implicit val ec = system.dispatcher
  type F[A] = EitherT[Future, Error, A]
  val cloudflare = new CloudflareClient(???, ???, ???)
  val persistence = new SheetsPersistenceClient(???, ???)
  val nameService = new NameService[F](persistence, cloudflare)
  val userService = new UserService[F]()

  implicitly[ToResponseMarshaller[Future[Either[Error, Unit]]]]
  eitherTMarshaller[Future, Error, Unit, HttpResponse]
  implicitly[ToResponseMarshaller[EitherT[Future, Error, Unit]]]
  override val routes = concat(
    path("names")(
      get(complete(nameService.listNames(???))) ~
        post(complete(nameService.registerName(???))) ~
        delete(complete(nameService.deregisterName(???)))
    ),
    path("names" / Segment)(name =>
      get(complete(nameService.retrieveName(name)))
    ),
    path("names" / Segment / "nameServers")(name =>
      post(complete(nameService.createNameServer(name, ???)))
    ),
    path("names" / Segment / "nameServers" / Segment)((name, recordId) =>
      delete(complete(nameService.deleteNameServer(name, recordId)))
    ),
    path("token" / Segment)(provider =>
      post(complete(userService.logIn(provider, ???)))
    )
  )
}

object Main extends App {
  Server.startServer("localhost", 8080)
}

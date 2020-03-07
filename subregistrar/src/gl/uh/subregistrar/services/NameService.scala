package gl.uh.subregistrar.services

import cats.MonadError
import cats.implicits._
import gl.uh.subregistrar.errors._
import gl.uh.subregistrar.models.Name
import gl.uh.subregistrar.security.UserAuthn

class NameService[F[_]](persistenceService: PersistenceService[F], cloudflare: CloudflareService[F])(implicit F: MonadError[F, Error]) {

  def listNames(implicit userAuthn: UserAuthn) = persistenceService.listNames(userAuthn.userId)

  def retrieveName(name: String) = cloudflare.listNameServers(name)

  def registerName(name: String)(implicit userAuthn: UserAuthn) =
    for {
      n <- F.fromEither(Name.validate(name).map(_.name))
      _ <- persistenceService.registerName(n, userAuthn.userId)
    } yield ()

  def deregisterName(name: String)(implicit userAuthn: UserAuthn) =
    for {
      n <- F.fromEither(Name.validate(name).map(_.name))
      _ <- authorize(n)
      _ <- persistenceService.deregisterName(n)
    } yield ()

  def createNameServer(name: String, nameServer: String)(implicit userAuthn: UserAuthn) = for {
    _ <- authorize(name.toLowerCase)
    _ <- cloudflare.createNameServer(name, nameServer)
  } yield ()

  def deleteNameServer(name: String, recordId: String)(implicit userAuthn: UserAuthn) = for {
    _ <- authorize(name.toLowerCase)
    _ <- cloudflare.deleteNameServer(recordId)
  } yield ()

  private def authorize(n: String)(implicit userAuthn: UserAuthn) = for {
    maybeOwner <- persistenceService.getNameOwner(n)
    _ <- maybeOwner match {
      case Some(userAuthn.userId) => F.unit
      case _ => F.raiseError(Error(unauthorized, "That name does not belong to you"))
    }
  } yield ()
}

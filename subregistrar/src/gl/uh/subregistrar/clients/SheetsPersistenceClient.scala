package gl.uh.subregistrar.clients

import akka.http.scaladsl.Http
import gl.uh.subregistrar.services.PersistenceService
import gl.uh.subregistrar.Server
import gl.uh.subregistrar.Server.F
import gl.uh.subregistrar.models.Name
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Path
import Path._
import akka.actor.ActorSystem

class SheetsPersistenceClient(sheetId: String, apiKey: String)(implicit system: ActorSystem) extends PersistenceService[Server.F] {
  private implicit val ec = system.dispatcher

  def listNames(userId: String): F[Seq[Name]] = for {
    reply <- Http().singleRequest(Get(base(/("spreadsheets") / sheetId / "values" / "A:C")))

  }

  def getNameOwner(name: String): F[Option[String]] = ???

  def registerName(name: String, userId: String): F[Unit] = ???

  def deregisterName(name: String): F[Unit] = ???

  private def base(path: Path) = Uri("https://sheets.googleapis.com").withPath(/("v4") ++ path)
}

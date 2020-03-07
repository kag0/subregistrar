package gl.uh.subregistrar.services

import gl.uh.subregistrar.models.NameServer

trait CloudflareService[F[_]] {
  def listNameServers(name: String): F[Seq[NameServer]]
  def createNameServer(name: String, nameServer: String): F[Unit]
  def deleteNameServer(id: String): F[Unit]
}

package gl.uh.subregistrar.services

import gl.uh.subregistrar.models.Name

trait PersistenceService[F[_]] {
  def listNames(userId: String): F[Seq[Name]]
  def getNameOwner(name: String): F[Option[String]]
  def registerName(name: String, userId: String): F[Unit]
  def deregisterName(name: String): F[Unit]

  /**
    * @return the user's ID
    */
  def upsertUser(sub: String, provider: String, email: String): F[String]
}

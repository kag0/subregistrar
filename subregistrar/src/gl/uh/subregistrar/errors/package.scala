package gl.uh.subregistrar

import cats.MonadError
import play.api.libs.json.Json

package object errors {
  type MonadErrorF[F[_]] = MonadError[F, Error]

  case class Error(error: String, message: String)
  object Error {
    implicit val format = Json.format[Error]
  }

  val nameAlreadyRegistered = "NameAlreadyRegistered"
  val invalidName = "InvalidName"
  val unauthorized = "Unauthorized"
}

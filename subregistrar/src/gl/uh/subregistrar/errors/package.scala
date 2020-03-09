package gl.uh.subregistrar

import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.StatusCodes.BadRequest
import cats.MonadError
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import play.api.libs.json.{JsObject, Json}

package object errors extends PlayJsonSupport {
  type MonadErrorF[F[_]] = MonadError[F, Error]

  case class Error(error: String, message: String)
  object Error {
    implicit val trm =
      PredefinedToResponseMarshallers
        .fromStatusCodeAndHeadersAndValue[JsObject]
        .compose[Error](e => (BadRequest, Nil, format.writes(e)))
    val format = Json.format[Error]
  }

  val nameAlreadyRegistered = "NameAlreadyRegistered"
  val invalidName = "InvalidName"
  val unauthorized = "Unauthorized"
  val unauthenticated = "Unauthenticated"
}

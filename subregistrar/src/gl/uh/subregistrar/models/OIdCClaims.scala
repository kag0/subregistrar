package gl.uh.subregistrar.models

import akka.http.scaladsl.model.Uri
import gl.uh.subregistrar.misc.uriFormat
import play.api.libs.json.Json

case class OIdCClaims(
    iss: Uri,
    aud: String,
    sub: String,
    azp: Option[String],
    email: Option[String]
)
object OIdCClaims {
  implicit val format = Json.format[OIdCClaims]
}

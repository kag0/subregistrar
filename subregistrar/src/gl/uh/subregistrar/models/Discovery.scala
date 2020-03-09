package gl.uh.subregistrar.models

import akka.http.scaladsl.model.Uri
import gl.uh.subregistrar.misc.uriFormat
import play.api.libs.json.Json

case class Discovery(
    issuer: Uri,
    token_endpoint: Uri,
    token_endpoint_auth_methods_supported: Seq[String],
    jwks_uri: Uri,
    userinfo_endpoint: Uri
)
object Discovery {
  implicit val format = Json.format[Discovery]
}

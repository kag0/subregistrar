package gl.uh.subregistrar.models

import play.api.libs.json.Json

case class SuccessfulTokenResponse(access_token: String, id_token: String)
object SuccessfulTokenResponse {
  implicit val format = Json.format[SuccessfulTokenResponse]
}

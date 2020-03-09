package gl.uh.subregistrar.models

import play.api.libs.json.Json

case class CreateNameServerRequest(nameServer: String)
object CreateNameServerRequest {
  implicit val format = Json.format[CreateNameServerRequest]
}

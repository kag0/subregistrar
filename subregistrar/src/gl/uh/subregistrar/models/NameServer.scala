package gl.uh.subregistrar.models

import play.api.libs.json.Json

case class NameServer(id: String, nameServer: String)
object NameServer {
  implicit val format = Json.format[NameServer]
}

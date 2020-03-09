package gl.uh.subregistrar.models

import play.api.libs.json.Json

//OpenID Connect 2 standard claims
case class UserInfo(sub: String, email: String)
object UserInfo {
  implicit val format = Json.format[UserInfo]
}

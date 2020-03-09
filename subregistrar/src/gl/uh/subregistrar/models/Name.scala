package gl.uh.subregistrar.models

import gl.uh.subregistrar.errors._
import play.api.libs.json.{JsString, Writes}

case class Name private (name: String)
object Name {
  val allowedCharacters = Set("")
  def validate(name: String) = {
    val lower = name.toLowerCase
    val violations = (if (!"^[a-z\\d-]+$".r.matches(lower))
                        List(s"'$lower' has invalid characters")
                      else Nil) ++
      (if (name.length > 240)
         List("name is too long (max length is 240 characters)")
       else Nil) ++
      Nil

    if (violations.isEmpty) Right(Name(lower))
    else Left(Error(invalidName, violations.mkString(", ")))
  }

  implicit val writes = Writes[Name](name => JsString(name.name))
}

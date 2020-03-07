package gl.uh.subregistrar.models

import play.api.libs.json.{JsBoolean, JsDefined, JsSuccess, Json, Reads}

package object cloudflare {

  case class DnsRecord(id: String, content: String)
  object DnsRecord {
    implicit val format = Json.format[DnsRecord]
  }

  case class CreateDnsRequest(`type`: String, name: String, content: String, ttl: Int = 1)
  object CreateDnsRequest {
    implicit val format = Json.format[CreateDnsRequest]
  }

  sealed trait CloudflairResponse[+A]
  case class CloudflairResult[A](result: A) extends CloudflairResponse[A]
  case class CloudflairErrors(errors: Seq[CloudflairError]) extends CloudflairResponse[Nothing] {
    def toException = new Exception(s"Cloudflair errors ${errors.map(e => s"${e.code}: ${e.message}").mkString(", ")}")
  }
  case class CloudflairError(code: Int, message: String)

  object CloudflairError {
    implicit val reads = Json.reads[CloudflairError]
  }

  object CloudflairResponse {
    implicit def reads[A](implicit aReads: Reads[A]) =
      Reads[CloudflairResponse[A]] ( js =>
        js \ "success" match {
          case JsDefined(JsBoolean(true)) => (js \ "result")
            .validate(CloudflairResult.reads[A])
          case JsDefined(JsBoolean(false)) => (js \ "errors")
            .validate[Seq[CloudflairError]]
            .map(CloudflairErrors)
        }
      )
  }

  object CloudflairResult {
    def reads[A: Reads] = Json.reads[CloudflairResult[A]]
  }
}

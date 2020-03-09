package gl.uh.subregistrar

import java.security.SecureRandom
import java.util.{Base64, Random}

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.{StatusCodes, Uri}
import cats.data.EitherT
import play.api.libs.json.{Format, JsError, JsString, JsSuccess, Reads, Writes}

import akka.http.scaladsl.model.HttpMethods._
import akka.http.scaladsl.model.StatusCodes.OK
import akka.http.scaladsl.model.headers._
import akka.http.scaladsl.server.Directive0
import akka.http.scaladsl.server.Directives._

import scala.util.Try

package object misc {

  implicit val unitTRM =
    PredefinedToResponseMarshallers.fromStatusCode.compose[Unit](_ =>
      StatusCodes.NoContent
    )

  implicit def eitherTMarshaller[F[_], L, R, B](
      implicit marshaller: Marshaller[F[Either[L, R]], B]
  ): Marshaller[EitherT[F, L, R], B] = marshaller.compose(et => et.value)

  implicit val uriFormat = Format[Uri](
    Reads {
      case JsString(value) =>
        Try(Uri(value)).fold(e => JsError(e.getMessage), JsSuccess(_))
      case _ => JsError("Uri was not a string")
    },
    Writes(uri => JsString(uri.toString))
  )

  private val random = ThreadLocal.withInitial[Random](() => new SecureRandom())
  def randomString: String = {
    val id = new Array[Byte](16)
    random.get.nextBytes(id)

    Base64.getUrlEncoder.withoutPadding.encodeToString(id)
  }

  trait CorsDirectives {

    val corsPreflight: Directive0 = extractRequest.flatMap(request =>
      request.method match {
        case OPTIONS =>
          val allowHeaders = request
            .header[`Access-Control-Request-Headers`]
            .map(rh => `Access-Control-Allow-Headers`(rh.headers))

          complete(
            OK -> (List(
              `Access-Control-Allow-Methods`(GET, POST, PATCH, PUT, DELETE),
              `Access-Control-Allow-Origin`.*,
              `Access-Control-Max-Age`(86400)
            ) ++ allowHeaders)
          )
        case _ => pass
      }
    )

    val corsSimple =
      mapResponseHeaders(
        _.filter(_.isNot(`Access-Control-Allow-Origin`.lowercaseName))
          .filter(_.isNot(`Access-Control-Max-Age`.lowercaseName)) ++ List(
          `Access-Control-Allow-Origin`.*,
          `Access-Control-Max-Age`(86400)
        )
      )

    val cors: Directive0 = corsPreflight.tflatMap(_ => corsSimple)
  }

  object CorsDirectives extends CorsDirectives

}

package gl.uh.subregistrar.clients

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import gl.uh.subregistrar.models.NameServer
import gl.uh.subregistrar.services.CloudflareService
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.model.{HttpRequest, Uri}
import akka.http.scaladsl.model.Uri.{Path, Query}
import Path./
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.unmarshalling.Unmarshal
import cats.MonadError
import gl.uh.subregistrar.errors.Error
import cats.data.EitherT
import gl.uh.subregistrar.Server
import cats.implicits._
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gl.uh.subregistrar.Server.F
import gl.uh.subregistrar.models.cloudflare._
import play.api.libs.json.{Format, JsBoolean, JsSuccess, JsValue, Json, Reads}

import scala.concurrent.duration.Duration
import scala.concurrent.{ExecutionContext, Future}

class CloudflareClient(sld: String, zoneId: String, token: String)(
    implicit
    system: ActorSystem,
    F: MonadError[Server.F, Error]
) extends CloudflareService[Server.F]
    with PlayJsonSupport
    with LazyLogging {
  private implicit val ec = system.dispatcher

  def listNameServers(name: String) =
    for {
      response <- EitherT.right(
        request(
          Get(
            base(/("dns_records"))
              .withQuery(Query("type" -> "NS", "name" -> (name + sld)))
          )
        )
      )

      result <- EitherT.right(
        Unmarshal(response).to[CloudflairResponse[Seq[DnsRecord]]]
      )

      records <- errorToException(result)

    } yield records.map(r => NameServer(r.id, r.content))

  def createNameServer(name: String, nameServer: String) =
    for {
      response <- EitherT.right(
        request(
          Post(
            base(/("dns_records")),
            CreateDnsRequest("NS", name, nameServer)
          )
        )
      )

      strictResponse <- EitherT.right(response.toStrict(Duration(5, "seconds")))

      _ = strictResponse.entity.dataBytes
        .runReduce(_ ++ _)
        .map(_.utf8String)
        .foreach(logger.debug(_))

      result <- EitherT.right {
        Unmarshal(strictResponse)
          .to[CloudflairResponse[DnsRecord]]
      }

      _ <- errorToException(result)
    } yield ()

  def deleteNameServer(id: String) =
    for {
      response <- EitherT.right(request(Delete(base(/("dns_records") / id))))
      result <- EitherT.right(
        Unmarshal(response).to[CloudflairResponse[JsValue]]
      )
      _ <- errorToException(result)
    } yield ()

  private def base(path: Path) =
    Uri("https://api.cloudflare.com").withPath(
      /("client") / "v4" / "zones" / zoneId ++ path
    )

  private def request(request: HttpRequest) =
    Http().singleRequest(
      request.addHeader(Authorization(OAuth2BearerToken(token)))
    )

  private def errorToException[A](result: CloudflairResponse[A]) =
    result match {
      case CloudflairResult(a) => EitherT.rightT[Future, Error](a)
      case error: CloudflairErrors =>
        EitherT.right[Error](
          Future.failed(
            error.toException
          )
        )
    }
}

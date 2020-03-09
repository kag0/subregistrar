package gl.uh.subregistrar.clients

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding.{Get, Post, _}
import akka.http.scaladsl.model.{FormData, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import cats.data.EitherT
import gl.uh.subregistrar.Server
import gl.uh.subregistrar.Server.F
import gl.uh.subregistrar.models.{Discovery, SuccessfulTokenResponse, UserInfo}
import gl.uh.subregistrar.services.OIdCService
import cats.implicits._
import akka.http.scaladsl.model.Uri.Query
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import cats.MonadError
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gl.uh.subregistrar.errors.{Error, MonadErrorF}

import scala.concurrent.Future

class OIdCClient(implicit F: MonadErrorF[Server.F], system: ActorSystem)
    extends OIdCService[Server.F]
    with PlayJsonSupport {
  private implicit val ec = system.dispatcher

  def discoveryDoc(iss: Uri) =
    EitherT.right[Error](
      Http()
        .singleRequest(
          Get(
            iss.withPath(iss.path ?/ ".well-known" / "openid-configuration")
          )
        )
        .flatMap(Unmarshal(_).to[Discovery])
    )

  def tokenRequest(
      discovery: Discovery,
      code: String,
      clientId: String,
      clientSecret: String,
      redirectUri: String
  ) =
    for {
      _ <- if (!discovery.token_endpoint_auth_methods_supported.contains(
                 "client_secret_post"
               ))
        F.raiseError(
          Error(
            "UnsupportedClientAuth",
            "Identity provider doesn't support client_secret_post client auth"
          )
        )
      else F.unit
      request = Post(
        discovery.token_endpoint,
        FormData(
          "grant_type" -> "authorization_code",
          "code" -> code,
          "client_id" -> clientId,
          "client_secret" -> clientSecret,
          "redirect_uri" -> redirectUri
        )
      )
      response <- EitherT.right[Error](
        Http()
          .singleRequest(request)
          .flatMap(Unmarshal(_).to[SuccessfulTokenResponse])
      )
    } yield response

  def userInfo(discovery: Discovery, accessToken: String) =
    EitherT.right[Error](
      Http()
        .singleRequest(
          Get(discovery.userinfo_endpoint)
            .addHeader(Authorization(OAuth2BearerToken(accessToken)))
        )
        .flatMap(Unmarshal(_).to[UserInfo])
    )
}

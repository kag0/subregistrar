package gl.uh.subregistrar.services

import java.time.{Clock, Instant}

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.Uri
import akka.http.scaladsl.model.Uri.Authority
import black.door.jose.jwk.Jwk
import black.door.jose.jws.{JwsHeader, KeyResolver}
import black.door.jose.jwt.{Check, Claims, Jwt, JwtValidator}
import cats.data.EitherT
import gl.uh.subregistrar.models.{OIdCClaims, SuccessfulTokenResponse}

import scala.concurrent.{ExecutionContext, Future}
import cats.implicits._
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.unmarshalling.Unmarshal
import gl.uh.subregistrar.misc.uriFormat
import play.api.libs.json.JsObject
import black.door.jose.json.playjson.JsonSupport._
import cats.{Applicative, Monad, ~>}
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gl.uh.subregistrar.errors._
import gl.uh.subregistrar.security.UserAuthn
import akka.http.scaladsl.server.Directives.authenticateOAuth2
import akka.http.scaladsl.server.directives.Credentials.Provided
import com.typesafe.scalalogging.LazyLogging
import gl.uh.subregistrar.Server.userService

class UserService[F[_]](
    tokenSigningKey: Jwk,
    providers: Seq[Provider],
    persistence: PersistenceService[F],
    oIdCService: OIdCService[F]
)(
    implicit F: MonadErrorF[F],
    etToF: UserService.ET ~> F,
    ec: ExecutionContext
) extends LazyLogging {

  def logIn(providerName: String, code: String, redirectUri: String) =
    for {
      provider <- providers.find(_.name == providerName) match {
        case Some(provider) => F.pure(provider)
        case None =>
          F.raiseError[Provider](
            Error(
              "ProviderNotSupported",
              s"try ${providers.map(_.name).mkString(", ")}"
            )
          )
      }

      discovery <- oIdCService.discoveryDoc(provider.iss)

      tokens <- oIdCService.tokenRequest(
        discovery,
        code,
        provider.clientId,
        provider.clientSecret,
        redirectUri
      )

      profile <- oIdCService.userInfo(discovery, tokens.access_token)

      userId <- persistence.upsertUser(profile.sub, providerName, profile.email)

    } yield SuccessfulTokenResponse(
      Jwt.sign(
        Claims(sub = Some(userId), iat = Some(Instant.now)),
        tokenSigningKey
      ),
      tokens.id_token
    )

  val authenticateDirective = authenticateOAuth2(
    "", {
      case Provided(token) =>
        Jwt.validate(token).using(tokenSigningKey).now match {
          case Left(e) =>
            logger.error(e)
            None
          case Right(jwt) => jwt.claims.sub.map(UserAuthn)
        }
      case _ => None
    }
  )

  /*
  def authenticate(token: String): F[UserAuthn] = {
    val check =
      JwtValidator.fromSync[OIdCClaims](Function.unlift { jwt =>
        (for {
          aud <- jwt.claims.aud
          maybeIss = jwt.claims.iss.map(Uri(_).authority)
          requiredAud <- providers
            .find(p => maybeIss.contains(p.iss.authority))
            .map(_.clientId)
        } yield aud == requiredAud) match {
          case Some(true)  => None
          case Some(false) => Some("aud did not match")
          case None        => Some("some field was missing")
        }
      })

    for {
      token <- etToF(
        EitherT(
          Jwt.validate[OIdCClaims](
            token,
            keyResolver,
            check,
            JwtValidator.defaultValidator()
          )
        ).leftMap(e => Error(unauthenticated, e))
      )

      userId <- persistence.upsertUser(
        token.claims.unregistered.sub,
        token.claims.unregistered.iss.toString,
        token.claims.unregistered.email.getOrElse("")
      )
    } yield UserAuthn(userId)
  }

 */
}

object UserService {
  type ET[A] = EitherT[Future, Error, A]
}

case class Provider(
    name: String,
    iss: Uri,
    clientId: String,
    clientSecret: String
)

class OIdCKeyResolver(implicit system: ActorSystem)
    extends KeyResolver[Claims[OIdCClaims]]
    with PlayJsonSupport {
  private implicit val ec = system.dispatcher

  def resolve(
      header: JwsHeader,
      payload: Claims[OIdCClaims]
  ): EitherT[Future, String, Jwk] =
    for {
      kid <- EitherT.fromOption[Future](header.kid, "id token has no kid")
      iss <- EitherT
        .fromOption[Future](payload.iss.map(Uri(_)), "id token has no iss")
      jwksUri <- EitherT
        .right[String](
          Http()
            .singleRequest(
              Get(
                iss.withPath(iss.path ?/ ".well-known" / "openid-configuration")
              )
            )
            .flatMap(Unmarshal(_).to[JsObject])
        )
        .subflatMap(js =>
          (js \ "jwks_uri").validate[Uri].asEither.left.map(_.toString)
        )

      jwks <- EitherT
        .right[String](
          Http()
            .singleRequest(Get(jwksUri))
            .flatMap(Unmarshal(_).to[JsObject])
        )
        .subflatMap(js =>
          (js \ "keys").validate[Seq[Jwk]].asEither.left.map(_.toString)
        )

      jwk <- EitherT.fromOption[Future](
        jwks.find(_.kid.contains(kid)),
        s"key set at $jwksUri did not contain key '$kid'"
      )
    } yield jwk
}

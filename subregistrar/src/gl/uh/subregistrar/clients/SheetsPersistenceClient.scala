package gl.uh.subregistrar.clients

import java.util.concurrent.Executors

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.client.RequestBuilding._
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.HttpMethods.{POST, PUT}
import akka.http.scaladsl.model.Uri.Path._
import akka.http.scaladsl.model.Uri.{Path, Query}
import akka.http.scaladsl.model.headers.{Authorization, OAuth2BearerToken}
import akka.http.scaladsl.model.{HttpRequest, RequestEntity, Uri}
import akka.http.scaladsl.unmarshalling.Unmarshal
import cats.data.EitherT
import cats.implicits._
import com.google.auth.oauth2.GoogleCredentials
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpplayjson.PlayJsonSupport
import gl.uh.subregistrar.{Server, misc}
import misc.randomString
import gl.uh.subregistrar.Server.F
import gl.uh.subregistrar.errors._
import gl.uh.subregistrar.models.Name
import gl.uh.subregistrar.services.PersistenceService
import play.api.libs.json.{JsArray, JsObject, JsString, Json}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}

// easily the worst class ever
class SheetsPersistenceClient(
    sheetId: String,
    domainPage: String,
    userPage: String,
    credential: GoogleCredentials
)(
    implicit system: ActorSystem,
    F: MonadErrorF[Server.F]
) extends PersistenceService[Server.F]
    with PlayJsonSupport
    with LazyLogging {
  private implicit val ec = system.dispatcher

  def listNames(userId: String) =
    loadNames.map(_.collect {
      case JsString(name) +: JsString(owner) +: _ if owner == userId =>
        Name(name)
    }.toSeq)

  def getNameOwner(name: String) =
    loadNames.map(_.collectFirst {
      case JsString(n) +: JsString(owner) +: _ if n == name => owner
    })

  def registerName(name: String, userId: String): F[Unit] =
    for {
      names <- loadNames
      existingNameIndex = names.indexWhere {
        case JsString(n) +: _ if n == name => true
        case _                             => false
      }
      _ <- if (existingNameIndex != -1) names(existingNameIndex) match {
        case _ +: JsString(owner) +: _ if owner.nonEmpty =>
          F.raiseError[Unit](
            Error(
              nameAlreadyRegistered,
              s"$name is already registered, contact support to appeal a takeover"
            )
          )
        case _ => F.unit
      }
      else F.unit

      (method, range, body) = if (existingNameIndex == -1)
        (POST, /(s"$domainPage!A:C:append"), valueRangeBody(name, userId))
      else
        (
          PUT,
          /(s"$domainPage!B${existingNameIndex + 1}"),
          valueRangeBody(userId)
        )

      entity <- EitherT.right[Error](Marshal(body).to[RequestEntity])

      token <- EitherT.right[Error](googleToken)
      response <- EitherT.right[Error](
        Http().singleRequest(
          HttpRequest(
            method,
            base(/("spreadsheets") / sheetId / "values" ++ range)
              .withQuery(Query("valueInputOption" -> "RAW")),
            entity = entity
          ).addHeader(Authorization(OAuth2BearerToken(token)))
        )
      )

      _ <- {
        response.discardEntityBytes()
        if (response.status.isFailure) {
          logger.error(
            s"failed to update ${range} in google sheet: ${response.status}"
          )
          F.raiseError(Error("Persistence failure", ""))
        } else F.unit
      }

      namesAgain <- loadNames
      dupIndices = namesAgain.zipWithIndex
        .collect { case (JsString(n) +: _, i) if n == name => i + 1 } match {
        case _ +: dups => dups
        case _         => Nil
      }
      _ <- EitherT.right[Error](
        Future.sequence(
          dupIndices.map(i =>
            Http()
              .singleRequest(
                Post(
                  base(
                    /("spreadsheets") / sheetId / "values" / s"$domainPage!A$i:C$i:clear"
                  )
                ).addHeader(Authorization(OAuth2BearerToken(token)))
              )
              .andThen {
                case Success(response) =>
                  response.discardEntityBytes()
                  if (response.status.isFailure)
                    logger.error(
                      s"failed to delete duplicate row $i in google sheet: ${response.status}"
                    )
                case Failure(exception) =>
                  logger.error(
                    s"failed to delete duplicate row $i in google sheet: ${response.status}",
                    exception
                  )
              }
          )
        )
      )
    } yield ()

  def deregisterName(name: String): F[Unit] =
    for {
      names <- loadNames
      i = names.indexWhere {
        case JsString(n) +: _ if n == name => true
        case _                             => false
      } + 1
      token <- EitherT.right[Error](googleToken)
      _ <- EitherT.right[Error](
        Http()
          .singleRequest(
            Post(
              base(
                /("spreadsheets") / sheetId / "values" / s"$domainPage!B$i:clear"
              )
            ).addHeader(Authorization(OAuth2BearerToken(token)))
          )
          .andThen {
            case Success(response) =>
              response.discardEntityBytes()
              if (response.status.isFailure)
                logger.error(s"failed to deregister name ${response.status}")
          }
      )
    } yield ()

  private def base(path: Path) =
    Uri("https://sheets.googleapis.com").withPath(/("v4") ++ path)

  private def loadNames =
    EitherT.right[Error](for {
      token <- googleToken
      reply <- Http().singleRequest(
        Get(base(/("spreadsheets") / sheetId / "values" / s"$domainPage!A:C"))
          .addHeader(Authorization(OAuth2BearerToken(token)))
      )
      values <- Unmarshal(reply)
        .to[JsObject]
        .map(js => (js \ "values").as[JsArray])
    } yield values.value.map(_.as[JsArray].value))

  def upsertUser(sub: String, provider: String, email: String): F[String] =
    for {
      token <- EitherT.right[Error](googleToken)
      reply <- EitherT.right[Error](
        Http().singleRequest(
          Get(base(/("spreadsheets") / sheetId / "values" / s"$userPage!A:D"))
            .addHeader(Authorization(OAuth2BearerToken(token)))
        )
      )

      values <- EitherT.right[Error](
        Unmarshal(reply)
          .to[JsObject]
          .map(js => (js \ "values").as[JsArray].value.map(_.as[JsArray].value))
      )

      maybeIndexAndId = values.zipWithIndex.collectFirst {
        case (JsString(id) +: JsString(s) +: JsString(p) +: _, i)
            if s == sub && p == provider =>
          i + 1 -> id
      }

      id <- maybeIndexAndId match {
        case Some((i, id)) =>
          Http()
            .singleRequest(
              Put(
                base(/("spreadsheets") / sheetId / "values" / s"$userPage!D$i"),
                valueRangeBody(email)
              ).addHeader(Authorization(OAuth2BearerToken(token)))
            )
            .map(_.discardEntityBytes())
          EitherT.rightT[Future, Error](id)
        case None =>
          val id = randomString
          EitherT
            .right[Error](
              Http()
                .singleRequest(
                  Post(
                    base(
                      /("spreadsheets") / sheetId / "values" / s"$userPage!A:D:append"
                    ).withQuery(Query("valueInputOption" -> "RAW")),
                    valueRangeBody(id, sub, provider, email)
                  ).addHeader(Authorization(OAuth2BearerToken(token)))
                )
            )
            .subflatMap { resp =>
              resp.entity.dataBytes
                .runReduce(_ ++ _)
                .map(_.utf8String)
                .andThen { case Success(s) => logger.error(s) }
              if (resp.status.isSuccess())
                Right(id)
              else
                Left(
                  Error("SignUpProblem", s"Unable to save user: ${resp.status}")
                )
            }
      }
    } yield id

  private def valueRangeBody(values: String*) =
    Json.obj("values" -> Json.arr(values))

  private val pool =
    ExecutionContext.fromExecutor(Executors.newCachedThreadPool())

  private val scopedCred =
    credential.createScoped("https://www.googleapis.com/auth/spreadsheets")

  private def googleToken =
    for {
      _ <- Future.apply(scopedCred.refreshIfExpired())(pool)
      token <- Future(scopedCred.getAccessToken)(pool)
    } yield token.getTokenValue

}

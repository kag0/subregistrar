package gl.uh.subregistrar.services

import akka.http.scaladsl.model.Uri
import gl.uh.subregistrar.models.{Discovery, SuccessfulTokenResponse, UserInfo}

trait OIdCService[F[_]] {
  def discoveryDoc(iss: Uri): F[Discovery]

  def tokenRequest(
      discovery: Discovery,
      code: String,
      clientId: String,
      clientSecret: String,
      redirectUri: String
  ): F[SuccessfulTokenResponse]

  def userInfo(discovery: Discovery, accessToken: String): F[UserInfo]
}

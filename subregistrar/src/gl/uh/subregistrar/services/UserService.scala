package gl.uh.subregistrar.services

import gl.uh.subregistrar.models.SuccessfulTokenResponse

class UserService[F[_]] {
  def logIn(provider: String, token: SuccessfulTokenResponse) = ???
}

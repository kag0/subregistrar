package gl.uh.subregistrar

import akka.http.scaladsl.marshalling.Marshaller
import akka.http.scaladsl.marshalling.PredefinedToResponseMarshallers
import akka.http.scaladsl.model.StatusCodes
import cats.data.EitherT

package object misc {

  implicit val unitTRM =
    PredefinedToResponseMarshallers.fromStatusCode.compose[Unit](_ =>
      StatusCodes.NoContent
    )
  implicit def eitherTMarshaller[F[_], L, R, B](
      implicit marshaller: Marshaller[F[Either[L, R]], B]
  ): Marshaller[EitherT[F, L, R], B] = marshaller.compose(et => et.value)

}

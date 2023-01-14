package cdp4s.domain.extensions

import cats.Monad
import cats.syntax.functor.*
import cdp4s.domain.Operation

object security {

  def ignoreHTTPSErrors[F[_]](implicit F: Monad[F], op: Operation[F]): F[Unit] = for {
    _ <- op.security.setIgnoreCertificateErrors(ignore = true)
  } yield ()
}

package cdp4s.domain

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._

object Extensions {

  def ignoreHTTPSErrors[F[_]: Monad](implicit op: Operation[F]): F[Unit] = for {
    _ <- op.security.enable // TODO is this required?
    _ <- op.security.setIgnoreCertificateErrors(ignore = true)
  } yield ()
}

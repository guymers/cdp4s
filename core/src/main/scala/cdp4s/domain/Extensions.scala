package cdp4s.domain

import java.net.URI
import java.net.URISyntaxException

import cats.Monad
import cats.syntax.functor._
import cats.syntax.flatMap._
import cdp4s.domain.event.SecurityEvent
import cdp4s.domain.model.Security.CertificateErrorAction

object Extensions {

  def ignoreAllHTTPSErrors[F[_]: Monad](implicit O: Operation[F]): F[Unit] = {
    ignoreHTTPSErrors(_ => true)
  }

  def ignoreHTTPSErrorsForHosts[F[_]: Monad](hosts: Set[String])(implicit O: Operation[F]): F[Unit] = {
    ignoreHTTPSErrors { e =>
      try {
        hosts.contains(new URI(e.requestURL).getHost)
      } catch {
        case _: URISyntaxException => false
      }
    }
  }

  def ignoreHTTPSErrors[F[_]: Monad](
    ignoreError: SecurityEvent.CertificateError => Boolean
  )(implicit op: Operation[F]): F[Unit] = for {
    _ <- op.security.enable
    _ <- op.security.setOverrideCertificateErrors(`override` = true)
    _ <- op.event.onEvent {
      case e: SecurityEvent.CertificateError =>
        val action = if (ignoreError(e)) CertificateErrorAction.continue else CertificateErrorAction.cancel
        op.security.handleCertificateError(e.eventId, action)
    }
  } yield ()
}

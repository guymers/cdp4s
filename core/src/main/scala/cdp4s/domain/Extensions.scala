package cdp4s.domain

import java.net.URI
import java.net.URISyntaxException

import cdp4s.domain.event.SecurityEvent
import cdp4s.domain.model.Security.CertificateErrorAction
import freestyle.free._

object Extensions {

  def ignoreAllHTTPSErrors[F[_]](implicit O: Operations[F]): FreeS[F, Unit] = {
    ignoreHTTPSErrors(_ => true)
  }

  def ignoreHTTPSErrorsForHosts[F[_]](hosts: Set[String])(implicit O: Operations[F]): FreeS[F, Unit] = {
    ignoreHTTPSErrors { e =>
      try {
        hosts.contains(new URI(e.requestURL).getHost)
      } catch {
        case _: URISyntaxException => false
      }
    }
  }

  def ignoreHTTPSErrors[F[_]](
    ignoreError: SecurityEvent.CertificateError => Boolean
  )(implicit O: Operations[F]): FreeS[F, Unit] = {
    import O.domain._

    for {
      _ <- security.enable
      _ <- security.setOverrideCertificateErrors(`override` = true)
      _ <- O.event.onEvent {
        case e: SecurityEvent.CertificateError =>
          val action = if (ignoreError(e)) CertificateErrorAction.continue else CertificateErrorAction.cancel

          def handleCertificateError[FF[_]](implicit O: Operations[FF]) = {
            for {
              r <- O.domain.security.handleCertificateError(e.eventId, action)
            } yield r
          }
          handleCertificateError[Operations.Op]
      }
    } yield ()
  }
}

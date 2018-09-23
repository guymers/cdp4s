package cdp4s.chrome.http

import java.nio.channels.AsynchronousChannelGroup

import cats.effect.ConcurrentEffect
import cats.effect.Sync
import cats.effect.Timer
import cats.instances.string._
import cats.kernel.Monoid
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.chrome.ChromeHttpException
import fs2.Stream
import io.circe.Decoder
import scodec.bits.ByteVector
import spinoco.fs2.http.HttpClient
import spinoco.fs2.http.HttpRequest
import spinoco.fs2.http.HttpResponse
import spinoco.protocol.http.HostPort
import spinoco.protocol.http.HttpScheme
import spinoco.protocol.http.Uri
import spinoco.protocol.http.Uri.Query
import spinoco.protocol.mime.MediaType

object ChromeHttpClient {

  def apply[F[_]](chromeInstance: ChromeInstance)(implicit
    AG: AsynchronousChannelGroup,
    F: ConcurrentEffect[F],
    T: Timer[F]
  ): F[ChromeHttpClient[F]] = {
    spinoco.fs2.http.client[F]().map { httpClient =>
      new ChromeHttpClient[F](chromeInstance, httpClient)
    }
  }
}

class ChromeHttpClient[F[_]](
  instance: ChromeInstance,
  val httpClient: HttpClient[F] // TODO dont expose
)(implicit F: Sync[F]) {
  import Uri.Path.Root

  def version(): F[ChromeVersion] = {
    val request = createRequest(Root / "json" / "version")
    json(request)
  }

  def listTabs(): F[Vector[ChromeTab]] = {
    val request = createRequest(Root / "json" / "list")
    json(request)
  }

  def newTab(): F[ChromeTab] = {
    val request = createRequest(Root / "json" / "new")
    json(request)
  }

  def activateTab(id: ChromeTabId): F[Unit] = {
    val request = createRequest(Root / "json" / "activate" / id.id)

    // returns a string 'Target activated' which is not valid json
    sendRequest(request).compile.drain
  }

  def closeTab(id: ChromeTabId): F[Unit] = {
    val request = createRequest(Root / "json" / "close" / id.id)

    // returns a string 'Target is closing' which is not valid json
    sendRequest(request).compile.drain
  }

  private def createRequest(path: Uri.Path) = {
    val uri = Uri(HttpScheme.HTTP, HostPort(instance.host, Some(instance.port)), path, Query.empty)
    HttpRequest.get[F](uri)
  }

  private def json[T : Decoder](request: HttpRequest[F]): F[T] = {

    sendRequest(request)
      .flatMap { response =>
        if (response.contentType.map(_.mediaType).contains(MediaType.`application/json`)) response.bodyAsByteVectorStream
        else Stream.raiseError(ChromeHttpException.NonJsonContentType(response.contentType))
      }
      .compile
      .foldMonoid
      .flatMap { bytes =>
        cdp4s.circe.parser.decodeByteBuffer[T](bytes.toByteBuffer)
          .leftMap[Throwable](err => ChromeHttpException.ClientResponseException(err, bytes.decodeUtf8.toOption.getOrElse("")))
          .raiseOrPure
      }
  }

  private def sendRequest(request: HttpRequest[F]) = {
    httpClient.request(request).flatMap { response =>
      if (response.header.status.isSuccess) Stream.emit(response)
      else Stream.eval {
        response.body
          .through(fs2.text.utf8Decode)
          .take(256)
          .compile
          .foldMonoid
          .flatMap { bodyFragment =>
            F.raiseError[HttpResponse[F]] {
              ChromeHttpException.UnsuccessfulResponse(response.header, bodyFragment)
            }
          }
      }
    }
  }

  // TODO laws
  private implicit val byteVectorMonoid: Monoid[ByteVector] = new Monoid[ByteVector] {
    override val empty: ByteVector = ByteVector.empty
    override def combine(x: ByteVector, y: ByteVector): ByteVector = x ++ y
  }
}

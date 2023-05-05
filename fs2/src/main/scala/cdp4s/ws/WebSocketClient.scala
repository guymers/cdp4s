package cdp4s.ws

import cdp4s.Runtime
import cats.Monad
import cats.effect.kernel.Async
import cats.effect.kernel.Deferred
import cats.effect.kernel.Resource
import cats.effect.std.Queue
import cats.effect.syntax.spawn.*
import cats.syntax.apply.*
import cats.syntax.functor.*
import cats.syntax.monadError.*
import cdp4s.chrome.WebSocketException
import fs2.Stream

import java.net.http.HttpClient
import java.net.http.HttpTimeoutException
import java.net.http.WebSocket
import java.net.http.WebSocketHandshakeException
import java.nio.ByteBuffer
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage
import java.util.concurrent.Executor
import scala.collection.mutable
import scala.collection.mutable.ListBuffer
import scala.concurrent.duration.*

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
trait WebSocketClient[F[_]] {
  def inbound: Stream[F, WebSocketClient.Data]
  def sendText(text: String, last: Boolean = true): F[Unit]
  def sendBinary(data: ByteBuffer, last: Boolean = true): F[Unit]
}

object WebSocketClient {

  final case class Options(
    bufferCapacity: Int,
    connectTimeout: Duration,
    executor: Option[Executor],
  )
  object Options {
    val defaults: Options = Options(
      bufferCapacity = 1024,
      connectTimeout = 15.seconds,
      executor = None,
    )
  }

  def create[F[_]](uri: WsUri, options: Options)(implicit F: Async[F], R: Runtime[F]): Resource[F, WebSocketClient[F]] = {
    val connectTimeout = java.time.Duration.ofMillis(options.connectTimeout.toMillis)

    val _clientBuilder = HttpClient.newBuilder()
      .connectTimeout(connectTimeout)
    val clientBuilder = options.executor match {
      case None => _clientBuilder
      case Some(executor) => _clientBuilder.executor(executor)
    }

    for {
      messageQueue <- Resource.eval(Queue.unbounded[F, WebSocketListenerMessage])
      webSocket <- {
        val acquire = F.fromCompletableFuture(F.delay {
          clientBuilder.build()
            .newWebSocketBuilder
            .connectTimeout(connectTimeout)
            .buildAsync(uri.value, listener(messageQueue))
        }).adaptError {
          case e: HttpTimeoutException =>
            new WebSocketException.Timeout(e.getMessage)
          case e: WebSocketHandshakeException =>
            val response = e.getResponse
            val body = response.body() match {
              case s: String => s
              case _ => "<unknown>"
            }
            new WebSocketException.Connection(response.statusCode(), body)
          // case e: IOException =>
        }

        Resource.make(acquire) { webSocket =>
          val sendClose = if (!webSocket.isOutputClosed) {
            F.fromCompletableFuture(F.delay {
              webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "")
            }).as(())
          } else {
            F.unit
          }
          val abort = F.delay {
            if (!webSocket.isOutputClosed) webSocket.abort() else ()
          }
          sendClose *> abort
        }
      }
      open <- Resource.eval(Deferred[F, Unit])
      closed <- Resource.eval(Deferred[F, Either[Throwable, WebSocketListenerMessage.Close]])
      queue <- Resource.eval(Queue.bounded[F, Option[Data]](options.bufferCapacity))
      _ <- Stream.fromQueueUnterminated(messageQueue)
        .evalTap {
          case WebSocketListenerMessage.Open => open.complete(()) *> F.delay(webSocket.request(1))
          case WebSocketListenerMessage.Text(data) => queue.offer(Some(Data.Text(data)))
          case WebSocketListenerMessage.Binary(data) => queue.offer(Some(Data.Binary(data)))
          case WebSocketListenerMessage.Ping(_) => F.delay(webSocket.request(1))
          case WebSocketListenerMessage.Pong(_) => F.delay(webSocket.request(1))
          case WebSocketListenerMessage.Error(t) => F.raiseError[Unit](t)
          case c: WebSocketListenerMessage.Close => closed.complete(Right(c)).as(()) *> queue.offer(None)
        }.drain.handleErrorWith { t =>
          Stream.eval {
            closed.complete(Left(t)) *> queue.offer(None)
          }.flatMap(_ => Stream.empty)
        }.compile.drain.background
    } yield {

      new WebSocketClient[F] {
        override def inbound: Stream[F, WebSocketClient.Data] = {
          fs2.Stream.fromQueueNoneTerminated(queue) mergeHaltBoth Stream.eval(closed.get).flatMap {
            case Left(e) => Stream.raiseError(e)
            case Right(_) => Stream.empty
          }
        }

        @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
        override def sendText(text: String, last: Boolean = true): F[Unit] = {
          open.get *> F.fromCompletableFuture(F.delay {
            webSocket.sendText(text, last)
          }).as(())
        }

        @SuppressWarnings(Array("org.wartremover.warts.DefaultArguments"))
        override def sendBinary(data: ByteBuffer, last: Boolean = true): F[Unit] = {
          open.get *> F.fromCompletableFuture(F.delay {
            webSocket.sendBinary(data, last)
          }).as(())
        }
      }
    }
  }

  @SuppressWarnings(Array(
    "org.wartremover.warts.MutableDataStructures",
    "org.wartremover.warts.Null",
    "org.wartremover.warts.Var",
  ))
  private def listener[F[_]](
    q: Queue[F, WebSocketListenerMessage],
  )(implicit F: Monad[F], R: Runtime[F]) = new WebSocket.Listener {

    override def onOpen(webSocket: WebSocket): Unit = {
      offerSync(WebSocketListenerMessage.Open)
    }

    private var textParts = new mutable.StringBuilder
    private var textFuture = new CompletableFuture[Unit]()

    override def onText(webSocket: WebSocket, data: CharSequence, last: Boolean): CompletionStage[?] = {
      val _ = textParts.append(data)
      webSocket.request(1)

      if (last) {
        val msg = WebSocketListenerMessage.Text(textParts.toString)

        val textFuture_ = textFuture
        def complete(): Unit = {
          val _ = textFuture_.complete(())
        }

        textParts = new mutable.StringBuilder
        textFuture = new CompletableFuture[Unit]()

        offer(msg).thenApply[Unit](_ => complete()).exceptionally(_ => complete())
      } else {
        textFuture
      }
    }

    private var binaryParts = ListBuffer.empty[ByteBuffer]
    private var binaryFuture = new CompletableFuture[Unit]()

    override def onBinary(webSocket: WebSocket, data: ByteBuffer, last: Boolean): CompletionStage[?] = {
      val _ = binaryParts.append(data)
      webSocket.request(1)

      if (last) {
        val data = binaryParts.foldLeft(Array.emptyByteArray) { case (arr, buf) => arr ++ byteBufToArray(buf) }
        val msg = WebSocketListenerMessage.Binary(data)

        val binaryFuture_ = binaryFuture
        def complete(): Unit = {
          val _ = binaryFuture_.complete(())
        }

        binaryParts = ListBuffer.empty[ByteBuffer]
        binaryFuture = new CompletableFuture[Unit]()

        offer(msg).thenApply[Unit](_ => complete()).exceptionally(_ => complete())
      } else {
        binaryFuture
      }
    }

    override def onPing(webSocket: WebSocket, message: ByteBuffer): CompletionStage[?] = {
      offer(WebSocketListenerMessage.Ping(byteBufToArray(message)))
    }

    override def onPong(webSocket: WebSocket, message: ByteBuffer): CompletionStage[?] = {
      offer(WebSocketListenerMessage.Pong(byteBufToArray(message)))
    }

    override def onClose(webSocket: WebSocket, statusCode: Int, reason: String): CompletionStage[?] = {
      offer(WebSocketListenerMessage.Close(statusCode, reason))
    }

    override def onError(webSocket: WebSocket, error: Throwable): Unit = {
      offerSync(WebSocketListenerMessage.Error(error))
    }

    private def offer(msg: WebSocketListenerMessage) = R.unsafeRun(_offer(msg))
    private def offerSync(msg: WebSocketListenerMessage) = R.unsafeRunSync(_offer(msg))
    private def _offer(msg: WebSocketListenerMessage) = q.offer(msg).as(())

    private def byteBufToArray(buffer: ByteBuffer) = {
      if (buffer.hasArray) {
        val array = buffer.array
        val arrayOffset = buffer.arrayOffset
        java.util.Arrays.copyOfRange(array, arrayOffset + buffer.position(), arrayOffset + buffer.limit())
      } else {
        val bytes = new Array[Byte](buffer.remaining)
        val _ = buffer.get(bytes)
        bytes
      }
    }

  }

  sealed trait WebSocketListenerMessage
  object WebSocketListenerMessage {
    case object Open extends WebSocketListenerMessage

    final case class Text(data: String) extends WebSocketListenerMessage
    final case class Binary(data: Array[Byte]) extends WebSocketListenerMessage
    final case class Ping(data: Array[Byte]) extends WebSocketListenerMessage
    final case class Pong(data: Array[Byte]) extends WebSocketListenerMessage

    final case class Close(statusCode: Int, reason: String) extends WebSocketListenerMessage
    final case class Error(error: Throwable) extends WebSocketListenerMessage
  }

  sealed trait Data
  object Data {
    final case class Text(data: String) extends Data
    final case class Binary(data: Array[Byte]) extends Data
  }
}

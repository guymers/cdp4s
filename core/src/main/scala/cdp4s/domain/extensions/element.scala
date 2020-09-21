package cdp4s.domain.extensions

import cats.Monad
import cats.MonadError
import cats.syntax.applicativeError._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cdp4s.domain.Operation
import cdp4s.domain.handles.ElementHandle
import cdp4s.domain.model.Runtime
import io.circe.syntax._

object element {

  def focus[F[_]: Monad](element: ElementHandle)(implicit op: Operation[F]): F[Unit] = for {
    _ <- op.dom.focus(objectId = element.remoteObject.objectId)
//    _ <- execute.callFunction(
//      element.executionContextId,
//      "element => element.focus()",
//      Seq(remoteObjectToCallArgument(element.remoteObject))
//    )
  } yield ()

  // from https://github.com/GoogleChrome/puppeteer/blob/v1.13.0/lib/JSHandle.js#L162
  def scrollIntoViewIfNeeded[F[_]](
    element: ElementHandle
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[Unit] = {
    // FIXME track if JavaScript is disable EmulationsetScriptExecutionDisabled

    val functionDec =
      s"""element => {
         |  if (!element.isConnected) {
         |    return 'Node is detached from document';
         |  }
         |  if (element.nodeType !== Node.ELEMENT_NODE) {
         |    return 'Node is not of type HTMLElement';
         |  }
         |  element.scrollIntoView({block: 'center', inline: 'center', behavior: 'instant'});
         |  return false;
         |}
       """.stripMargin

    for {
      result <- execute.callFunction(
        element.executionContextId,
        functionDec,
        Vector(remoteObjectToCallArgument(element.remoteObject))
      )
      _ <- {
        if (result.`type` == Runtime.RemoteObject.Type.string) {
          result.value.flatMap(_.asString) match {
            case None => F.unit
            case Some(v) => F.raiseError(new RuntimeException(v)) // FIXME specific error
          }
        } else F.unit
      }
    } yield ()
  }

  // https://github.com/GoogleChrome/puppeteer/blob/v1.13.0/lib/JSHandle.js#L191
  def clickablePoint[F[_]](
    element: ElementHandle,
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[(Double, Double)] = for {
    quads <- op.dom.getContentQuads(objectId = element.remoteObject.objectId).handleError { _ =>
      Vector.empty // FIXME dont just ignore the error
    }
    // Filter out quads that have too small area to click into.
    validQuads = quads.flatMap(Quadrilateral.fromDomQuad(_).toList).filter(_.area > 1)
    // FIXME specific error
    quad <- validQuads.headOption match {
      case None => F.raiseError(new RuntimeException("Node is either not visible or not an HTMLElement"))
      case Some(q) => F.pure(q)
    }
  } yield {
    val middle = quad.middle
    (middle.x, middle.y)
  }

  private def remoteObjectToCallArgument(remoteObject: Runtime.RemoteObject): Runtime.CallArgument = {
    remoteObject.objectId match {
      case None => Runtime.CallArgument(value = Some(remoteObject.asJson))
      case Some(objectId) => Runtime.CallArgument(objectId = Some(objectId))
    }
  }
}

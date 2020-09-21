package cdp4s.domain.extensions

import cats.MonadError
import cats.syntax.alternative._
import cats.syntax.applicativeError._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.traverse._
import cdp4s.domain.Operation
import cdp4s.domain.handles.ElementHandle
import cdp4s.domain.model.Runtime
import io.circe.syntax._

object selector {

  private val querySelectorFunctionDeclaration = "selector => document.querySelector(selector)"
  private val querySelectorAllFunctionDeclaration = "selector => Array.from(document.querySelectorAll(selector))"

  // https://github.com/GoogleChrome/puppeteer/blob/v1.13.0/lib/DOMWorld.js#L506
  private val isVisibleDeclaration = {
    """node => {
      |  function hasVisibleBoundingBox() {
      |    const rect = element.getBoundingClientRect();
      |    return !!(rect.top || rect.bottom || rect.width || rect.height);
      |  }
      |
      |  const element = (node.nodeType === Node.TEXT_NODE ? node.parentElement : node);
      |  const style = window.getComputedStyle(element);
      |  return style && style.visibility !== 'hidden' && hasVisibleBoundingBox();
      |}
    """.stripMargin
  }

  // TODO hide the default execution context in state

  def find[F[_]](
    executionContextId: Runtime.ExecutionContextId,
    selector: String
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[Option[ElementHandle]] = for {
    remoteObject <- execute.callFunction[F](
      executionContextId,
      querySelectorFunctionDeclaration,
      Vector(Runtime.CallArgument(Some(selector.asJson)))
    )
    elementHandle <- {
      if (remoteObject.subtype.contains(Runtime.RemoteObject.Subtype.node)) {
        F.pure { Option(ElementHandle(executionContextId, remoteObject)) }
      } else {
        releaseObject(remoteObject).map((_: Unit) => None: Option[ElementHandle])
      }
    }
  } yield elementHandle

  def findAll[F[_]](
    executionContextId: Runtime.ExecutionContextId,
    selector: String
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[Seq[ElementHandle]] = for {
    remoteObject <- execute.callFunction[F](
      executionContextId,
      querySelectorAllFunctionDeclaration,
      Vector(Runtime.CallArgument(Some(selector.asJson)))
    )

    properties <- remoteObject.objectId match {
      case None => F.pure(Vector.empty[cdp4s.domain.model.Runtime.PropertyDescriptor])
      case Some(objectId) => op.runtime.getProperties(objectId, ownProperties = Some(true)).map(_.result)
    }

    elementHandles <- {
      val (releaseObjects, results) = properties.flatMap { property =>
        property.value.map { value =>
         if (property.enumerable && value.subtype.contains(Runtime.RemoteObject.Subtype.node)) {
            ElementHandle(executionContextId, value).asRight
          } else {
            releaseObject(remoteObject).asLeft
          }
        }.toList
      }.separate

      releaseObjects.sequence >> F.pure(results)
    }
  } yield elementHandles

  // from lib/helper.js releaseObject
  private def releaseObject[F[_]](
    remoteObject: Runtime.RemoteObject
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[Unit] = {
    remoteObject.objectId match {
      case None => F.unit
      case Some(objectId) =>
        // Exceptions might happen in case of a page been navigated or closed.
        // Swallow these since they are harmless and we don't leak anything in this case.
        op.runtime.releaseObject(objectId).handleError { _ => () }
    }
  }

  def isVisible[F[_]](
    elementHandle: ElementHandle,
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[Boolean] = for {
    result <- execute.callFunction[F](
      elementHandle.executionContextId,
      isVisibleDeclaration,
      Vector(remoteObjectToCallArgument(elementHandle.remoteObject))
    )
    visible = result.value.flatMap(_.asBoolean).getOrElse(false)
    _ <- releaseObject(result) // TODO Resource?
  } yield visible

  // TODO duplicated
  private def remoteObjectToCallArgument(remoteObject: Runtime.RemoteObject): Runtime.CallArgument = {
    remoteObject.objectId match {
      case None => Runtime.CallArgument(value = Some(remoteObject.asJson))
      case Some(objectId) => Runtime.CallArgument(objectId = Some(objectId))
    }
  }
}

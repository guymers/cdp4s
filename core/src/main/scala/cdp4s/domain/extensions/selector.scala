package cdp4s.domain.extensions

import scala.util.control.NonFatal

import cats.instances.either._
import cats.instances.list._
import cats.syntax.alternative._
import cats.syntax.either._
import cats.syntax.flatMap._
import cats.syntax.traverse._
import cdp4s.domain.Operations
import cdp4s.domain.handles.ElementHandle
import cdp4s.domain.model.Runtime
import freestyle.free._
import io.circe.syntax._

object selector {

  private val querySelectorFunctionDeclaration = "selector => document.querySelector(selector)"
  private val querySelectorAllFunctionDeclaration = "selector => Array.from(document.querySelectorAll(selector))"

  // TODO hide the default execution context in state?

  def find[F[_]](
    executionContextId: Runtime.ExecutionContextId,
    selector: String
  )(implicit O: Operations[F]): FreeS[F, Option[ElementHandle]] = {
    for {
      remoteObject <- execute.callFunction(
        executionContextId,
        querySelectorFunctionDeclaration,
        Seq(Runtime.CallArgument(Some(selector.asJson)))
      )
      elementHandle <- (
        if (remoteObject.subtype.contains(Runtime.RemoteObject.Subtype.node)) {
          FreeS.pure { Some(ElementHandle(executionContextId, remoteObject)) }
        } else {
          FreeS.liftPar { releaseObject(remoteObject).map(_ => None) }
        }
      ) : FreeS[F, Option[ElementHandle]]
    } yield elementHandle
  }

  def findAll[F[_]](
    executionContextId: Runtime.ExecutionContextId,
    selector: String
  )(implicit O: Operations[F]): FreeS[F, Seq[ElementHandle]] = {
    import O.domain._

    for {
      remoteObject <- execute.callFunction(
        executionContextId,
        querySelectorAllFunctionDeclaration,
        Seq(Runtime.CallArgument(Some(selector.asJson)))
      )

      properties <- (remoteObject.objectId match {
        case None => FreeS.pure(Seq.empty)
        case Some(objectId) => runtime.getProperties(objectId, ownProperties = Some(true)).map(_.result)
      }) : FreeS[F, Seq[Runtime.PropertyDescriptor]]

      elementHandles <- {
        val (releaseObjects, results) = properties.toList.flatMap { property =>
          property.value.map { value =>
           if (property.enumerable && value.subtype.contains(Runtime.RemoteObject.Subtype.node)) {
              ElementHandle(executionContextId, value).asRight
            } else {
              releaseObject(remoteObject).asLeft
            }
          }.toList
        }.separate

        FreeS.liftPar(releaseObjects.sequence) >> FreeS.pure[F, List[ElementHandle]](results)
      }
    } yield elementHandles
  }

  // from lib/helper.js releaseObject
  private def releaseObject[F[_]](
    remoteObject: Runtime.RemoteObject
  )(implicit O: Operations[F]): FreeS.Par[F, Unit] = {
    remoteObject.objectId match {
      case None => O.util.pure(())
      case Some(objectId) =>
        // Exceptions might happen in case of a page been navigated or closed.
        // Swallow these since they are harmless and we don't leak anything in this case.
        O.error.handle(doReleaseObject[Operations.Op](objectId), {
          case NonFatal(_) => ()
        })
    }
  }

  private def doReleaseObject[F[_]](objectId: Runtime.RemoteObjectId)(implicit O: Operations[F]) = {
    for {
      r <- O.domain.runtime.releaseObject(objectId)
    } yield r
  }
}

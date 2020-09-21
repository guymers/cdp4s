package cdp4s.domain.extensions

import cats.MonadError
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import cdp4s.domain.Operation
import cdp4s.domain.model.Runtime
import cdp4s.domain.model.Runtime.ExceptionDetails

object execute {

  def callFunction[F[_]](
    executionContextId: Runtime.ExecutionContextId,
    functionDeclaration: String,
    arguments: Vector[Runtime.CallArgument]
  )(implicit F: MonadError[F, Throwable], op: Operation[F]): F[Runtime.RemoteObject] = for {
    functionResult <- op.runtime.callFunctionOn(
      functionDeclaration,
      arguments = Some(arguments),
      returnByValue = Some(false),
      awaitPromise = Some(true),
      executionContextId = Some(executionContextId)
    )

    remoteObject <- functionResult.exceptionDetails match {
      case None => F.pure(functionResult.result)
      case Some(exceptionDetails) => F.raiseError(RuntimeExceptionDetailsException(exceptionDetails))
    }
  } yield remoteObject

}

final case class RuntimeExceptionDetailsException(details: ExceptionDetails) extends RuntimeException(
  show"${details.text} ${details.lineNumber}:${details.columnNumber}"
)

package cdp4s.domain.extensions

import cats.Monad
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.show._
import cdp4s.domain.Operation
import cdp4s.domain.model.Runtime
import cdp4s.domain.model.Runtime.ExceptionDetails

object execute {

  def callFunction[F[_]: Monad](
    executionContextId: Runtime.ExecutionContextId,
    functionDeclaration: String,
    arguments: Seq[Runtime.CallArgument]
  )(implicit op: Operation[F]): F[Runtime.RemoteObject] = for {
    functionResult <- op.runtime.callFunctionOn(
      functionDeclaration,
      arguments = Some(arguments),
      returnByValue = Some(false),
      awaitPromise = Some(true),
      executionContextId = Some(executionContextId)
    )

    remoteObject <- functionResult.exceptionDetails match {
      case None => op.util.pure(functionResult.result)
      case Some(exceptionDetails) => op.util.fail[Runtime.RemoteObject](RuntimeExceptionDetailsException(exceptionDetails))
    }
  } yield remoteObject

}

final case class RuntimeExceptionDetailsException(details: ExceptionDetails) extends RuntimeException(
  show"${details.text} ${details.lineNumber}:${details.columnNumber}"
)

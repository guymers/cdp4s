package cdp4s.domain.extensions

import cdp4s.domain.Operations
import cdp4s.domain.model.Runtime
import cdp4s.domain.model.Runtime.ExceptionDetails
import freestyle.free._

object execute {

  def callFunction[F[_]](
    executionContextId: Runtime.ExecutionContextId,
    functionDeclaration: String,
    arguments: Seq[Runtime.CallArgument]
  )(implicit O: Operations[F]): FreeS[F, Runtime.RemoteObject] = {
    import O.domain._

    for {
      functionResult <- runtime.callFunctionOn(
        functionDeclaration,
        arguments = Some(arguments),
        returnByValue = Some(false),
        awaitPromise = Some(true),
        executionContextId = Some(executionContextId)
      )

      remoteObject <- (functionResult.exceptionDetails match {
        case None => FreeS.pure(functionResult.result)
        case Some(exceptionDetails) => O.util.fail[Runtime.RemoteObject](RuntimeExceptionDetailsException(exceptionDetails))
      }) : FreeS[F, Runtime.RemoteObject]
    } yield remoteObject
  }

}

case class RuntimeExceptionDetailsException(details: ExceptionDetails) extends RuntimeException(
  s"${details.text} ${details.lineNumber}:${details.columnNumber}"
)

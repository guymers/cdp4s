package cdp4s.exception

abstract class CDP4sException(
  msg: String,
  cause: Option[Throwable] = None
) extends RuntimeException(msg, cause.orNull)

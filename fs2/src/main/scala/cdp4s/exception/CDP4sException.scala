package cdp4s.exception

@SuppressWarnings(Array("org.wartremover.warts.DefaultArguments", "org.wartremover.warts.Null"))
abstract class CDP4sException(
  msg: String,
  cause: Option[Throwable] = None,
) extends RuntimeException(msg, cause.orNull)

package cdp4s.domain

import freestyle.free._

@module trait Operations {
  val domain: Domains

  val error: Errors
  val event: Events
  val util: Util
}

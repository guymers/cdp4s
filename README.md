A Scala API for the [DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/) which
allows programmatic control over a Chromium based browser.

The API is exposed as a [free monad](https://typelevel.org/cats/datatypes/freemonad.html) and is
generated from the protocol JSON schema exposed by Chromium. Currently it is tracking 
`Chromium 69.0.3497.100`. To generate an API for a different version, run a headless Chromium in
the backround via `chromium --remote-debugging-port=9222 --headless --disable-gpu` and store its
protocol schema `curl -s http://localhost:9222/json/protocol > project/protocol.json`.

The entry point to the API is exposed via the `Operations` module. This module contains four groups:
- domain - process access to all API's exposed via the DevTools Protocol
- error - exposes ways to handle errors
- event - ways to handle events returned via the DevTools Protocol
- util - miscellaneous utility methods

### Examples

See example directory.

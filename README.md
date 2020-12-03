A basic Scala API for the [DevTools Protocol](https://chromedevtools.github.io/devtools-protocol/) which
allows programmatic control over a Chromium based browser.

The API is exposed in the tagless final style and is generated from the protocol JSON schema exposed by Chromium. Currently it is tracking
`Chromium 87.0.4280.66`. To generate an API for a different version, run a headless Chromium in the background via `chromium --remote-debugging-port=9222 --headless --disable-gpu` and store its protocol schema `curl -s http://localhost:9222/json/protocol > project/protocol.json`.

The entry point to the API is exposed via the `Operation` trait.

### Examples

See example directory.

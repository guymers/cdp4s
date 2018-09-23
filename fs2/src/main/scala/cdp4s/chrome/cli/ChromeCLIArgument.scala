package cdp4s.chrome.cli

import java.nio.file.Path

abstract class ChromeCLIArgument(name: String, value: => Option[String]) {
  val flag: String = name concat value.map(v => "=" concat v).getOrElse("")
}

object ChromeCLIArgument {
  case object DisableBackgroundNetworking extends ChromeCLIArgument("--disable-background-networking", None)
  case object DisableBackgroundTimerThrottling extends ChromeCLIArgument("--disable-background-timer-throttling", None)
  case object DisableBreakpad extends ChromeCLIArgument("--disable-breakpad", None)
  case object DisableClientSidePhishingDetection extends ChromeCLIArgument("--disable-client-side-phishing-detection", None)
  case object DisableDefaultApps extends ChromeCLIArgument("--disable-default-apps", None)
  case object DisableDevShmUsage extends ChromeCLIArgument("--disable-dev-shm-usage", None)
  case object DisableExtensions extends ChromeCLIArgument("--disable-extensions", None)
  case object DisableGpu extends ChromeCLIArgument("--disable-gpu", None)
  case object DisableHangMonitor extends ChromeCLIArgument("--disable-hang-monitor", None)
  case object DisablePopupBlocking extends ChromeCLIArgument("--disable-popup-blocking", None)
  case object DisablePromptOnRepost extends ChromeCLIArgument("--disable-prompt-on-repost", None)
  case object DisableSync extends ChromeCLIArgument("--disable-sync", None)
  case object DisableTranslate extends ChromeCLIArgument("--disable-translate", None)
  case object EnableAutomation extends ChromeCLIArgument("--enable-automation", None)
  case object EnableDevtoolsExperiments extends ChromeCLIArgument("--enable-devtools-experiments", None)
  case object Headless extends ChromeCLIArgument("--headless", None)
  case object HideScrollbars extends ChromeCLIArgument("--hide-scrollbars", None)
  case object MetricsRecordingOnly extends ChromeCLIArgument("--metrics-recording-only", None)
  case object MuteAudio extends ChromeCLIArgument("--mute-audio", None)
  case object NoFirstRun extends ChromeCLIArgument("--no-first-run", None)
  final case class PasswordStore(store: String) extends ChromeCLIArgument("--password-store", Some(store))
  final case class RemoteDebuggingPort(port: Int) extends ChromeCLIArgument("--remote-debugging-port", Some(port.toString))
  case object SafebrowsingDisableAutoUpdate extends ChromeCLIArgument("--safebrowsing-disable-auto-update", None)
  case object UseMockKeychain extends ChromeCLIArgument("--use-mock-keychain", None)
  final case class UserDataDir(directory: Path) extends ChromeCLIArgument("--user-data-dir", Some(directory.toFile.getAbsolutePath))

  // use the same default and headless arguments as set by puppeteer
  // https://github.com/GoogleChrome/puppeteer/blob/v1.8.0/lib/Launcher.js
  val defaultArguments: Set[ChromeCLIArgument] = Set(
    DisableBackgroundNetworking,
    DisableBackgroundTimerThrottling,
    DisableBreakpad,
    DisableClientSidePhishingDetection,
    DisableDefaultApps,
    DisableDevShmUsage,
    DisableExtensions,
    DisableHangMonitor,
    DisablePopupBlocking,
    DisablePromptOnRepost,
    DisableSync,
    DisableTranslate,
    MetricsRecordingOnly,
    NoFirstRun,
    SafebrowsingDisableAutoUpdate,
    EnableAutomation,
    PasswordStore("basic"),
    UseMockKeychain
  ) ++ Set[ChromeCLIArgument](
    RemoteDebuggingPort(0)
  )

  val headlessArguments: Set[ChromeCLIArgument] = Set(
    Headless,
    DisableGpu,
    HideScrollbars,
    MuteAudio
  )
}

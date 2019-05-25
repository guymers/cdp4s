package cdp4s.chrome.cli

import java.nio.file.Path

import cats.data.NonEmptyList
import cats.instances.string._
import cats.syntax.foldable._

abstract class ChromeCLIArgument(val name: String, value: => Option[String]) extends Product with Serializable {
  val flag: String = name concat value.map(v => "=" concat v).getOrElse("")

  override val hashCode: Int = name.hashCode
  override def equals(o: Any): Boolean = o match {
    case a: ChromeCLIArgument => a.name == this.name
    case _ => false
  }
}

object ChromeCLIArgument {
  case object AutoOpenDevtoolsForTabs extends ChromeCLIArgument("--auto-open-devtools-for-tabs", None)
  case object DisableBackgroundNetworking extends ChromeCLIArgument("--disable-background-networking", None)
  case object DisableBackgroundTimerThrottling extends ChromeCLIArgument("--disable-background-timer-throttling", None)
  case object DisableBackgroundingOccludedWindows extends ChromeCLIArgument("--disable-backgrounding-occluded-windows", None)
  case object DisableBreakpad extends ChromeCLIArgument("--disable-breakpad", None)
  case object DisableClientSidePhishingDetection extends ChromeCLIArgument("--disable-client-side-phishing-detection", None)
  case object DisableDefaultApps extends ChromeCLIArgument("--disable-default-apps", None)
  case object DisableDevShmUsage extends ChromeCLIArgument("--disable-dev-shm-usage", None)
  case object DisableExtensions extends ChromeCLIArgument("--disable-extensions", None)
  final case class DisableFeatures(features: NonEmptyList[String]) extends ChromeCLIArgument(
    "--disable-features",
    Some(features.mkString_("", ",", ""))
  )
  case object DisableHangMonitor extends ChromeCLIArgument("--disable-hang-monitor", None)
  case object DisableIpcFloodingProtection extends ChromeCLIArgument("--disable-ipc-flooding-protection", None)
  case object DisablePopupBlocking extends ChromeCLIArgument("--disable-popup-blocking", None)
  case object DisablePromptOnRepost extends ChromeCLIArgument("--disable-prompt-on-repost", None)
  case object DisableRendererBackgrounding extends ChromeCLIArgument("--disable-renderer-backgrounding", None)
  case object DisableSync extends ChromeCLIArgument("--disable-sync", None)
  case object EnableAutomation extends ChromeCLIArgument("--enable-automation", None)
  final case class EnableFeatures(features: NonEmptyList[String]) extends ChromeCLIArgument(
    "--enable-features",
    Some(features.mkString_("", ",", ""))
  )
  final case class ForceColorProfile(profile: String) extends ChromeCLIArgument("--force-color-profile", Some(profile))
  case object Headless extends ChromeCLIArgument("--headless", None)
  case object HideScrollbars extends ChromeCLIArgument("--hide-scrollbars", None)
  case object MetricsRecordingOnly extends ChromeCLIArgument("--metrics-recording-only", None)
  case object MuteAudio extends ChromeCLIArgument("--mute-audio", None)
  case object NoFirstRun extends ChromeCLIArgument("--no-first-run", None)
  case object NoSandbox extends ChromeCLIArgument("--no-sandbox", None)
  final case class PasswordStore(store: String) extends ChromeCLIArgument("--password-store", Some(store))
  final case class RemoteDebuggingPort(port: Int) extends ChromeCLIArgument("--remote-debugging-port", Some(port.toString))
  case object SafebrowsingDisableAutoUpdate extends ChromeCLIArgument("--safebrowsing-disable-auto-update", None)
  case object UseMockKeychain extends ChromeCLIArgument("--use-mock-keychain", None)
  final case class UserDataDir(directory: Path) extends ChromeCLIArgument("--user-data-dir", Some(directory.toFile.getAbsolutePath))

  // use the same default and headless arguments as set by puppeteer
  // https://github.com/GoogleChrome/puppeteer/blob/v1.12.2/lib/Launcher.js
  val defaultArguments: Set[ChromeCLIArgument] = Set(
    DisableBackgroundNetworking,
    EnableFeatures(NonEmptyList.of("NetworkService", "NetworkServiceInProcess")),
    DisableBackgroundTimerThrottling,
    DisableBackgroundingOccludedWindows,
    DisableBreakpad,
    DisableClientSidePhishingDetection,
    DisableDefaultApps,
    DisableDevShmUsage,
    DisableExtensions,
    DisableFeatures(NonEmptyList.of("site-per-process", "TranslateUI")),
    DisableHangMonitor,
    DisableIpcFloodingProtection,
    DisablePopupBlocking,
    DisablePromptOnRepost,
    DisableRendererBackgrounding,
    DisableSync,
    ForceColorProfile("srgb"),
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
    HideScrollbars,
    MuteAudio
  )
}

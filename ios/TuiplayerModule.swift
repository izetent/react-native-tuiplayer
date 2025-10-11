import Foundation
import TUIPlayerCore

@objcMembers
class TuiplayerModule: NSObject {
  static let shared = TuiplayerModule()

  func initialize(with config: [String: Any]?) {
    let enableLog = (config?["enableLog"] as? Bool) ?? false
    let licenseUrl = config?["licenseUrl"] as? String
    let licenseKey = config?["licenseKey"] as? String

    DispatchQueue.main.async {
      let playerConfig = TUIPlayerConfig()
      playerConfig.enableLog = enableLog
      if let licenseUrl, !licenseUrl.isEmpty, let licenseKey, !licenseKey.isEmpty {
        playerConfig.licenseUrl = licenseUrl
        playerConfig.licenseKey = licenseKey
      }
      TUIPlayerCore.shareInstance().setPlayerConfig(playerConfig)
    }
  }
}

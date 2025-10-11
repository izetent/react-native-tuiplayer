import Foundation
import React

@objc(TuiplayerShortVideoViewManager)
class TuiplayerShortVideoViewManager: RCTViewManager {
  override static func moduleName() -> String! {
    return "TuiplayerShortVideoView"
  }

  override static func requiresMainQueueSetup() -> Bool {
    return true
  }

  override func view() -> UIView! {
    return TuiplayerShortVideoView()
  }
}

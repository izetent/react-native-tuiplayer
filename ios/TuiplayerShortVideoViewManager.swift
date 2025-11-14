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

  private func withView(_ reactTag: NSNumber, block: @escaping (TuiplayerShortVideoView) -> Void) {
    bridge.uiManager.addUIBlock { _, viewRegistry in
      guard let view = viewRegistry?[reactTag] as? TuiplayerShortVideoView else { return }
      block(view)
    }
  }

  @objc func startPlayIndex(_ reactTag: NSNumber, index: NSNumber, smooth: NSNumber?) {
    withView(reactTag) { view in
      view.commandStartPlayIndex(index.intValue, smooth: smooth?.boolValue ?? false)
    }
  }

  @objc func setPlayMode(_ reactTag: NSNumber, mode: NSNumber) {
    withView(reactTag) { view in
      view.commandSetPlayMode(mode.intValue)
    }
  }

  @objc func release(_ reactTag: NSNumber) {
    withView(reactTag) { view in
      view.commandRelease()
    }
  }

  @objc func resume(_ reactTag: NSNumber) {
    withView(reactTag) { view in
      view.commandResume()
    }
  }

  @objc func switchResolution(_ reactTag: NSNumber, resolution: NSNumber, target: NSNumber) {
    withView(reactTag) { view in
      view.commandSwitchResolution(resolution.doubleValue, target: target.intValue)
    }
  }

  @objc func pausePreload(_ reactTag: NSNumber) {
    withView(reactTag) { view in
      view.commandPausePreload()
    }
  }

  @objc func resumePreload(_ reactTag: NSNumber) {
    withView(reactTag) { view in
      view.commandResumePreload()
    }
  }

  @objc func setUserInputEnabled(_ reactTag: NSNumber, enabled: NSNumber) {
    withView(reactTag) { view in
      view.commandSetUserInputEnabled(enabled.boolValue)
    }
  }

  @objc func updateMeta(_ reactTag: NSNumber, index: NSNumber, meta: NSDictionary) {
    withView(reactTag) { view in
      if let payload = meta as? [String: Any] {
        view.updateMeta(index, meta: payload)
      }
    }
  }

  @objc func syncPlaybackState(_ reactTag: NSNumber) {
    withView(reactTag) { view in
      view.commandSyncPlaybackState()
    }
  }
}

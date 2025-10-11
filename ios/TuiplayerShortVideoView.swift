import Foundation
import UIKit
import TUIPlayerCore
import TUIPlayerShortVideo

@objc(TuiplayerShortVideoView)
class TuiplayerShortVideoView: UIView {
  private let shortVideoView = TUIShortVideoView()
  private var isAutoPlayEnabled: Bool = true
  private var currentModels: [TUIPlayerVideoModel] = []
  @objc var autoPlay: NSNumber = true {
    didSet {
      isAutoPlayEnabled = autoPlay.boolValue
      if isAutoPlayEnabled {
        resume()
      } else {
        pause()
      }
    }
  }

  @objc var sources: NSArray? {
    didSet {
      guard let items = sources as? [[String: Any]] else {
        currentModels = []
        shortVideoView.setShortVideoModels([])
        return
      }
      let models = items.compactMap { Self.makeVideoModel(from: $0) }
      currentModels = models
      shortVideoView.setShortVideoModels(models)
      if isAutoPlayEnabled {
        resume()
      }
    }
  }

  override init(frame: CGRect) {
    super.init(frame: frame)
    setup()
  }

  required init?(coder: NSCoder) {
    super.init(coder: coder)
    setup()
  }

  private func setup() {
    backgroundColor = .black
    shortVideoView.translatesAutoresizingMaskIntoConstraints = false
    addSubview(shortVideoView)
    NSLayoutConstraint.activate([
      shortVideoView.topAnchor.constraint(equalTo: topAnchor),
      shortVideoView.leadingAnchor.constraint(equalTo: leadingAnchor),
      shortVideoView.trailingAnchor.constraint(equalTo: trailingAnchor),
      shortVideoView.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])
    shortVideoView.isAutoPlay = true
    shortVideoView.startLoading()
  }

  private func resume() {
    DispatchQueue.main.async {
      self.shortVideoView.resume()
    }
  }

  private func pause() {
    DispatchQueue.main.async {
      self.shortVideoView.pause()
    }
  }

  override func didMoveToWindow() {
    super.didMoveToWindow()
    if window == nil {
      pause()
    } else if isAutoPlayEnabled {
      resume()
    }
  }

  deinit {
    shortVideoView.destoryPlayer()
  }

  private static func makeVideoModel(from dictionary: [String: Any]) -> TUIPlayerVideoModel? {
    let model = TUIPlayerVideoModel()
    if let appId = dictionary["appId"] as? NSNumber {
      model.appId = appId.intValue
    } else if let appId = dictionary["appId"] as? Int {
      model.appId = Int32(appId)
    }

    if let fileId = dictionary["fileId"] as? String {
      model.fileId = fileId
    }

    if let url = dictionary["url"] as? String {
      model.videoUrl = url
    }

    if let cover = dictionary["coverPictureUrl"] as? String {
      model.coverPictureUrl = cover
    }

    if let pSign = dictionary["pSign"] as? String {
      model.pSign = pSign
    }

    if model.fileId.isEmpty && model.videoUrl.isEmpty {
      return nil
    }

    return model
  }
}

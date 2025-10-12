import Foundation
import React
import UIKit
import TUIPlayerCore
import TUIPlayerShortVideo

@objc(TuiplayerShortVideoView)
class TuiplayerShortVideoView: UIView {
  private let shortVideoView = TUIShortVideoView()
  private var isAutoPlayEnabled: Bool = true
  private var currentModels: [TUIPlayerVideoModel] = []
  private var lastEndReachedTotal: Int = -1
  private var lastPageIndex: Int = -1
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
        lastEndReachedTotal = -1
        lastPageIndex = -1
        return
      }
      let models = items.compactMap { Self.makeVideoModel(from: $0) }
      apply(models: models)
    }
  }

  @objc var onEndReached: RCTDirectEventBlock?
  @objc var onPageChanged: RCTDirectEventBlock?

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
    shortVideoView.delegate = self
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

  private func apply(models: [TUIPlayerVideoModel]) {
    let previous = currentModels
    if shouldAppend(previous: previous, current: models) {
      let appended = Array(models.dropFirst(previous.count))
      if !appended.isEmpty {
        shortVideoView.appendShortVideoModels(appended)
      }
    } else {
      shortVideoView.setShortVideoModels(models)
      lastEndReachedTotal = -1
      lastPageIndex = -1
      if isAutoPlayEnabled {
        resume()
      }
    }
    currentModels = models
    if lastPageIndex >= currentModels.count {
      lastPageIndex = currentModels.isEmpty ? -1 : currentModels.count - 1
    }
    if lastPageIndex < 0, !currentModels.isEmpty {
      let resolvedIndex = max(0, min(shortVideoView.currentVideoIndex, currentModels.count - 1))
      notifyPageChanged(index: resolvedIndex)
    }
  }

  private func shouldAppend(
    previous: [TUIPlayerVideoModel],
    current: [TUIPlayerVideoModel]
  ) -> Bool {
    guard !previous.isEmpty else { return false }
    guard current.count >= previous.count else { return false }
    for index in 0..<previous.count {
      if !isSameModel(lhs: previous[index], rhs: current[index]) {
        return false
      }
    }
    return current.count > previous.count
  }

  private func isSameModel(lhs: TUIPlayerVideoModel, rhs: TUIPlayerVideoModel) -> Bool {
    if lhs.appId != rhs.appId {
      return false
    }
    if lhs.fileId != rhs.fileId {
      return false
    }
    if lhs.videoUrl != rhs.videoUrl {
      return false
    }
    return true
  }

  private func notifyPageChanged(index: Int) {
    let total = currentModels.count
    guard total > 0 else { return }
    guard index >= 0, index < total else { return }
    if index == lastPageIndex {
      return
    }
    lastPageIndex = index
    onPageChanged?([
      "index": index,
      "total": total,
    ])
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

extension TuiplayerShortVideoView: TUIShortVideoViewDelegate {
  func scrollViewDidEndDeceleratingIndex(_ videoIndex: NSInteger, videoModel: TUIPlayerDataModel!) {
    notifyPageChanged(index: videoIndex)
  }

  func shortVideoView(_ shortVideoView: TUIShortVideoView, didEndScrollingAnimationWithIndex index: UInt, videoModel: TUIPlayerDataModel!) {
    notifyPageChanged(index: Int(index))
  }

  func scrollToVideoIndex(_ videoIndex: NSInteger, videoModel: TUIPlayerDataModel!) {
    notifyPageChanged(index: videoIndex)
  }

  func onReachLast() {
    let total = currentModels.count
    guard total > 0 else { return }
    if total == lastEndReachedTotal {
      return
    }
    lastEndReachedTotal = total
    onEndReached?([
      "index": shortVideoView.currentVideoIndex,
      "total": total,
    ])
  }
}

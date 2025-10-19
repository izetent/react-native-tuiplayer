import Foundation
import React
import UIKit
import TUIPlayerCore
import TUIPlayerShortVideo

enum VodPlayerCommandError: LocalizedError {
  case playerUnavailable
  case invalidParameter(String)
  case unsupportedCommand(String)

  var errorDescription: String? {
    switch self {
    case .playerUnavailable:
      return "当前播放器尚未就绪，无法执行操作。"
    case .invalidParameter(let name):
      return "缺少或非法的参数：\(name)"
    case .unsupportedCommand(let name):
      return "暂不支持的播放器指令：\(name)"
    }
  }
}

@objc(TuiplayerShortVideoView)
class TuiplayerShortVideoView: UIView {
  private let shortVideoView = TUIShortVideoView()
  private var isAutoPlayEnabled: Bool = true
  private var currentModels: [TUIPlayerVideoModel] = []
  private var vodPlayerByModel: [ObjectIdentifier: TUITXVodPlayer] = [:]
  private var currentVodPlayerIdentifier: ObjectIdentifier?
  private var lastVodStatus: TUITXVodPlayerStatus = .TUITXVodPlayerStatusUnload
  @objc var onVodEvent: RCTDirectEventBlock?
  private var lastEndReachedTotal: Int = -1
  private var lastPageIndex: Int = -1
  @objc var onTopReached: RCTDirectEventBlock?
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
    for player in vodPlayerByModel.values {
      player.removeDelegate(self)
    }
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
    let identifiers = Set(currentModels.map { ObjectIdentifier($0) })
    pruneVodPlayers(keeping: identifiers)
    if lastPageIndex >= currentModels.count {
      lastPageIndex = currentModels.isEmpty ? -1 : currentModels.count - 1
    }
    if lastPageIndex < 0, !currentModels.isEmpty {
      let resolvedIndex = max(0, min(shortVideoView.currentVideoIndex, currentModels.count - 1))
      notifyPageChanged(index: resolvedIndex)
    }
  }

  private func refreshModels() {
    shortVideoView.setShortVideoModels(currentModels)
    lastEndReachedTotal = -1
    if lastPageIndex >= currentModels.count {
      lastPageIndex = currentModels.isEmpty ? -1 : currentModels.count - 1
    }
    let identifiers = Set(currentModels.map { ObjectIdentifier($0) })
    pruneVodPlayers(keeping: identifiers)
  }

  private func pruneVodPlayers(keeping identifiers: Set<ObjectIdentifier>) {
    var retained: [ObjectIdentifier: TUITXVodPlayer] = [:]
    for (key, player) in vodPlayerByModel {
      if identifiers.contains(key) {
        retained[key] = player
      } else {
        player.removeDelegate(self)
      }
    }
    vodPlayerByModel = retained
    if let currentId = currentVodPlayerIdentifier,
       retained[currentId] == nil {
      currentVodPlayerIdentifier = nil
    }
  }

  private func emitVodEvent(_ type: String, payload: [String: Any]? = nil) {
    guard let callback = onVodEvent else { return }
    var body: [String: Any] = ["type": type]
    if let payload = payload {
      body["payload"] = payload
    }
    callback(body)
  }

  private func normalizeParam(_ param: [AnyHashable: Any]) -> [String: Any] {
    var result: [String: Any] = [:]
    for (key, value) in param {
      guard let keyString = key as? String else { continue }
      switch value {
      case let number as NSNumber:
        result[keyString] = number
      case let string as String:
        result[keyString] = string
      case let dict as [AnyHashable: Any]:
        result[keyString] = normalizeParam(dict)
      default:
        result[keyString] = String(describing: value)
      }
    }
    return result
  }

  @objc func removeData(at index: NSNumber) {
    DispatchQueue.main.async {
      let target = index.intValue
      guard target >= 0, target < self.currentModels.count else { return }
      self.currentModels.remove(at: target)
      self.refreshModels()
    }
  }

  @objc func removeRangeData(_ index: NSNumber, count: NSNumber) {
    DispatchQueue.main.async {
      let start = index.intValue
      let length = count.intValue
      guard length > 0, !self.currentModels.isEmpty else { return }
      let safeStart = max(0, min(start, self.currentModels.count - 1))
      let end = min(self.currentModels.count, safeStart + length)
      guard end > safeStart else { return }
      self.currentModels.removeSubrange(safeStart..<end)
      self.refreshModels()
    }
  }

  @objc func removeDataByIndexes(_ removeIndexList: [NSNumber]) {
    DispatchQueue.main.async {
      guard !removeIndexList.isEmpty else { return }
      let sorted = removeIndexList
        .map { $0.intValue }
        .filter { $0 >= 0 && $0 < self.currentModels.count }
        .sorted(by: >)
      guard !sorted.isEmpty else { return }
      for index in sorted {
        self.currentModels.remove(at: index)
      }
      self.refreshModels()
    }
  }

  @objc func addData(_ source: [String: Any], index: NSNumber) {
    DispatchQueue.main.async {
      guard let model = Self.makeVideoModel(from: source) else { return }
      let target = index.intValue
      let insertion = target < 0 ? self.currentModels.count : max(0, min(target, self.currentModels.count))
      self.currentModels.insert(model, at: insertion)
      self.refreshModels()
    }
  }

  @objc func addRangeData(_ sources: [[String: Any]], startIndex: NSNumber) {
    DispatchQueue.main.async {
      let models = sources.compactMap { Self.makeVideoModel(from: $0) }
      guard !models.isEmpty else { return }
      let target = startIndex.intValue
      let insertion = target < 0 ? self.currentModels.count : max(0, min(target, self.currentModels.count))
      self.currentModels.insert(contentsOf: models, at: insertion)
      self.refreshModels()
    }
  }

  @objc func replaceData(_ source: [String: Any], index: NSNumber) {
    DispatchQueue.main.async {
      guard let model = Self.makeVideoModel(from: source) else { return }
      let target = index.intValue
      guard target >= 0, target < self.currentModels.count else { return }
      self.currentModels[target] = model
      self.refreshModels()
    }
  }

  @objc func replaceRangeData(_ sources: [[String: Any]], startIndex: NSNumber) {
    DispatchQueue.main.async {
      let models = sources.compactMap { Self.makeVideoModel(from: $0) }
      guard !models.isEmpty else { return }
      let start = startIndex.intValue
      guard start >= 0, start < self.currentModels.count else { return }
      let end = start + models.count
      guard end <= self.currentModels.count else { return }
      for (offset, model) in models.enumerated() {
        self.currentModels[start + offset] = model
      }
      self.refreshModels()
    }
  }

  @objc func updateMeta(_ index: NSNumber, meta: [String: Any]) {
    // iOS demo暂未实现覆盖层，先保留空实现避免命令报错
  }

  @objc func dataCount() -> NSNumber {
    return NSNumber(value: currentModels.count)
  }

  @objc func dataSnapshot(at index: NSNumber) -> [String: Any]? {
    let target = index.intValue
    guard target >= 0, target < currentModels.count else { return nil }
    return Self.serialize(model: currentModels[target])
  }

  @objc
  func currentIndexValue() -> NSNumber? {
    guard let index = resolveCurrentIndex() else {
      return nil
    }
    return NSNumber(value: index)
  }

  @objc
  func currentSourceSnapshot() -> [String: Any]? {
    guard
      let index = resolveCurrentIndex(),
      index >= 0,
      index < currentModels.count
    else {
      return nil
    }
    return Self.serialize(model: currentModels[index])
  }

  private func resolveCurrentIndex() -> Int? {
    let currentIndex = shortVideoView.currentVideoIndex
    if currentIndex >= 0, currentIndex < currentModels.count {
      return currentIndex
    }
    if lastPageIndex >= 0, lastPageIndex < currentModels.count {
      return lastPageIndex
    }
    return nil
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
    if currentModels.indices.contains(index) {
      currentVodPlayerIdentifier = ObjectIdentifier(currentModels[index])
    } else {
      currentVodPlayerIdentifier = nil
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

  private static func serialize(model: TUIPlayerVideoModel) -> [String: Any] {
    var payload: [String: Any] = [:]
    if model.appId != 0 {
      payload["appId"] = Int(model.appId)
    }
    if !model.fileId.isEmpty {
      payload["type"] = "fileId"
      payload["fileId"] = model.fileId
    } else if !model.videoUrl.isEmpty {
      payload["type"] = "url"
      payload["url"] = model.videoUrl
    }
    if !model.coverPictureUrl.isEmpty {
      payload["coverPictureUrl"] = model.coverPictureUrl
    }
    if !model.pSign.isEmpty {
      payload["pSign"] = model.pSign
    }
    return payload
  }

  private func resolveVodPlayer(options: [String: Any]?) -> TUITXVodPlayer? {
    if let indexValue = options?["index"] as? NSNumber {
      let targetIndex = indexValue.intValue
      if let model = currentModels[safe: targetIndex] {
        return vodPlayerByModel[ObjectIdentifier(model)]
      }
      return nil
    }
    if let currentIndex = resolveCurrentIndex(),
       let model = currentModels[safe: currentIndex] {
      return vodPlayerByModel[ObjectIdentifier(model)]
    }
    return nil
  }

  @objc(handleVodPlayerCommand:options:error:)
  func handleVodPlayerCommand(
    _ command: String,
    options: [String: Any]?
  ) throws -> Any? {
    guard let player = resolveVodPlayer(options: options) else {
      throw VodPlayerCommandError.playerUnavailable
    }

    switch command {
    case "startPlay":
      guard
        let source = options?["source"] as? [String: Any],
        let model = TuiplayerShortVideoView.makeVideoModel(from: source)
      else {
        throw VodPlayerCommandError.invalidParameter("source")
      }
      player.startVodPlayWithModel(model)
      return nil
    case "resumePlay":
      player.resumePlay()
      return nil
    case "pause":
      player.pausePlay()
      return nil
    case "stop":
      if let clear = options?["clearLastImage"] as? Bool {
        _ = player.stopPlay(!clear)
      } else {
        _ = player.stopPlay()
      }
      return nil
    case "seekTo":
      guard let time = options?["time"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("time")
      }
      player.seekToTime(time.floatValue)
      return nil
    case "isPlaying":
      return NSNumber(value: player.isPlaying)
    case "setLoop":
      guard let loop = options?["loop"] as? Bool else {
        throw VodPlayerCommandError.invalidParameter("loop")
      }
      player.loop = loop
      return nil
    case "isLoop":
      return NSNumber(value: player.loop)
    case "setRate":
      guard let rate = options?["rate"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("rate")
      }
      player.setRate(rate.floatValue)
      return nil
    case "getDuration":
      return NSNumber(value: player.duration)
    case "getCurrentPlaybackTime":
      return NSNumber(value: player.currentPlaybackTime)
    case "getPlayableDuration":
      return NSNumber(value: player.playableDuration)
    case "setMute":
      guard let mute = options?["mute"] as? Bool else {
        throw VodPlayerCommandError.invalidParameter("mute")
      }
      player.setMute(mute)
      return nil
    case "setAudioPlayoutVolume":
      guard let volume = options?["volume"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("volume")
      }
      player.setAudioPlayoutVolume(volume.intValue)
      return nil
    case "setMirror":
      guard let mirror = options?["mirror"] as? Bool else {
        throw VodPlayerCommandError.invalidParameter("mirror")
      }
      player.setMirror(mirror)
      return nil
    case "setBitrateIndex":
      guard let index = options?["index"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("index")
      }
      let success = player.setBitrateIndex(index.intValue)
      return NSNumber(value: success)
    case "getBitrateIndex":
      return NSNumber(value: player.bitrateIndex)
    case "getSupportResolution":
      let items = player.supportedBitrates ?? []
      return items.map { item in
        [
          "index": NSNumber(value: item.index),
          "width": NSNumber(value: item.width),
          "height": NSNumber(value: item.height),
          "bitrate": NSNumber(value: item.bitrate),
        ]
      }
    case "setRenderRotation":
      guard let rotation = options?["rotation"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("rotation")
      }
      let orientation = TUI_Enum_Type_HomeOrientation(rawValue: rotation.intValue)
        ?? TUI_HOME_ORIENTATION_RIGHT
      player.setRenderRotation(orientation)
      return nil
    case "setRenderMode":
      guard let mode = options?["mode"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("mode")
      }
      let renderMode = TUI_Enum_Type_RenderMode(rawValue: mode.intValue)
        ?? TUI_RENDER_MODE_FILL_SCREEN
      player.setRenderMode(renderMode)
      return nil
    case "getWidth":
      return NSNumber(value: player.width)
    case "getHeight":
      return NSNumber(value: player.height)
    case "switchResolution":
      guard let resolution = options?["resolution"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("resolution")
      }
      let target = resolution.doubleValue
      let items = player.supportedBitrates ?? []
      if let matched = items.first(where: { Double($0.width * $0.height) == target }) {
        let success = player.setBitrateIndex(matched.index)
        return NSNumber(value: success)
      }
      return NSNumber(value: false)
    case "setAudioNormalization":
      guard let value = options?["value"] as? NSNumber else {
        throw VodPlayerCommandError.invalidParameter("value")
      }
      player.setAudioNormalization(value.floatValue)
      return nil
    case "enableHardwareDecode":
      guard let enable = options?["enable"] as? Bool else {
        throw VodPlayerCommandError.invalidParameter("enable")
      }
      player.enableHWAcceleration = enable
      return NSNumber(value: enable)
    default:
      throw VodPlayerCommandError.unsupportedCommand(command)
    }
  }
}

private extension Array {
  subscript(safe index: Int) -> Element? {
    guard index >= 0, index < count else { return nil }
    return self[index]
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

  func shortVideoView(_ shortVideoView: TUIShortVideoView, playerDidReady player: TUITXVodPlayer, videoModel: TUIPlayerDataModel!) {
    guard let model = videoModel as? TUIPlayerVideoModel else {
      return
    }
    let identifier = ObjectIdentifier(model)
    if let existing = vodPlayerByModel[identifier], existing !== player {
      existing.removeDelegate(self)
    }
    vodPlayerByModel[identifier] = player
    player.addDelegate(self)
    if let currentId = currentVodPlayerIdentifier, currentId == identifier {
      return
    }
    if let currentIndex = resolveCurrentIndex(),
       currentIndex >= 0,
       currentIndex < currentModels.count {
      let currentModel = currentModels[currentIndex]
      if ObjectIdentifier(currentModel) == identifier {
        currentVodPlayerIdentifier = identifier
      }
    }
  }
}

extension TuiplayerShortVideoView: TUITXVodPlayerDelegate {
  func player(_ player: TUITXVodPlayer, statusChanged status: TUITXVodPlayerStatus) {
    if lastVodStatus == .TUITXVodPlayerStatusLoading,
       status != .TUITXVodPlayerStatusLoading {
      emitVodEvent("onPlayLoadingEnd")
    }
    lastVodStatus = status
    switch status {
    case .TUITXVodPlayerStatusPrepared:
      emitVodEvent("onPlayPrepare")
    case .TUITXVodPlayerStatusLoading:
      emitVodEvent("onPlayLoading")
    case .TUITXVodPlayerStatusPlaying:
      emitVodEvent("onPlayBegin")
    case .TUITXVodPlayerStatusPaused:
      emitVodEvent("onPlayPause")
    case .TUITXVodPlayerStatusEnded:
      emitVodEvent("onPlayEnd")
    case .TUITXVodPlayerStatusError:
      emitVodEvent("onError", ["code": -1, "message": "Player status error"])
    case .TUITXVodPlayerStatusLoopCompleted:
      emitVodEvent("onLoopCompleted")
    default:
      break
    }
  }

  func player(_ player: TUITXVodPlayer, currentTime: Float, totalTime: Float, progress: Float) {
    emitVodEvent(
      "onPlayProgress",
      [
        "current": Double(currentTime),
        "duration": Double(totalTime),
        "progress": Double(progress),
      ]
    )
  }

  func onNetStatus(_ player: TUITXVodPlayer, withParam param: [AnyHashable: Any]) {
    emitVodEvent("onNetStatus", ["param": normalizeParam(param)])
  }

  func onPlayEvent(_ player: TUITXVodPlayer, event evtID: Int, withParam param: [AnyHashable: Any]) {
    var payload: [String: Any] = ["event": evtID]
    if !param.isEmpty {
      payload["param"] = normalizeParam(param)
    }
    emitVodEvent("onPlayEvent", payload)
  }

  func vodRenderModeChanged(_ renderMode: TUI_Enum_Type_RenderMode) {
    emitVodEvent("onRenderModeChanged", ["mode": renderMode.rawValue])
  }
}

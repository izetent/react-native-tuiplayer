import Foundation
import React
import UIKit
import TUIPlayerCore
import TUIPlayerShortVideo
import TXLiteAVSDK_Player

fileprivate enum TuiplayerRenderModePreference: Int {
  case fill = 0
  case fit = 1

  var nativeValue: TUI_Enum_Type_RenderMode {
    switch self {
    case .fit:
      return TUI_RENDER_MODE_FILL_EDGE
    case .fill:
      return TUI_RENDER_MODE_FILL_SCREEN
    }
  }
}

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

fileprivate struct ShortVideoMetadata {
  var name: String?
  var icon: String?
  var tags: [String]?
  var details: String?
  var isShowPaly: Bool?
  var authorName: String?
  var authorAvatar: String?
  var title: String?
  var likeCount: Double?
  var commentCount: Double?
  var favoriteCount: Double?
  var isLiked: Bool?
  var isBookmarked: Bool?
  var isFollowed: Bool?
  var watchMoreText: String?

  init() {}

  init?(dictionary: [String: Any]) {
    var metadata = ShortVideoMetadata()
    metadata.name = ShortVideoMetadata.pickString(dictionary["name"])
    metadata.icon = ShortVideoMetadata.pickString(dictionary["icon"])
    metadata.tags = ShortVideoMetadata.parseTags(dictionary["type"])
    metadata.details = ShortVideoMetadata.pickString(dictionary["details"])
    if let value = dictionary["isShowPaly"] as? Bool {
      metadata.isShowPaly = value
    }
    if let value = dictionary["authorName"] as? String, !value.isEmpty {
      metadata.authorName = value
    } else if let fallback = metadata.name {
      metadata.authorName = fallback
    }
    if let value = dictionary["authorAvatar"] as? String, !value.isEmpty {
      metadata.authorAvatar = value
    } else if let fallback = metadata.icon {
      metadata.authorAvatar = fallback
    }
    if let value = dictionary["title"] as? String, !value.isEmpty {
      metadata.title = value
    } else if let fallback = metadata.name {
      metadata.title = fallback
    }
    if let value = Self.parseNumeric(dictionary["likeCount"]) {
      metadata.likeCount = value
    }
    if let value = Self.parseNumeric(dictionary["commentCount"]) {
      metadata.commentCount = value
    }
    if let value = Self.parseNumeric(dictionary["favoriteCount"]) {
      metadata.favoriteCount = value
    }
    if let value = dictionary["isLiked"] as? Bool {
      metadata.isLiked = value
    }
    if let value = dictionary["isBookmarked"] as? Bool {
      metadata.isBookmarked = value
    }
    if let value = dictionary["isFollowed"] as? Bool {
      metadata.isFollowed = value
    }
    if let value = dictionary["watchMoreText"] as? String,
       !value.isEmpty {
      metadata.watchMoreText = value
    } else if let fallback = metadata.details {
      metadata.watchMoreText = fallback
    }
    if metadata.isEmpty {
      return nil
    }
    self = metadata
  }

  private static func parseNumeric(_ value: Any?) -> Double? {
    if let number = value as? NSNumber {
      return number.doubleValue
    }
    if let string = value as? String,
       let parsed = Double(string) {
      return parsed
    }
    return nil
  }

  private static func pickString(_ value: Any?) -> String? {
    guard let raw = value as? String else {
      return nil
    }
    let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
    return trimmed.isEmpty ? nil : trimmed
  }

  private static func parseTags(_ value: Any?) -> [String]? {
    if let array = value as? [Any] {
      let tags = array.compactMap { entry -> String? in
        guard let raw = entry as? String else {
          return nil
        }
        let trimmed = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        return trimmed.isEmpty ? nil : trimmed
      }
      return tags.isEmpty ? nil : tags
    }
    if let raw = value as? String {
      let segments = raw
        .split(separator: "#")
        .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
        .filter { !$0.isEmpty }
      return segments.isEmpty ? nil : segments
    }
    return nil
  }

  var isEmpty: Bool {
    let hasTags = !(tags?.isEmpty ?? true)
    return name == nil
      && icon == nil
      && !hasTags
      && details == nil
      && isShowPaly == nil
      && authorName == nil
      && authorAvatar == nil
      && title == nil
      && likeCount == nil
      && commentCount == nil
      && favoriteCount == nil
      && isLiked == nil
      && isBookmarked == nil
      && isFollowed == nil
      && watchMoreText == nil
  }

  func dictionaryRepresentation() -> [String: Any] {
    var payload: [String: Any] = [:]
    if let name {
      payload["name"] = name
    } else if let authorName {
      payload["name"] = authorName
    }
    if let icon {
      payload["icon"] = icon
    } else if let authorAvatar {
      payload["icon"] = authorAvatar
    }
    if let tags, !tags.isEmpty {
      payload["type"] = tags
    }
    if let details {
      payload["details"] = details
    } else if let watchMoreText {
      payload["details"] = watchMoreText
    }
    if let isShowPaly {
      payload["isShowPaly"] = isShowPaly
    }
    if let authorName {
      payload["authorName"] = authorName
    }
    if let authorAvatar {
      payload["authorAvatar"] = authorAvatar
    }
    if let title {
      payload["title"] = title
    }
    if let likeCount {
      payload["likeCount"] = likeCount
    }
    if let commentCount {
      payload["commentCount"] = commentCount
    }
    if let favoriteCount {
      payload["favoriteCount"] = favoriteCount
    }
    if let isLiked {
      payload["isLiked"] = isLiked
    }
    if let isBookmarked {
      payload["isBookmarked"] = isBookmarked
    }
    if let isFollowed {
      payload["isFollowed"] = isFollowed
    }
    if let watchMoreText {
      payload["watchMoreText"] = watchMoreText
    } else if let details {
      payload["watchMoreText"] = details
    }
    return payload
  }
}

fileprivate struct ShortVideoSubtitle {
  let name: String
  let url: String
  let mimeType: TUI_VOD_PLAYER_SUBTITLE_MIME_TYPE

  init?(dictionary: [String: Any]) {
    guard let rawUrl = dictionary["url"] as? String, !rawUrl.isEmpty else {
      return nil
    }
    url = rawUrl
    if let value = dictionary["name"] as? String, !value.isEmpty {
      name = value
    } else {
      name = rawUrl
    }
    mimeType = ShortVideoSubtitle.parseMimeType(dictionary["mimeType"])
  }

  init(name: String, url: String, mimeType: TUI_VOD_PLAYER_SUBTITLE_MIME_TYPE) {
    self.name = name
    self.url = url
    self.mimeType = mimeType
  }

  func dictionaryRepresentation() -> [String: Any] {
    return [
      "name": name,
      "url": url,
      "mimeType": ShortVideoSubtitle.string(from: mimeType),
    ]
  }

  func makeModel() -> TUIPlayerSubtitleModel {
    let model = TUIPlayerSubtitleModel()
    model.name = name
    model.url = url
    model.mimeType = mimeType
    return model
  }

  private static func parseMimeType(_ value: Any?) -> TUI_VOD_PLAYER_SUBTITLE_MIME_TYPE {
    guard let raw = value as? String else {
      return .TUI_VOD_PLAYER_MIMETYPE_TEXT_VTT
    }
    switch raw.lowercased() {
    case "text/srt", "srt", "application/x-subrip":
      return .TUI_VOD_PLAYER_MIMETYPE_TEXT_SRT
    default:
      return .TUI_VOD_PLAYER_MIMETYPE_TEXT_VTT
    }
  }

  private static func string(from mime: TUI_VOD_PLAYER_SUBTITLE_MIME_TYPE) -> String {
    switch mime {
    case .TUI_VOD_PLAYER_MIMETYPE_TEXT_SRT:
      return "text/srt"
    default:
      return "text/vtt"
    }
  }
}

fileprivate struct ShortVideoVideoConfig {
  var preloadBufferSizeInMB: Double?
  var preDownloadSize: Double?

  init() {}

  init?(dictionary: [String: Any]) {
    var config = ShortVideoVideoConfig()
    if let value = dictionary["preloadBufferSizeInMB"] as? NSNumber {
      config.preloadBufferSizeInMB = value.doubleValue
    } else if let value = dictionary["preloadBufferSizeInMB"] as? Double {
      config.preloadBufferSizeInMB = value
    }
    if let value = dictionary["preDownloadSize"] as? NSNumber {
      config.preDownloadSize = value.doubleValue
    } else if let value = dictionary["preDownloadSize"] as? Double {
      config.preDownloadSize = value
    }
    if config.isEmpty {
      return nil
    }
    self = config
  }

  var isEmpty: Bool {
    return preloadBufferSizeInMB == nil && preDownloadSize == nil
  }

  func dictionaryRepresentation() -> [String: Any] {
    var payload: [String: Any] = [:]
    if let preloadBufferSizeInMB {
      payload["preloadBufferSizeInMB"] = preloadBufferSizeInMB
    }
    if let preDownloadSize {
      payload["preDownloadSize"] = preDownloadSize
    }
    return payload
  }
}

fileprivate struct ShortVideoPayload {
  enum SourceType: String {
    case fileId
    case url
  }

  let type: SourceType
  var appId: Int32?
  var fileId: String?
  var url: String?
  var coverPictureUrl: String?
  var pSign: String?
  var extViewType: Int?
  var autoPlay: Bool?
  var videoConfig: ShortVideoVideoConfig?
  var meta: ShortVideoMetadata?
  var duration: Double?
  var subtitles: [ShortVideoSubtitle]?

  init?(dictionary: [String: Any]) {
    let resolvedType: SourceType
    if let rawType = dictionary["type"] as? String,
       rawType.lowercased() == SourceType.url.rawValue {
      resolvedType = .url
    } else {
      resolvedType = .fileId
    }

    let fileId = dictionary["fileId"] as? String
    let url = dictionary["url"] as? String

    switch resolvedType {
    case .fileId:
      guard let actualFileId = fileId, !actualFileId.isEmpty || (url != nil && !(url ?? "").isEmpty) else {
        return nil
      }
    case .url:
      guard let actualUrl = url, !actualUrl.isEmpty else {
        return nil
      }
    }

    self.type = resolvedType
    if let value = dictionary["appId"] as? NSNumber {
      appId = Int32(truncating: value)
    } else if let value = dictionary["appId"] as? Int {
      appId = Int32(value)
    }
    if let fileId, !fileId.isEmpty {
      self.fileId = fileId
    }
    if let url, !url.isEmpty {
      self.url = url
    }
    if let value = dictionary["coverPictureUrl"] as? String, !value.isEmpty {
      coverPictureUrl = value
    }
    if let value = dictionary["pSign"] as? String, !value.isEmpty {
      pSign = value
    }
    if let value = dictionary["extViewType"] as? NSNumber {
      extViewType = value.intValue
    } else if let value = dictionary["extViewType"] as? Int {
      extViewType = value
    }
    if let value = dictionary["autoPlay"] as? Bool {
      autoPlay = value
    }
    if let configDict = dictionary["videoConfig"] as? [String: Any] {
      videoConfig = ShortVideoVideoConfig(dictionary: configDict)
    }
    if let metaDict = dictionary["meta"] as? [String: Any] {
      meta = ShortVideoMetadata(dictionary: metaDict)
    }
    if let value = dictionary["duration"] as? NSNumber {
      duration = value.doubleValue
    } else if let value = dictionary["duration"] as? Double {
      duration = value
    }
    if let items = dictionary["subtitles"] as? [Any] {
      let parsed = items.compactMap { element -> ShortVideoSubtitle? in
        guard let dict = element as? [String: Any] else { return nil }
        return ShortVideoSubtitle(dictionary: dict)
      }
      if !parsed.isEmpty {
        subtitles = parsed
      }
    }
  }

  func dictionaryRepresentation() -> [String: Any] {
    var payload: [String: Any] = ["type": type.rawValue]
    if let appId, appId != 0 {
      payload["appId"] = Int(appId)
    }
    if let fileId, !fileId.isEmpty {
      payload["fileId"] = fileId
    }
    if let url, !url.isEmpty {
      payload["url"] = url
    }
    if let coverPictureUrl, !coverPictureUrl.isEmpty {
      payload["coverPictureUrl"] = coverPictureUrl
    }
    if let pSign, !pSign.isEmpty {
      payload["pSign"] = pSign
    }
    if let extViewType {
      payload["extViewType"] = extViewType
    }
    if let autoPlay {
      payload["autoPlay"] = autoPlay
    }
    if let videoConfig, !videoConfig.isEmpty {
      payload["videoConfig"] = videoConfig.dictionaryRepresentation()
    }
    if let meta, !meta.isEmpty {
      payload["meta"] = meta.dictionaryRepresentation()
    }
    if let duration {
      payload["duration"] = duration
    }
    if let subtitles, !subtitles.isEmpty {
      payload["subtitles"] = subtitles.map { $0.dictionaryRepresentation() }
    }
    return payload
  }

  func hasSameIdentity(as other: ShortVideoPayload) -> Bool {
    switch (fileId, other.fileId, url, other.url) {
    case let (lhsFileId?, rhsFileId?, _, _) where !lhsFileId.isEmpty && !rhsFileId.isEmpty:
      if lhsFileId != rhsFileId {
        return false
      }
      let lhsAppId = appId ?? 0
      let rhsAppId = other.appId ?? 0
      return lhsAppId == 0 || rhsAppId == 0 || lhsAppId == rhsAppId
    case let (_, _, lhsUrl?, rhsUrl?) where !lhsUrl.isEmpty && !rhsUrl.isEmpty:
      return lhsUrl == rhsUrl
    default:
      return false
    }
  }

  func hasSameContent(as other: ShortVideoPayload) -> Bool {
    let lhs = dictionaryRepresentation() as NSDictionary
    let rhs = other.dictionaryRepresentation() as NSDictionary
    return lhs.isEqual(rhs)
  }
}

fileprivate struct SubtitleRenderStyle {
  var canvasWidth: Int?
  var canvasHeight: Int?
  var familyName: String?
  var fontSize: Double?
  var fontScale: Double?
  var fontColor: UInt32?
  var bold: Bool?
  var outlineWidth: Double?
  var outlineColor: UInt32?
  var lineSpace: Double?
  var startMargin: Double?
  var endMargin: Double?
  var verticalMargin: Double?

  init?(dictionary: [String: Any]) {
    canvasWidth = SubtitleRenderStyle.parseInt(dictionary["canvasWidth"])
    canvasHeight = SubtitleRenderStyle.parseInt(dictionary["canvasHeight"])
    if let value = dictionary["familyName"] as? String, !value.isEmpty {
      familyName = value
    }
    fontSize = SubtitleRenderStyle.parseDouble(dictionary["fontSize"])
    fontScale = SubtitleRenderStyle.parseDouble(dictionary["fontScale"])
    fontColor = SubtitleRenderStyle.parseColor(dictionary["fontColor"])
    bold = dictionary["bold"] as? Bool
    outlineWidth = SubtitleRenderStyle.parseDouble(dictionary["outlineWidth"])
    outlineColor = SubtitleRenderStyle.parseColor(dictionary["outlineColor"])
    lineSpace = SubtitleRenderStyle.parseDouble(dictionary["lineSpace"])
    startMargin = SubtitleRenderStyle.parseDouble(dictionary["startMargin"])
    endMargin = SubtitleRenderStyle.parseDouble(dictionary["endMargin"])
    verticalMargin = SubtitleRenderStyle.parseDouble(dictionary["verticalMargin"])
    if !hasValue {
      return nil
    }
  }

  private func payload(for model: TUIPlayerVideoModel) -> ShortVideoPayload? {
    guard let index = currentModels.firstIndex(where: { $0 === model }),
          index < currentPayloads.count else {
      return nil
    }
    return currentPayloads[index]
  }

  private func markSubtitlePreference(
    for player: TUITXVodPlayer,
    model: TUIPlayerVideoModel
  ) {
    let hasSubtitles = payload(for: model)?.subtitles?.isEmpty == false
    markSubtitlePreference(for: player, hasSubtitles: hasSubtitles)
  }

  private func markSubtitlePreference(for player: TUITXVodPlayer, hasSubtitles: Bool) {
    let identifier = ObjectIdentifier(player)
    if hasSubtitles {
      subtitlePreferredPlayers.insert(identifier)
      subtitleSelectionAttempts[identifier] = nil
      maybeSelectSubtitle(for: player)
    } else {
      subtitlePreferredPlayers.remove(identifier)
      subtitleSelectionAttempts.removeValue(forKey: identifier)
    }
  }

  private func maybeSelectSubtitle(for player: TUITXVodPlayer) {
    let identifier = ObjectIdentifier(player)
    guard subtitlePreferredPlayers.contains(identifier) else { return }
    if trySelectSubtitle(for: player) {
      subtitleSelectionAttempts.removeValue(forKey: identifier)
      return
    }
    let attempt = (subtitleSelectionAttempts[identifier] ?? 0)
    if attempt >= 5 {
      return
    }
    subtitleSelectionAttempts[identifier] = attempt + 1
    let delay = 0.35 * Double(attempt + 1)
    DispatchQueue.main.asyncAfter(deadline: .now() + delay) { [weak self, weak player] in
      guard let self, let player else { return }
      self.maybeSelectSubtitle(for: player)
    }
  }

  private func trySelectSubtitle(for player: TUITXVodPlayer) -> Bool {
    guard let tracks = player.getSubtitleTrackInfo(), !tracks.isEmpty else {
      return false
    }
    guard let target = tracks.first(where: { $0.trackType == TX_VOD_MEDIA_TRACK_TYPE_SUBTITLE }) else {
      return false
    }
    player.selectTrack(target.trackIndex)
    return true
  }

  private func clearSubtitlePreference(for player: TUITXVodPlayer) {
    let identifier = ObjectIdentifier(player)
    subtitlePreferredPlayers.remove(identifier)
    subtitleSelectionAttempts.removeValue(forKey: identifier)
  }

  private var hasValue: Bool {
    return canvasWidth != nil
      || canvasHeight != nil
      || familyName != nil
      || fontSize != nil
      || fontScale != nil
      || fontColor != nil
      || bold != nil
      || outlineWidth != nil
      || outlineColor != nil
      || lineSpace != nil
      || startMargin != nil
      || endMargin != nil
      || verticalMargin != nil
  }

  func makeNativeModel() -> TXPlayerSubtitleRenderModel {
    let model = TXPlayerSubtitleRenderModel()
    if let canvasWidth {
      model.canvasWidth = Int32(canvasWidth)
    }
    if let canvasHeight {
      model.canvasHeight = Int32(canvasHeight)
    }
    if let familyName {
      model.familyName = familyName
    }
    if let fontSize {
      model.fontSize = Float(fontSize)
    }
    if let fontScale {
      model.fontScale = Float(fontScale)
    }
    if let fontColor {
      model.fontColor = fontColor
    }
    if let bold {
      model.isBondFontStyle = bold
    }
    if let outlineWidth {
      model.outlineWidth = Float(outlineWidth)
    }
    if let outlineColor {
      model.outlineColor = outlineColor
    }
    if let lineSpace {
      model.lineSpace = Float(lineSpace)
    }
    if let startMargin {
      model.startMargin = Float(startMargin)
    }
    if let endMargin {
      model.endMargin = Float(endMargin)
    }
    if let verticalMargin {
      model.verticalMargin = Float(verticalMargin)
    }
    return model
  }

  private static func parseInt(_ value: Any?) -> Int? {
    if let number = value as? NSNumber {
      return number.intValue
    }
    return value as? Int
  }

  private static func parseDouble(_ value: Any?) -> Double? {
    if let number = value as? NSNumber {
      return number.doubleValue
    }
    return value as? Double
  }

  private static func parseColor(_ value: Any?) -> UInt32? {
    guard let number = value as? NSNumber else {
      return nil
    }
    return UInt32(bitPattern: number.int32Value)
  }
}

fileprivate struct LayerConfiguration {
  var vodLayerClassNames: [String]
  var liveLayerClassNames: [String]
  var customLayerClassNames: [String]

  init(vod: [String] = [], live: [String] = [], custom: [String] = []) {
    self.vodLayerClassNames = vod
    self.liveLayerClassNames = live
    self.customLayerClassNames = custom
  }

  init?(dictionary: [String: Any]) {
    func extractArray(_ value: Any?) -> [String] {
      guard let items = value as? [Any] else {
        return []
      }
      return items.compactMap { element in
        if let string = element as? String, !string.isEmpty {
          return string
        }
        return nil
      }
    }
    let vod = extractArray(dictionary["vodLayers"])
    let live = extractArray(dictionary["liveLayers"])
    let custom = extractArray(dictionary["customLayers"])
    if vod.isEmpty && live.isEmpty && custom.isEmpty {
      return nil
    }
    self.init(vod: vod, live: live, custom: custom)
  }
}

@objc(TuiplayerShortVideoView)
class TuiplayerShortVideoView: UIView {
  private let shortVideoView = TUIShortVideoView()
  private let overlayView = TuiplayerInfoOverlayView()
  private var isAutoPlayEnabled: Bool = true
  private var currentModels: [TUIPlayerVideoModel] = []
  private var currentPayloads: [ShortVideoPayload] = []
  private var vodPlayerByModel: [ObjectIdentifier: TUITXVodPlayer] = [:]
  private var currentVodPlayerIdentifier: ObjectIdentifier?
  private var durationByModel: [ObjectIdentifier: Double] = [:] // seconds
  private var currentTimeByModel: [ObjectIdentifier: Double] = [:] // seconds
  private var lastVodStatus: TUITXVodPlayerStatus = .TUITXVodPlayerStatusUnload
  @objc var onVodEvent: RCTDirectEventBlock?
  @objc var onPlaybackStart: RCTDirectEventBlock?
  @objc var onPlaybackEnd: RCTDirectEventBlock?
  private var lastEndReachedTotal: Int = -1
  private var lastPageIndex: Int = -1
  private var lastPlaybackStartedIndex: Int?
  private var pendingInitialIndex: Int?
  private var isManuallyPaused: Bool = false
  private var hasDispatchedReady: Bool = false
  private var hasDispatchedTopReached: Bool = false
  private var lastKnownScrollOffset: CGFloat = 0
  private var customVodStrategy: TUIPlayerVodStrategyModel?
  private var requestedRenderMode: TuiplayerRenderModePreference = .fill
  private var customLiveStrategy: TUIPlayerLiveStrategyModel?
  private var subtitleRenderStyle: SubtitleRenderStyle?
  private var layerConfig: LayerConfiguration?
  private var subtitlePreferredPlayers: Set<ObjectIdentifier> = []
  private var subtitleSelectionAttempts: [ObjectIdentifier: Int] = [:]
  @objc var onTopReached: RCTDirectEventBlock?
  @objc var onReady: RCTDirectEventBlock? {
    didSet {
      dispatchReadyEventIfNeeded()
    }
  }
  @objc var autoPlay: NSNumber = true {
    didSet {
      isAutoPlayEnabled = autoPlay.boolValue
      shortVideoView.isAutoPlay = isAutoPlayEnabled
      if isAutoPlayEnabled {
        if !isManuallyPaused {
          resume()
        }
      } else {
        pause()
      }
      setOverlayPaused(isOverlayPaused())
    }
  }
  @objc var paused: NSNumber = false {
    didSet {
      let shouldPause = paused.boolValue
      isManuallyPaused = shouldPause
      if shouldPause {
        pause()
      } else if isAutoPlayEnabled {
        resume()
      }
      setOverlayPaused(isOverlayPaused())
    }
  }
  @objc var initialIndex: NSNumber = -1 {
    didSet {
      let value = initialIndex.intValue
      pendingInitialIndex = value >= 0 ? value : nil
      applyInitialIndexIfPossible()
    }
  }
  @objc var playMode: NSNumber = -1 {
    didSet {
      applyPlayModeIfNeeded()
    }
  }
  @objc var userInputEnabled: NSNumber = true {
    didSet {
      shortVideoView.scrollEnabled = userInputEnabled.boolValue
    }
  }
  @objc var pageScrollMsPerInch: NSNumber? {
    didSet {
      // iOS SDK does not expose page scroll tuning; retain for parity without applying.
    }
  }
  @objc var vodStrategy: NSDictionary? {
    didSet {
      if let dict = vodStrategy as? [String: Any] {
        if let modeValue = dict["renderMode"] as? NSNumber {
          let resolved =
            TuiplayerRenderModePreference(rawValue: modeValue.intValue) ?? .fill
          updateRenderModePreference(resolved)
        } else {
          updateRenderModePreference(.fill)
        }
        customVodStrategy = makeVodStrategy(from: dict)
      } else {
        customVodStrategy = nil
        updateRenderModePreference(.fill)
      }
      applyStrategies()
    }
  }
  @objc var liveStrategy: NSDictionary? {
    didSet {
      if let dict = liveStrategy as? [String: Any] {
        customLiveStrategy = Self.makeLiveStrategy(from: dict)
      } else {
        customLiveStrategy = nil
      }
      applyStrategies()
    }
  }
  @objc var subtitleStyle: NSDictionary? {
    didSet {
      if let dict = subtitleStyle as? [String: Any],
         let style = SubtitleRenderStyle(dictionary: dict) {
        subtitleRenderStyle = style
      } else {
        subtitleRenderStyle = nil
      }
      applySubtitleStyleToActivePlayers()
    }
  }
  @objc var layers: NSDictionary? {
    didSet {
      if let dict = layers as? [String: Any] {
        layerConfig = LayerConfiguration(dictionary: dict)
      } else {
        layerConfig = nil
      }
      applyLayerConfiguration()
    }
  }

  @objc var sources: NSArray? {
    didSet {
      guard let items = sources as? [[String: Any]] else {
        currentModels = []
        currentPayloads = []
        shortVideoView.setShortVideoModels([])
        lastEndReachedTotal = -1
        lastPageIndex = -1
        lastPlaybackStartedIndex = nil
        pendingInitialIndex = nil
        updateOverlay(for: nil)
        return
      }
      let entries = items.compactMap { Self.makeEntry(from: $0) }
      apply(entries: entries)
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
    shortVideoView.isAutoPlay = isAutoPlayEnabled
    shortVideoView.scrollEnabled = userInputEnabled.boolValue
    shortVideoView.startLoading()
    shortVideoView.delegate = self
    overlayView.translatesAutoresizingMaskIntoConstraints = false
    overlayView.isHidden = true
    overlayView.delegate = self
    addSubview(overlayView)
    NSLayoutConstraint.activate([
      overlayView.leadingAnchor.constraint(equalTo: leadingAnchor),
      overlayView.trailingAnchor.constraint(equalTo: trailingAnchor),
      overlayView.topAnchor.constraint(equalTo: topAnchor),
      overlayView.bottomAnchor.constraint(equalTo: bottomAnchor),
    ])
    applyStrategies()
    applyLayerConfiguration()
    DispatchQueue.main.async { [weak self] in
      self?.dispatchReadyEventIfNeeded()
    }
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
    } else if isAutoPlayEnabled && !isManuallyPaused {
      resume()
    }
  }

  deinit {
    for player in vodPlayerByModel.values {
      player.removeDelegate(self)
      clearSubtitlePreference(for: player)
    }
    shortVideoView.destoryPlayer()
  }

  private func apply(entries: [(model: TUIPlayerVideoModel, payload: ShortVideoPayload)]) {
    let incomingPayloads = entries.map { $0.payload }
    let previousPayloads = currentPayloads
    let previousCount = previousPayloads.count
    let requiresFullReplace = previousPayloads.isEmpty
      || currentModels.count != previousCount
      || entries.count < previousCount

    if requiresFullReplace {
      replaceAllEntries(entries)
    } else if shouldAppend(previous: previousPayloads, current: incomingPayloads) {
      let appendedEntries = Array(entries.dropFirst(previousCount))
      appendEntries(appendedEntries)
    } else if canUpdateInPlace(previous: previousPayloads, current: incomingPayloads) {
      updateExistingEntries(with: entries)
    } else {
      replaceAllEntries(entries)
    }

    finalizeApplyUpdates()
  }

  private func replaceAllEntries(_ entries: [(model: TUIPlayerVideoModel, payload: ShortVideoPayload)]) {
    let models = entries.map { $0.model }
    shortVideoView.setShortVideoModels(models)
    currentModels = models
    currentPayloads = entries.map { $0.payload }
    lastEndReachedTotal = -1
    lastPageIndex = -1
    lastPlaybackStartedIndex = nil
    if isAutoPlayEnabled && !isManuallyPaused {
      resume()
    }
  }

  private func appendEntries(_ entries: [(model: TUIPlayerVideoModel, payload: ShortVideoPayload)]) {
    guard !entries.isEmpty else { return }
    let models = entries.map { $0.model }
    shortVideoView.appendShortVideoModels(models)
    currentModels.append(contentsOf: models)
    currentPayloads.append(contentsOf: entries.map { $0.payload })
  }

  private func canUpdateInPlace(
    previous: [ShortVideoPayload],
    current: [ShortVideoPayload]
  ) -> Bool {
    guard !previous.isEmpty, previous.count == current.count else {
      return false
    }
    for index in 0..<previous.count {
      if !previous[index].hasSameIdentity(as: current[index]) {
        return false
      }
    }
    return true
  }

  private func updateExistingEntries(
    with entries: [(model: TUIPlayerVideoModel, payload: ShortVideoPayload)]
  ) {
    guard entries.count == currentModels.count,
          currentPayloads.count == currentModels.count else {
      replaceAllEntries(entries)
      return
    }
    let previousPayloads = currentPayloads
    for (index, entry) in entries.enumerated() {
      if !previousPayloads[index].hasSameContent(as: entry.payload) {
        synchronizeModel(currentModels[index], with: entry.model, payload: entry.payload)
      }
    }
    currentPayloads = entries.map { $0.payload }
  }

  private func synchronizeModel(
    _ target: TUIPlayerVideoModel,
    with source: TUIPlayerVideoModel,
    payload: ShortVideoPayload
  ) {
    target.appId = source.appId
    target.fileId = source.fileId
    target.videoUrl = source.videoUrl
    target.coverPictureUrl = source.coverPictureUrl
    target.duration = source.duration
    target.pSign = source.pSign
    target.config = source.config
    TuiplayerShortVideoView.applyMetadata(payload.meta, to: target, notify: true)
    TuiplayerShortVideoView.applySubtitles(payload.subtitles, to: target)
  }

  private func finalizeApplyUpdates() {
    if currentModels.count == currentPayloads.count {
      for (model, payload) in zip(currentModels, currentPayloads) {
        TuiplayerShortVideoView.applyMetadata(payload.meta, to: model, notify: false)
      }
    }
    hasDispatchedTopReached = false
    let identifiers = Set(currentModels.map { ObjectIdentifier($0) })
    pruneVodPlayers(keeping: identifiers)
    if lastPageIndex >= currentModels.count {
      lastPageIndex = currentModels.isEmpty ? -1 : currentModels.count - 1
    }
    applyInitialIndexIfPossible()
    if currentPayloads.isEmpty {
      lastPlaybackStartedIndex = nil
      updateOverlay(for: nil)
    } else if lastPageIndex < 0, !currentModels.isEmpty {
      let resolvedIndex = max(0, min(shortVideoView.currentVideoIndex, currentModels.count - 1))
      notifyPageChanged(index: resolvedIndex)
    } else if lastPageIndex >= 0 {
      let target = min(lastPageIndex, currentPayloads.count - 1)
      updateOverlay(for: target)
    }
  }

  private func refreshEntries() {
    shortVideoView.setShortVideoModels(currentModels)
    lastEndReachedTotal = -1
    if lastPageIndex >= currentModels.count {
      lastPageIndex = currentModels.isEmpty ? -1 : currentModels.count - 1
    }
    let identifiers = Set(currentModels.map { ObjectIdentifier($0) })
    pruneVodPlayers(keeping: identifiers)
    applyInitialIndexIfPossible()
    if currentPayloads.isEmpty {
      updateOverlay(for: nil)
    } else if let index = resolveCurrentIndex() {
      updateOverlay(for: min(index, currentPayloads.count - 1))
    }
  }

  private func pruneVodPlayers(keeping identifiers: Set<ObjectIdentifier>) {
    var retained: [ObjectIdentifier: TUITXVodPlayer] = [:]
    for (key, player) in vodPlayerByModel {
      if identifiers.contains(key) {
        retained[key] = player
      } else {
        player.removeDelegate(self)
        clearSubtitlePreference(for: player)
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

  private func makePlaybackPayload(indexOverride: Int? = nil) -> [String: Any]? {
    guard !currentPayloads.isEmpty else { return nil }
    let resolvedIndex = indexOverride ?? resolveCurrentIndex()
    guard
      let index = resolvedIndex,
      index >= 0,
      index < currentPayloads.count
    else {
      return nil
    }
    var payload: [String: Any] = [
      "index": index,
      "total": currentPayloads.count,
    ]
    payload["source"] = TuiplayerShortVideoView.serialize(payload: currentPayloads[index])
    return payload
  }

  private func emitPlaybackStartIfNeeded() {
    guard let callback = onPlaybackStart,
          let payload = makePlaybackPayload() else { return }
    if let last = lastPlaybackStartedIndex,
       let currentIndex = payload["index"] as? Int,
       last == currentIndex {
      return
    }
    if let currentIndex = payload["index"] as? Int {
      lastPlaybackStartedIndex = currentIndex
    } else {
      lastPlaybackStartedIndex = nil
    }
    callback(payload)
  }

  private func emitPlaybackEndIfPossible(indexOverride: Int? = nil) {
    guard let callback = onPlaybackEnd,
          let payload = makePlaybackPayload(indexOverride: indexOverride) else { return }
    callback(payload)
    if let currentIndex = payload["index"] as? Int,
       currentIndex == lastPlaybackStartedIndex {
      lastPlaybackStartedIndex = nil
    }
  }

  private func isEventFromCurrentPlayer(_ player: TUITXVodPlayer) -> Bool {
    if let currentId = currentVodPlayerIdentifier,
       let currentPlayer = vodPlayerByModel[currentId] {
      return currentPlayer === player
    }
    guard let index = resolveCurrentIndex(),
          currentModels.indices.contains(index) else { return false }
    let modelId = ObjectIdentifier(currentModels[index])
    return vodPlayerByModel[modelId] === player
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

  private func updateOverlay(for index: Int?) {
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      guard let index = index,
            self.currentPayloads.indices.contains(index) else {
        self.overlayView.update(with: nil)
        self.overlayView.isHidden = true
        self.overlayView.setPlaybackPaused(false)
        return
      }
      let payload = self.currentPayloads[index]
      self.overlayView.update(with: payload)
      if payload.meta == nil {
        self.overlayView.isHidden = true
      } else {
        self.overlayView.isHidden = false
        self.overlayView.setPlaybackPaused(self.isOverlayPaused())
      }
    }
  }

  private func setOverlayPaused(_ paused: Bool) {
    DispatchQueue.main.async { [weak self] in
      self?.overlayView.setPlaybackPaused(paused)
    }
  }

  private func isOverlayPaused() -> Bool {
    if isManuallyPaused || !isAutoPlayEnabled {
      return true
    }
    switch lastVodStatus {
    case .TUITXVodPlayerStatusPlaying:
      return false
    case .TUITXVodPlayerStatusPaused,
         .TUITXVodPlayerStatusEnded,
         .TUITXVodPlayerStatusError,
         .TUITXVodPlayerStatusLoopCompleted,
         .TUITXVodPlayerStatusLoading:
      return true
    default:
      return false
    }
  }

  private func emitOverlayAction(_ action: String, index: Int) {
    var payload: [String: Any] = [
      "action": action,
      "index": index,
    ]
    if currentPayloads.indices.contains(index) {
      payload["source"] = Self.serialize(payload: currentPayloads[index])
    }
    emitVodEvent("overlayAction", payload: payload)
  }

  private func emitOverlayActionForCurrentIndex(_ action: String) {
    guard let index = resolveCurrentIndex(), currentPayloads.indices.contains(index) else { return }
    emitOverlayAction(action, index: index)
  }

  @objc func removeData(at index: NSNumber) {
    DispatchQueue.main.async {
      let target = index.intValue
      guard target >= 0, target < self.currentModels.count else { return }
      self.currentModels.remove(at: target)
      self.currentPayloads.remove(at: target)
      self.refreshEntries()
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
      self.currentPayloads.removeSubrange(safeStart..<end)
      self.refreshEntries()
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
        self.currentPayloads.remove(at: index)
      }
      self.refreshEntries()
    }
  }

  @objc func addData(_ source: [String: Any], index: NSNumber) {
    DispatchQueue.main.async {
      guard let entry = Self.makeEntry(from: source) else { return }
      let target = index.intValue
      let insertion = target < 0 ? self.currentModels.count : max(0, min(target, self.currentModels.count))
      self.currentModels.insert(entry.model, at: insertion)
      self.currentPayloads.insert(entry.payload, at: insertion)
      self.refreshEntries()
    }
  }

  @objc func addRangeData(_ sources: [[String: Any]], startIndex: NSNumber) {
    DispatchQueue.main.async {
      let entries = sources.compactMap { Self.makeEntry(from: $0) }
      guard !entries.isEmpty else { return }
      let target = startIndex.intValue
      let insertion = target < 0 ? self.currentModels.count : max(0, min(target, self.currentModels.count))
      self.currentModels.insert(contentsOf: entries.map { $0.model }, at: insertion)
      self.currentPayloads.insert(contentsOf: entries.map { $0.payload }, at: insertion)
      self.refreshEntries()
    }
  }

  @objc func replaceData(_ source: [String: Any], index: NSNumber) {
    DispatchQueue.main.async {
      guard let entry = Self.makeEntry(from: source) else { return }
      let target = index.intValue
      guard target >= 0, target < self.currentModels.count else { return }
      self.currentModels[target] = entry.model
      self.currentPayloads[target] = entry.payload
      self.refreshEntries()
    }
  }

  @objc func replaceRangeData(_ sources: [[String: Any]], startIndex: NSNumber) {
    DispatchQueue.main.async {
      let entries = sources.compactMap { Self.makeEntry(from: $0) }
      guard !entries.isEmpty else { return }
      let start = startIndex.intValue
      guard start >= 0, start < self.currentModels.count else { return }
      let end = start + entries.count
      guard end <= self.currentModels.count else { return }
      for (offset, entry) in entries.enumerated() {
        self.currentModels[start + offset] = entry.model
        self.currentPayloads[start + offset] = entry.payload
      }
      self.refreshEntries()
    }
  }

  @objc func updateMeta(_ index: NSNumber, meta: [String: Any]) {
    DispatchQueue.main.async {
      let target = index.intValue
      guard target >= 0, target < self.currentPayloads.count else { return }
      var payload = self.currentPayloads[target]
      let metadata = ShortVideoMetadata(dictionary: meta)
      payload.meta = metadata
      self.currentPayloads[target] = payload
      if target < self.currentModels.count {
        TuiplayerShortVideoView.applyMetadata(metadata, to: self.currentModels[target], notify: true)
      }
      if let currentIndex = self.resolveCurrentIndex(), currentIndex == target {
        self.updateOverlay(for: target)
      }
    }
  }

  @objc func dataCount() -> NSNumber {
    return NSNumber(value: currentPayloads.count)
  }

  @objc func dataSnapshot(at index: NSNumber) -> [String: Any]? {
    let target = index.intValue
    guard target >= 0, target < currentPayloads.count else { return nil }
    return Self.serialize(payload: currentPayloads[target])
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
      index < currentPayloads.count
    else {
      return nil
    }
    return Self.serialize(payload: currentPayloads[index])
  }

  private func resolveCurrentIndex() -> Int? {
    let currentIndex = shortVideoView.currentVideoIndex
    if currentIndex >= 0, currentIndex < currentPayloads.count {
      return currentIndex
    }
    if lastPageIndex >= 0, lastPageIndex < currentPayloads.count {
      return lastPageIndex
    }
    return nil
  }

  private static func applyMetadata(_ metadata: ShortVideoMetadata?, to model: TUIPlayerVideoModel, notify: Bool) {
    if let metadata {
      model.extInfo = metadata.dictionaryRepresentation()
    } else {
      model.extInfo = nil
    }
    if notify {
      model.extInfoChangeNotify()
    }
  }

  private static func applySubtitles(_ subtitles: [ShortVideoSubtitle]?, to model: TUIPlayerVideoModel) {
    guard let subtitles = subtitles, !subtitles.isEmpty else {
      model.subtitles = nil
      return
    }
    model.subtitles = subtitles.map { $0.makeModel() }
  }

  private func shouldAppend(
    previous: [ShortVideoPayload],
    current: [ShortVideoPayload]
  ) -> Bool {
    guard !previous.isEmpty else { return false }
    guard current.count >= previous.count else { return false }
    for index in 0..<previous.count {
      if !previous[index].hasSameIdentity(as: current[index]) {
        return false
      }
    }
    return current.count > previous.count
  }

  private func notifyPageChanged(index: Int) {
    let previousStartedIndex = lastPlaybackStartedIndex
    let total = currentPayloads.count
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
    lastPlaybackStartedIndex = nil
    lastPageIndex = index
    updateOverlay(for: index)
    if let previousStartedIndex,
       previousStartedIndex != index {
      emitPlaybackEndIfPossible(indexOverride: previousStartedIndex)
    }
    onPageChanged?([
      "index": index,
      "total": total,
    ])
  }

  private static func makeEntry(from dictionary: [String: Any]) -> (model: TUIPlayerVideoModel, payload: ShortVideoPayload)? {
    guard let payload = ShortVideoPayload(dictionary: dictionary) else {
      return nil
    }
    let model = TUIPlayerVideoModel()
    if let appId = payload.appId {
      model.appId = appId
    }
    if let fileId = payload.fileId {
      model.fileId = fileId
    }
    if let url = payload.url {
      model.videoUrl = url
    }
    if let cover = payload.coverPictureUrl {
      model.coverPictureUrl = cover
    }
    if let pSign = payload.pSign {
      model.pSign = pSign
    }
    if let duration = payload.duration {
      model.duration = String(duration)
    }
    if let videoConfig = payload.videoConfig {
      let config = TUIPlayerVideoConfig()
      if let buffer = videoConfig.preloadBufferSizeInMB {
        config.mPreloadBufferSizeInMB = Float(buffer)
      }
      if let preDownload = videoConfig.preDownloadSize {
        config.preDownloadSize = Float(preDownload)
      }
      model.config = config
    }
    TuiplayerShortVideoView.applyMetadata(payload.meta, to: model, notify: false)
    TuiplayerShortVideoView.applySubtitles(payload.subtitles, to: model)
    return (model, payload)
  }

  private static func serialize(payload: ShortVideoPayload) -> [String: Any] {
    return payload.dictionaryRepresentation()
  }

  private func applyPlayModeIfNeeded() {
    let value = playMode.intValue
    guard value >= 0 else { return }
    guard let mode = TUIPlayMode(rawValue: UInt(value)) else { return }
    shortVideoView.setPlaymode(mode)
  }

  private func applyInitialIndexIfPossible() {
    guard let index = pendingInitialIndex,
          index >= 0,
          index < currentModels.count else {
      return
    }
    DispatchQueue.main.async { [weak self] in
      guard let self else { return }
      guard let pending = self.pendingInitialIndex, pending == index else { return }
      if index < self.shortVideoView.videos.count {
        self.shortVideoView.didScrollToCellWithIndex(index, animated: false)
        self.notifyPageChanged(index: index)
        self.pendingInitialIndex = nil
      }
    }
  }

  private func dispatchReadyEventIfNeeded() {
    guard !hasDispatchedReady, let callback = onReady else { return }
    hasDispatchedReady = true
    callback([:])
  }

  private func updateRenderModePreference(_ mode: TuiplayerRenderModePreference) {
    if requestedRenderMode == mode {
      return
    }
    requestedRenderMode = mode
    customVodStrategy?.mRenderMode = mode.nativeValue
    applyRenderModeToActivePlayers()
  }

  private func applyRenderModeToActivePlayers() {
    let nativeMode = requestedRenderMode.nativeValue
    for player in vodPlayerByModel.values {
      player.setRenderMode(nativeMode)
    }
  }

  private func applySubtitleStyleToActivePlayers() {
    for player in vodPlayerByModel.values {
      applySubtitleStyle(to: player)
    }
    if
      let currentId = currentVodPlayerIdentifier,
      let player = vodPlayerByModel[currentId]
    {
      applySubtitleStyle(to: player)
    }
  }

  private func applySubtitleStyle(to player: TUITXVodPlayer) {
    if let style = subtitleRenderStyle?.makeNativeModel() {
      player.setSubtitleStyle(style)
    } else {
      let model = TXPlayerSubtitleRenderModel()
      player.setSubtitleStyle(model)
    }
  }

  private func applyStrategies() {
    let vodStrategy = customVodStrategy ?? {
      let strategy = TUIPlayerVodStrategyModel()
      strategy.mRenderMode = requestedRenderMode.nativeValue
      return strategy
    }()
    vodStrategy.mRenderMode = requestedRenderMode.nativeValue
    shortVideoView.setShortVideoStrategyModel(vodStrategy)
    if let liveStrategy = customLiveStrategy {
      shortVideoView.setShortVideoLiveStrategyModel(liveStrategy)
    }
    applyRenderModeToActivePlayers()
  }

  private func applyLayerConfiguration() {
    guard let config = layerConfig else { return }
    let selector = NSSelectorFromString("shared")
    let uiManager: TUIPlayerShortVideoUIManager
    if let managerClass = NSClassFromString("TUIPlayerShortVideoUIManager") as? NSObject.Type,
       managerClass.responds(to: selector),
       let unmanaged = managerClass.perform(selector),
       let sharedManager = unmanaged.takeUnretainedValue() as? TUIPlayerShortVideoUIManager {
      uiManager = sharedManager
    } else {
      uiManager = TUIPlayerShortVideoUIManager()
    }
    if let vodName = config.vodLayerClassNames.first,
       let vodClass = resolveClass(named: vodName) as? TUIPlayerShortVideoControl.Type {
      uiManager.setControlViewClass(vodClass)
    }
    if let liveName = config.liveLayerClassNames.first,
       let liveClass = resolveClass(named: liveName) as? TUIPlayerShortVideoLiveControl.Type {
      uiManager.setControlViewClass(liveClass, viewType: TUI_ITEM_VIEW_TYPE_LIVE)
    }
    if let customName = config.customLayerClassNames.first,
       let customClass = resolveClass(named: customName) as? TUIPlayerShortVideoCustomControl.Type {
      uiManager.setControlViewClass(customClass, viewType: TUI_ITEM_VIEW_TYPE_CUSTOM)
    }
  }

  private func resolveClass(named name: String) -> AnyClass? {
    if let cls = NSClassFromString(name) {
      return cls
    }
    let bundleName = Bundle.main.infoDictionary?["CFBundleName"] as? String
    let candidates = [
      bundleName.flatMap { "\($0).\(name)" },
      "TUIPlayerShortVideoDemo.\(name)"
    ].compactMap { $0 }
    for candidate in candidates {
      if let cls = NSClassFromString(candidate) {
        return cls
      }
    }
    return nil
  }

  private func makeVodStrategy(from dictionary: [String: Any]) -> TUIPlayerVodStrategyModel? {
    let strategy = TUIPlayerVodStrategyModel()
    strategy.mRenderMode = requestedRenderMode.nativeValue
    if let value = dictionary["preloadCount"] as? NSNumber {
      strategy.mPreloadConcurrentCount = value.intValue
    } else if let value = dictionary["preloadCount"] as? Int {
      strategy.mPreloadConcurrentCount = value
    }
    if let value = dictionary["preDownloadSize"] as? NSNumber {
      strategy.preDownloadSize = value.floatValue
    } else if let value = dictionary["preDownloadSize"] as? Double {
      strategy.preDownloadSize = Float(value)
    }
    if let value = dictionary["preLoadBufferSize"] as? NSNumber {
      strategy.mPreloadBufferSizeInMB = value.floatValue
    } else if let value = dictionary["preLoadBufferSize"] as? Double {
      strategy.mPreloadBufferSizeInMB = Float(value)
    }
    if let value = dictionary["maxBufferSize"] as? NSNumber {
      strategy.maxBufferSize = value.floatValue
    } else if let value = dictionary["maxBufferSize"] as? Double {
      strategy.maxBufferSize = Float(value)
    }
    if let preferred = dictionary["preferredResolution"] as? [String: Any] {
      if let width = preferred["width"] as? NSNumber,
         let height = preferred["height"] as? NSNumber {
        strategy.mPreferredResolution = width.longValue * height.longValue
      } else if let width = preferred["width"] as? Int,
                let height = preferred["height"] as? Int {
        strategy.mPreferredResolution = Int64(width * height)
      }
    }
    if let value = dictionary["progressInterval"] as? NSNumber {
      strategy.mProgressInterval = value.longValue
    } else if let value = dictionary["progressInterval"] as? Int {
      strategy.mProgressInterval = Int64(value)
    }
    if let value = dictionary["mediaType"] as? NSNumber,
       let type = TUI_Enum_MediaType(rawValue: value.intValue) {
      strategy.mediaType = type
    }
    if let resume = dictionary["resumeMode"] as? String {
      switch resume {
      case "TUI_RESUM_MODEL_LAST", "LAST":
        strategy.mResumeModel = .TUI_RESUM_MODEL_LAST
      case "TUI_RESUM_MODEL_PLAYED", "PLAYED":
        strategy.mResumeModel = .TUI_RESUM_MODEL_PLAYED
      default:
        strategy.mResumeModel = .TUI_RESUM_MODEL_NONE
      }
    }
    if let value = dictionary["enableAutoBitrate"] as? Bool {
      strategy.enableAutoBitrate = value
    }
    if let value = dictionary["enableAccurateSeek"] as? Bool {
      strategy.enableAccurateSeek = value
    }
    if let value = dictionary["audioNormalization"] as? NSNumber {
      strategy.audioNormalization = value.floatValue
    } else if let value = dictionary["audioNormalization"] as? Double {
      strategy.audioNormalization = Float(value)
    }
    if let value = dictionary["retainPreVod"] as? Bool {
      strategy.enableLastPrePlay = value
    }
    if let value = dictionary["superResolutionMode"] as? String {
      switch value.uppercased() {
      case "TUI_SUPERRESOLUTION_TSR", "TSR":
        strategy.superResolutionType = .TUI_SuperResolution_TSR
      default:
        strategy.superResolutionType = .TUI_SuperResolution_NONE
      }
    }
    if let headers = dictionary["headers"] as? [String: Any] {
      strategy.headers = headers
    }
    return strategy
  }

  private static func makeLiveStrategy(from dictionary: [String: Any]) -> TUIPlayerLiveStrategyModel? {
    let strategy = TUIPlayerLiveStrategyModel()
    if let value = dictionary["retainPreLive"] as? Bool {
      strategy.enableLastPrePlay = value
    }
    if let value = dictionary["renderMode"] as? NSNumber,
       let mode = V2TXLiveFillMode(rawValue: value.intValue) {
      strategy.mRenderMode = mode
    }
    if let value = dictionary["enablePictureInPicture"] as? Bool {
      strategy.enablePictureInPicture = value
    }
    if let value = dictionary["volume"] as? NSNumber {
      strategy.volume = value.uintValue
    } else if let value = dictionary["volume"] as? Int {
      strategy.volume = UInt(value)
    }
    if let value = dictionary["maxAutoAdjustCacheTime"] as? NSNumber {
      strategy.maxAutoAdjustCacheTime = value.floatValue
    } else if let value = dictionary["maxAutoAdjustCacheTime"] as? Double {
      strategy.maxAutoAdjustCacheTime = Float(value)
    }
    if let value = dictionary["minAutoAdjustCacheTime"] as? NSNumber {
      strategy.minAutoAdjustCacheTime = value.floatValue
    } else if let value = dictionary["minAutoAdjustCacheTime"] as? Double {
      strategy.minAutoAdjustCacheTime = Float(value)
    }
    if let value = dictionary["showDebugView"] as? Bool {
      strategy.isShowDebugView = value
    }
    return strategy
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
        let entry = TuiplayerShortVideoView.makeEntry(from: source)
      else {
        throw VodPlayerCommandError.invalidParameter("source")
      }
      player.startVodPlayWithModel(entry.model)
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
      if player.duration > 0 {
        return NSNumber(value: player.duration)
      }
      if let cached = durationByModel[ObjectIdentifier(player)], cached > 0 {
        return NSNumber(value: cached)
      }
      return NSNumber(value: 0)
    case "getCurrentPlaybackTime":
      if player.currentPlaybackTime >= 0 {
        return NSNumber(value: player.currentPlaybackTime)
      }
      if let cached = currentTimeByModel[ObjectIdentifier(player)] {
        return NSNumber(value: cached)
      }
      return NSNumber(value: 0)
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
      let resolved =
        TuiplayerRenderModePreference(rawValue: mode.intValue) ?? .fill
      updateRenderModePreference(resolved)
      player.setRenderMode(resolved.nativeValue)
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

  func commandStartPlayIndex(_ index: Int, smooth: Bool) {
    DispatchQueue.main.async {
      guard index >= 0 else { return }
      if index < self.currentModels.count && index < self.shortVideoView.videos.count {
        self.shortVideoView.didScrollToCellWithIndex(index, animated: smooth)
        self.notifyPageChanged(index: index)
        if self.isAutoPlayEnabled && !self.isManuallyPaused {
          self.resume()
        }
      } else {
        self.pendingInitialIndex = index
        self.applyInitialIndexIfPossible()
      }
    }
  }

  func commandSetPlayMode(_ mode: Int) {
    playMode = NSNumber(value: mode)
  }

  func commandRelease() {
    DispatchQueue.main.async {
      for player in self.vodPlayerByModel.values {
        player.removeDelegate(self)
        self.clearSubtitlePreference(for: player)
      }
      self.vodPlayerByModel.removeAll()
      self.currentVodPlayerIdentifier = nil
      self.isManuallyPaused = false
      self.pendingInitialIndex = nil
      self.hasDispatchedTopReached = false
      self.shortVideoView.destoryPlayer()
      self.shortVideoView.startLoading()
      self.updateOverlay(for: nil)
      self.setOverlayPaused(true)
    }
  }

  func commandResume() {
    DispatchQueue.main.async {
      self.isManuallyPaused = false
      if self.paused.boolValue {
        self.paused = false
      } else if self.isAutoPlayEnabled {
        self.resume()
      }
      self.setOverlayPaused(self.isOverlayPaused())
    }
  }

  func commandSwitchResolution(_ resolution: Double, target: Int) {
    DispatchQueue.main.async {
      let resolved = Int64(resolution.rounded())
      self.shortVideoView.switchResolution(resolved, index: target)
    }
  }

  func commandPausePreload() {
    DispatchQueue.main.async {
      self.shortVideoView.pausePreload()
    }
  }

  func commandResumePreload() {
    DispatchQueue.main.async {
      self.shortVideoView.resumePreload()
    }
  }

  func commandSetUserInputEnabled(_ enabled: Bool) {
    userInputEnabled = NSNumber(value: enabled)
  }

  func commandSyncPlaybackState() {
    DispatchQueue.main.async {
      if self.isAutoPlayEnabled && !self.isManuallyPaused {
        self.resume()
      } else {
        self.pause()
      }
      self.setOverlayPaused(self.isOverlayPaused())
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
  func scrollViewDidScrollContentOffset(_ contentOffset: CGPoint) {
    let offsetY = contentOffset.y
    if offsetY < 0 {
      let offset = Int(abs(offsetY))
      if offset > 0, !hasDispatchedTopReached {
        hasDispatchedTopReached = true
        onTopReached?(["offset": offset])
      }
    } else if offsetY > 0 {
      hasDispatchedTopReached = false
    }
    lastKnownScrollOffset = max(0, offsetY)
  }

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
    let total = currentPayloads.count
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
    player.setRenderMode(requestedRenderMode.nativeValue)
    applySubtitleStyle(to: player)
    markSubtitlePreference(for: player, model: model)
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
    let isCurrentPlayer = isEventFromCurrentPlayer(player)
    switch status {
    case .TUITXVodPlayerStatusPrepared:
      setOverlayPaused(false)
      emitVodEvent("onPlayPrepare")
    case .TUITXVodPlayerStatusLoading:
      setOverlayPaused(true)
      emitVodEvent("onPlayLoading")
    case .TUITXVodPlayerStatusPlaying:
      setOverlayPaused(false)
      emitVodEvent("onPlayBegin")
      if isCurrentPlayer {
        emitPlaybackStartIfNeeded()
      }
    case .TUITXVodPlayerStatusPaused:
      setOverlayPaused(true)
      emitVodEvent("onPlayPause")
    case .TUITXVodPlayerStatusEnded:
      setOverlayPaused(true)
      emitVodEvent("onPlayEnd")
      if isCurrentPlayer {
        emitPlaybackEndIfPossible()
      }
    case .TUITXVodPlayerStatusError:
      setOverlayPaused(true)
      emitVodEvent("onError", ["code": -1, "message": "Player status error"])
    case .TUITXVodPlayerStatusLoopCompleted:
      emitVodEvent("onLoopCompleted")
    default:
      break
    }
  }

  func player(_ player: TUITXVodPlayer, currentTime: Float, totalTime: Float, progress: Float) {
    let identifier = ObjectIdentifier(player)
    if totalTime > 0 {
      durationByModel[identifier] = Double(totalTime)
    }
    currentTimeByModel[identifier] = Double(currentTime)
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

extension TuiplayerShortVideoView: TuiplayerInfoOverlayDelegate {
  func overlay(_ overlay: TuiplayerInfoOverlayView, didTriggerAction action: String) {
    emitOverlayActionForCurrentIndex(action)
  }
}

fileprivate protocol TuiplayerInfoOverlayDelegate: AnyObject {
  func overlay(_ overlay: TuiplayerInfoOverlayView, didTriggerAction action: String)
}

fileprivate final class TuiplayerInfoOverlayView: UIView {
  weak var delegate: TuiplayerInfoOverlayDelegate?

  private let containerView = UIView()
  private let gradientLayer = CAGradientLayer()
  private let contentStack = UIStackView()
  private let headerStack = UIStackView()
  private let authorStack = UIStackView()
  private let avatarImageView = UIImageView()
  private let authorLabel = UILabel()
  private let followButton = UIButton(type: .system)
  private let titleLabel = UILabel()
  private let actionStack = UIStackView()
  private let likeButton = UIButton(type: .system)
  private let commentButton = UIButton(type: .system)
  private let favoriteButton = UIButton(type: .system)
  private let watchMoreButton = UIButton(type: .system)
  private let pauseIndicator = UIImageView()

  private var avatarTask: URLSessionDataTask?
  private var currentAvatarURL: String?

  private static var likeHighlightColor: UIColor {
    if #available(iOS 13.0, *) {
      return UIColor.systemPink
    }
    return UIColor(red: 1.0, green: 0.27, blue: 0.55, alpha: 1.0)
  }

  private static var favoriteHighlightColor: UIColor {
    if #available(iOS 13.0, *) {
      return UIColor.systemYellow
    }
    return UIColor(red: 1.0, green: 0.84, blue: 0.0, alpha: 1.0)
  }

  private static var followBackgroundColor: UIColor {
    if #available(iOS 13.0, *) {
      return UIColor.systemPink
    }
    return UIColor(red: 1.0, green: 0.27, blue: 0.55, alpha: 1.0)
  }

  private enum Action: String {
    case like
    case comment
    case favorite
    case watchMore
    case author
    case avatar
  }

  override init(frame: CGRect) {
    super.init(frame: frame)
    backgroundColor = .clear
    isUserInteractionEnabled = true
    setupUI()
  }

  required init?(coder: NSCoder) {
    fatalError("init(coder:) has not been implemented")
  }

  override func layoutSubviews() {
    super.layoutSubviews()
    CATransaction.begin()
    CATransaction.setDisableActions(true)
    gradientLayer.frame = containerView.bounds
    CATransaction.commit()
    avatarImageView.layer.cornerRadius = avatarImageView.bounds.height / 2
  }

  override func hitTest(_ point: CGPoint, with event: UIEvent?) -> UIView? {
    let target = super.hitTest(point, with: event)
    guard let target else { return nil }
    if target === self || target === containerView {
      return nil
    }
    return target
  }

  func update(with payload: ShortVideoPayload?) {
    avatarTask?.cancel()
    avatarTask = nil
    currentAvatarURL = nil

    guard
      let payload,
      let metadata = payload.meta
    else {
      containerView.isHidden = true
      watchMoreButton.isHidden = true
      followButton.isHidden = true
      avatarImageView.image = TuiplayerOverlayImageLoader.placeholder
      return
    }

    containerView.isHidden = false

    let fallbackName = payload.fileId?.isEmpty == false ? payload.fileId : payload.url
    authorLabel.text = metadata.authorName?.isEmpty == false ? metadata.authorName : fallbackName
    authorLabel.isHidden = (authorLabel.text?.isEmpty ?? true)

    if let title = metadata.title, !title.isEmpty {
      titleLabel.text = title
      titleLabel.isHidden = false
    } else {
      titleLabel.text = nil
      titleLabel.isHidden = true
    }

    updateActionButton(likeButton, title: formatCount(metadata.likeCount), highlighted: metadata.isLiked == true, highlightColor: TuiplayerInfoOverlayView.likeHighlightColor)
    updateActionButton(commentButton, title: formatCount(metadata.commentCount), highlighted: false, highlightColor: UIColor.white)
    updateActionButton(favoriteButton, title: formatCount(metadata.favoriteCount), highlighted: metadata.isBookmarked == true, highlightColor: TuiplayerInfoOverlayView.favoriteHighlightColor)

    followButton.isHidden = metadata.isFollowed == true

    if let watchText = metadata.watchMoreText, !watchText.isEmpty {
      watchMoreButton.setTitle(watchText, for: .normal)
      watchMoreButton.isHidden = false
    } else {
      watchMoreButton.isHidden = true
      watchMoreButton.setTitle(nil, for: .normal)
    }

    updateAvatar(with: metadata.authorAvatar)
  }

  func setPlaybackPaused(_ paused: Bool) {
    DispatchQueue.main.async {
      let targetAlpha: CGFloat = paused ? 1 : 0
      if paused {
        self.pauseIndicator.isHidden = false
      }
      UIView.animate(withDuration: 0.2, animations: {
        self.pauseIndicator.alpha = targetAlpha
      }, completion: { finished in
        if !paused {
          self.pauseIndicator.isHidden = true
        }
      })
    }
  }

  private func setupUI() {
    containerView.translatesAutoresizingMaskIntoConstraints = false
    containerView.layer.masksToBounds = true
    containerView.layer.cornerRadius = 16
    containerView.backgroundColor = UIColor.black.withAlphaComponent(0.35)
    containerView.isHidden = true
    gradientLayer.colors = [
      UIColor.black.withAlphaComponent(0.55).cgColor,
      UIColor.black.withAlphaComponent(0.0).cgColor,
    ]
    gradientLayer.startPoint = CGPoint(x: 0.5, y: 1.0)
    gradientLayer.endPoint = CGPoint(x: 0.5, y: 0.0)
    containerView.layer.insertSublayer(gradientLayer, at: 0)

    addSubview(containerView)
    NSLayoutConstraint.activate([
      containerView.leadingAnchor.constraint(equalTo: leadingAnchor, constant: 16),
      containerView.trailingAnchor.constraint(equalTo: trailingAnchor, constant: -16),
      containerView.bottomAnchor.constraint(equalTo: safeAreaLayoutGuide.bottomAnchor, constant: -20),
    ])

    contentStack.axis = .vertical
    contentStack.spacing = 12
    contentStack.translatesAutoresizingMaskIntoConstraints = false
    containerView.addSubview(contentStack)
    NSLayoutConstraint.activate([
      contentStack.leadingAnchor.constraint(equalTo: containerView.leadingAnchor, constant: 16),
      contentStack.trailingAnchor.constraint(equalTo: containerView.trailingAnchor, constant: -16),
      contentStack.topAnchor.constraint(equalTo: containerView.topAnchor, constant: 16),
      contentStack.bottomAnchor.constraint(equalTo: containerView.bottomAnchor, constant: -16),
    ])

    headerStack.axis = .horizontal
    headerStack.spacing = 12
    headerStack.alignment = .center

    avatarImageView.translatesAutoresizingMaskIntoConstraints = false
    avatarImageView.backgroundColor = UIColor.white.withAlphaComponent(0.2)
    avatarImageView.contentMode = .scaleAspectFill
    avatarImageView.clipsToBounds = true
    avatarImageView.isUserInteractionEnabled = true
    avatarImageView.image = TuiplayerOverlayImageLoader.placeholder
    headerStack.addArrangedSubview(avatarImageView)
    NSLayoutConstraint.activate([
      avatarImageView.widthAnchor.constraint(equalToConstant: 48),
      avatarImageView.heightAnchor.constraint(equalToConstant: 48),
    ])
    let avatarTap = UITapGestureRecognizer(target: self, action: #selector(handleAvatarTapped))
    avatarImageView.addGestureRecognizer(avatarTap)

    authorStack.axis = .vertical
    authorStack.spacing = 4
    authorStack.alignment = .leading

    authorLabel.font = UIFont.systemFont(ofSize: 16, weight: .semibold)
    authorLabel.textColor = .white
    authorLabel.numberOfLines = 1
    authorStack.addArrangedSubview(authorLabel)

    followButton.setTitle("关注", for: .normal)
    followButton.setTitleColor(.white, for: .normal)
    followButton.titleLabel?.font = UIFont.systemFont(ofSize: 13, weight: .semibold)
    followButton.backgroundColor = TuiplayerInfoOverlayView.followBackgroundColor.withAlphaComponent(0.9)
    followButton.layer.cornerRadius = 12
    followButton.contentEdgeInsets = UIEdgeInsets(top: 4, left: 12, bottom: 4, right: 12)
    followButton.addTarget(self, action: #selector(handleAuthorTapped), for: .touchUpInside)
    authorStack.addArrangedSubview(followButton)

    let authorTap = UITapGestureRecognizer(target: self, action: #selector(handleAuthorTapped))
    authorStack.addGestureRecognizer(authorTap)
    authorStack.isUserInteractionEnabled = true

    headerStack.addArrangedSubview(authorStack)

    contentStack.addArrangedSubview(headerStack)

    titleLabel.font = UIFont.systemFont(ofSize: 18, weight: .medium)
    titleLabel.textColor = .white
    titleLabel.numberOfLines = 2
    titleLabel.setContentCompressionResistancePriority(.defaultLow, for: .vertical)
    contentStack.addArrangedSubview(titleLabel)

    actionStack.axis = .horizontal
    actionStack.spacing = 18
    actionStack.alignment = .center

    configureActionButton(likeButton, systemName: "hand.thumbsup.fill", action: #selector(handleLikeTapped))
    configureActionButton(commentButton, systemName: "text.bubble.fill", action: #selector(handleCommentTapped))
    configureActionButton(favoriteButton, systemName: "star.fill", action: #selector(handleFavoriteTapped))

    actionStack.addArrangedSubview(likeButton)
    actionStack.addArrangedSubview(commentButton)
    actionStack.addArrangedSubview(favoriteButton)

    contentStack.addArrangedSubview(actionStack)

    watchMoreButton.setTitleColor(.white, for: .normal)
    watchMoreButton.titleLabel?.font = UIFont.systemFont(ofSize: 15, weight: .semibold)
    watchMoreButton.contentHorizontalAlignment = .leading
    watchMoreButton.addTarget(self, action: #selector(handleWatchMoreTapped), for: .touchUpInside)
    watchMoreButton.isHidden = true
    contentStack.addArrangedSubview(watchMoreButton)

    pauseIndicator.translatesAutoresizingMaskIntoConstraints = false
    if #available(iOS 13.0, *) {
      pauseIndicator.image = UIImage(systemName: "pause.circle.fill")?.withRenderingMode(.alwaysTemplate)
      pauseIndicator.tintColor = UIColor.white.withAlphaComponent(0.9)
    }
    pauseIndicator.alpha = 0
    pauseIndicator.isHidden = true
    addSubview(pauseIndicator)
    NSLayoutConstraint.activate([
      pauseIndicator.centerXAnchor.constraint(equalTo: centerXAnchor),
      pauseIndicator.centerYAnchor.constraint(equalTo: centerYAnchor),
      pauseIndicator.widthAnchor.constraint(equalToConstant: 64),
      pauseIndicator.heightAnchor.constraint(equalToConstant: 64),
    ])

  }

  private func configureActionButton(_ button: UIButton, systemName: String, action: Selector) {
    button.tintColor = UIColor.white
    button.setTitleColor(UIColor.white, for: .normal)
    button.titleLabel?.font = UIFont.systemFont(ofSize: 15, weight: .medium)
    button.contentHorizontalAlignment = .leading
    button.contentEdgeInsets = UIEdgeInsets(top: 6, left: 10, bottom: 6, right: 10)
    if #available(iOS 13.0, *) {
      let image = UIImage(systemName: systemName)?.withRenderingMode(.alwaysTemplate)
      button.setImage(image, for: .normal)
      button.imageEdgeInsets = UIEdgeInsets(top: 0, left: -4, bottom: 0, right: 6)
    }
    button.backgroundColor = UIColor.white.withAlphaComponent(0.08)
    button.layer.cornerRadius = 14
    button.setTitle("0", for: .normal)
    button.addTarget(self, action: action, for: .touchUpInside)
  }

  private func updateActionButton(_ button: UIButton, title: String, highlighted: Bool, highlightColor: UIColor) {
    let tint: UIColor = highlighted ? highlightColor : UIColor.white.withAlphaComponent(0.92)
    button.tintColor = tint
    button.setTitle(title, for: .normal)
    button.setTitleColor(tint, for: .normal)
  }

  private func updateAvatar(with urlString: String?) {
    avatarImageView.image = TuiplayerOverlayImageLoader.placeholder
    guard let urlString = urlString, let url = URL(string: urlString) else {
      return
    }
    currentAvatarURL = urlString
    if let cached = TuiplayerOverlayImageLoader.shared.cachedImage(for: url) {
      avatarImageView.image = cached
      return
    }
    let task = URLSession.shared.dataTask(with: url) { [weak self] data, _, _ in
      guard let self, let data = data, let image = UIImage(data: data) else { return }
      TuiplayerOverlayImageLoader.shared.store(image: image, for: url)
      guard self.currentAvatarURL == urlString else { return }
      DispatchQueue.main.async {
        self.avatarImageView.image = image
      }
    }
    avatarTask = task
    task.resume()
  }

  private func formatCount(_ value: Double?) -> String {
    let raw = Int64(value ?? 0)
    let normalized = max(0, raw)
    if normalized >= 100_000 {
      return "10w+"
    }
    if normalized >= 10_000 {
      let major = normalized / 10_000
      let minor = (normalized % 10_000) / 1000
      return minor == 0 ? "\(major)w" : "\(major).\(minor)w"
    }
    if normalized >= 1_000 {
      let major = normalized / 1_000
      let minor = (normalized % 1_000) / 100
      return minor == 0 ? "\(major)k" : "\(major).\(minor)k"
    }
    return String(normalized)
  }

  @objc private func handleLikeTapped() {
    delegate?.overlay(self, didTriggerAction: Action.like.rawValue)
  }

  @objc private func handleCommentTapped() {
    delegate?.overlay(self, didTriggerAction: Action.comment.rawValue)
  }

  @objc private func handleFavoriteTapped() {
    delegate?.overlay(self, didTriggerAction: Action.favorite.rawValue)
  }

  @objc private func handleWatchMoreTapped() {
    delegate?.overlay(self, didTriggerAction: Action.watchMore.rawValue)
  }

  @objc private func handleAuthorTapped() {
    delegate?.overlay(self, didTriggerAction: Action.author.rawValue)
  }

  @objc private func handleAvatarTapped() {
    delegate?.overlay(self, didTriggerAction: Action.avatar.rawValue)
  }

}

fileprivate final class TuiplayerOverlayImageLoader {
  static let shared = TuiplayerOverlayImageLoader()
  private let cache = NSCache<NSURL, UIImage>()

  static let placeholder: UIImage = {
    if #available(iOS 13.0, *) {
      return UIImage(systemName: "person.circle")?.withRenderingMode(.alwaysTemplate) ?? UIImage()
    }
    return UIImage()
  }()

  func cachedImage(for url: URL) -> UIImage? {
    cache.object(forKey: url as NSURL)
  }

  func store(image: UIImage, for url: URL) {
    cache.setObject(image, forKey: url as NSURL)
  }
}

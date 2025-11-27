package com.tuiplayer.shortvideo

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.annotations.ReactProp
import com.tuiplayer.shortvideo.TuiplayerShortVideoEndReachedEvent
import com.tuiplayer.shortvideo.TuiplayerShortVideoPageChangedEvent
import com.tuiplayer.shortvideo.TuiplayerShortVideoReadyEvent

@ReactModule(name = TuiplayerShortVideoViewManager.NAME)
internal class TuiplayerShortVideoViewManager : SimpleViewManager<TuiplayerShortVideoView>() {

  companion object {
    const val NAME = "TuiplayerShortVideoView"

    private const val COMMAND_START_PLAY_INDEX = 1
    private const val COMMAND_SET_PLAY_MODE = 2
    private const val COMMAND_RELEASE = 3
    private const val COMMAND_RESUME = 4
    private const val COMMAND_SWITCH_RESOLUTION = 5
    private const val COMMAND_PAUSE_PRELOAD = 6
    private const val COMMAND_RESUME_PRELOAD = 7
    private const val COMMAND_SET_USER_INPUT_ENABLED = 8
    private const val COMMAND_UPDATE_META = 9
    private const val COMMAND_SYNC_PLAYBACK_STATE = 10
    private const val COMMAND_SET_OVERLAY_VISIBLE = 11
    private const val COMMAND_SET_TOP_LOADING_VISIBLE = 12
    private const val COMMAND_SET_BOTTOM_LOADING_VISIBLE = 13
  }

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): TuiplayerShortVideoView {
    return TuiplayerShortVideoView(context)
  }

  @ReactProp(name = "sources")
  fun propSources(view: TuiplayerShortVideoView, value: ReadableArray?) {
    view.setSources(parseSources(value))
  }

  @ReactProp(name = "autoPlay", defaultBoolean = true)
  fun propAutoPlay(view: TuiplayerShortVideoView, value: Boolean) {
    view.setAutoPlay(value)
  }

  @ReactProp(name = "paused", defaultBoolean = false)
  fun propPaused(view: TuiplayerShortVideoView, value: Boolean) {
    view.setPaused(value)
  }

  @ReactProp(name = "initialIndex", defaultInt = -1)
  fun propInitialIndex(view: TuiplayerShortVideoView, value: Int) {
    if (value >= 0) {
      view.setInitialIndex(value)
    }
  }

  @ReactProp(name = "playMode", defaultInt = -1)
  fun propPlayMode(view: TuiplayerShortVideoView, value: Int) {
    view.setPlayMode(value)
  }

  @ReactProp(name = "userInputEnabled", defaultBoolean = true)
  fun propUserInputEnabled(view: TuiplayerShortVideoView, value: Boolean) {
    view.setUserInputEnabled(value)
  }

  @ReactProp(name = "pageScrollMsPerInch", defaultFloat = Float.NaN)
  fun propPageScrollMsPerInch(view: TuiplayerShortVideoView, value: Float) {
    val resolved = if (value.isNaN()) null else value.toDouble()
    view.setPageScrollMsPerInch(resolved)
  }

  @ReactProp(name = "layers")
  fun propLayers(view: TuiplayerShortVideoView, value: ReadableMap?) {
    view.setLayerConfig(value?.toLayerConfig())
  }

  @ReactProp(name = "vodStrategy")
  fun propVodStrategy(view: TuiplayerShortVideoView, value: ReadableMap?) {
    view.setVodStrategy(value)
  }

  @ReactProp(name = "liveStrategy")
  fun propLiveStrategy(view: TuiplayerShortVideoView, value: ReadableMap?) {
    view.setLiveStrategy(value)
  }

  @ReactProp(name = "subtitleStyle")
  fun propSubtitleStyle(view: TuiplayerShortVideoView, value: ReadableMap?) {
    view.setSubtitleStyle(value)
  }

  override fun getExportedCustomDirectEventTypeConstants(): MutableMap<String, Any> {
    return mutableMapOf(
      TuiplayerShortVideoPageChangedEvent.EVENT_NAME to mapOf("registrationName" to "onPageChanged"),
      TuiplayerShortVideoEndReachedEvent.EVENT_NAME to mapOf("registrationName" to "onEndReached"),
      TuiplayerShortVideoTopReachedEvent.EVENT_NAME to mapOf("registrationName" to "onTopReached"),
      TuiplayerShortVideoVodEvent.EVENT_NAME to mapOf("registrationName" to "onVodEvent"),
      TuiplayerShortVideoReadyEvent.EVENT_NAME to mapOf("registrationName" to "onReady")
    )
  }

  override fun getCommandsMap(): MutableMap<String, Int> {
    return mutableMapOf(
      "startPlayIndex" to COMMAND_START_PLAY_INDEX,
      "setPlayMode" to COMMAND_SET_PLAY_MODE,
      "release" to COMMAND_RELEASE,
      "resume" to COMMAND_RESUME,
      "switchResolution" to COMMAND_SWITCH_RESOLUTION,
      "pausePreload" to COMMAND_PAUSE_PRELOAD,
      "resumePreload" to COMMAND_RESUME_PRELOAD,
      "setUserInputEnabled" to COMMAND_SET_USER_INPUT_ENABLED,
      "updateMeta" to COMMAND_UPDATE_META,
      "syncPlaybackState" to COMMAND_SYNC_PLAYBACK_STATE,
      "setOverlayVisible" to COMMAND_SET_OVERLAY_VISIBLE,
      "setTopLoadingVisible" to COMMAND_SET_TOP_LOADING_VISIBLE,
      "setBottomLoadingVisible" to COMMAND_SET_BOTTOM_LOADING_VISIBLE,
    )
  }

  @Suppress("DEPRECATION")
  override fun receiveCommand(view: TuiplayerShortVideoView, commandId: String, args: ReadableArray?) {
    when (commandId) {
      "startPlayIndex" -> handleStartPlayIndex(view, args)
      "setPlayMode" -> handleSetPlayMode(view, args)
      "release" -> view.commandRelease()
      "resume" -> view.commandResume()
      "switchResolution" -> handleSwitchResolution(view, args)
      "pausePreload" -> view.commandPausePreload()
      "resumePreload" -> view.commandResumePreload()
      "setUserInputEnabled" -> handleSetUserInputEnabled(view, args)
      "updateMeta" -> handleUpdateMeta(view, args)
      "syncPlaybackState" -> view.commandSyncPlaybackState()
      "setOverlayVisible" -> handleSetOverlayVisible(view, args)
      "setTopLoadingVisible" -> handleSetTopLoadingVisible(view, args)
      "setBottomLoadingVisible" -> handleSetBottomLoadingVisible(view, args)
    }
  }

  @Suppress("DEPRECATION")
  override fun receiveCommand(view: TuiplayerShortVideoView, commandId: Int, args: ReadableArray?) {
    when (commandId) {
      COMMAND_START_PLAY_INDEX -> handleStartPlayIndex(view, args)
      COMMAND_SET_PLAY_MODE -> handleSetPlayMode(view, args)
      COMMAND_RELEASE -> view.commandRelease()
      COMMAND_RESUME -> view.commandResume()
      COMMAND_SWITCH_RESOLUTION -> handleSwitchResolution(view, args)
      COMMAND_PAUSE_PRELOAD -> view.commandPausePreload()
      COMMAND_RESUME_PRELOAD -> view.commandResumePreload()
      COMMAND_SET_USER_INPUT_ENABLED -> handleSetUserInputEnabled(view, args)
      COMMAND_UPDATE_META -> handleUpdateMeta(view, args)
      COMMAND_SYNC_PLAYBACK_STATE -> view.commandSyncPlaybackState()
      COMMAND_SET_OVERLAY_VISIBLE -> handleSetOverlayVisible(view, args)
      COMMAND_SET_TOP_LOADING_VISIBLE -> handleSetTopLoadingVisible(view, args)
      COMMAND_SET_BOTTOM_LOADING_VISIBLE -> handleSetBottomLoadingVisible(view, args)
    }
  }

  private fun handleStartPlayIndex(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val index = args?.getIntOrNull(0) ?: return
    val smooth = args?.getBooleanOrDefault(1, false) ?: false
    view.commandStartPlayIndex(index, smooth)
  }

  private fun handleSetPlayMode(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val mode = args?.getIntOrNull(0) ?: return
    view.commandSetPlayMode(mode)
  }

  private fun handleSwitchResolution(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val resolution = args?.getDoubleOrNull(0) ?: return
    val target = args.getIntOrDefault(1, 0)
    view.commandSwitchResolution(resolution, target)
  }

  private fun handleSetUserInputEnabled(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val enabled = args?.getBooleanOrDefault(0, true) ?: true
    view.commandSetUserInputEnabled(enabled)
  }

  private fun handleUpdateMeta(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val index = args?.getIntOrNull(0) ?: return
    val metaMap = args.getMapOrNull(1) ?: return
    val metadata = metaMap.toShortVideoMetadata() ?: return
    view.updateMetadata(index, metadata)
  }

  private fun handleSetOverlayVisible(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val visible = args?.getBooleanOrDefault(0, true) ?: true
    view.commandSetOverlayVisible(visible)
  }

  private fun handleSetTopLoadingVisible(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val visible = args?.getBooleanOrDefault(0, true) ?: true
    view.commandSetTopLoadingVisible(visible)
  }

  private fun handleSetBottomLoadingVisible(view: TuiplayerShortVideoView, args: ReadableArray?) {
    val visible = args?.getBooleanOrDefault(0, true) ?: true
    view.commandSetBottomLoadingVisible(visible)
  }

  private fun parseSources(value: ReadableArray?): List<TuiplayerShortVideoSource> {
    if (value == null) {
      return emptyList()
    }
    val result = mutableListOf<TuiplayerShortVideoSource>()
    for (index in 0 until value.size()) {
      val map = value.getMap(index) ?: continue
      result.add(map.toSource())
    }
    return result
  }
}

private fun ReadableArray?.getIntOrNull(index: Int): Int? {
  return if (this != null && index < size() && !isNull(index)) getInt(index) else null
}

private fun ReadableArray?.getDoubleOrNull(index: Int): Double? {
  return if (this != null && index < size() && !isNull(index)) getDouble(index) else null
}

private fun ReadableArray?.getBooleanOrDefault(index: Int, default: Boolean): Boolean? {
  return if (this != null && index < size() && !isNull(index)) getBoolean(index) else default
}

private fun ReadableArray?.getIntOrDefault(index: Int, default: Int): Int {
  return if (this != null && index < size() && !isNull(index)) getInt(index) else default
}

private fun ReadableArray?.getMapOrNull(index: Int): ReadableMap? {
  return if (this != null && index < size() && !isNull(index)) getMap(index) else null
}

private fun ReadableMap.getStringOrNull(key: String): String? {
  return if (hasKey(key) && !isNull(key)) getString(key) else null
}

private fun ReadableMap.getDoubleOrNull(key: String): Double? {
  return if (hasKey(key) && !isNull(key)) getDouble(key) else null
}

private fun ReadableMap.getIntOrNull(key: String): Int? {
  return getDoubleOrNull(key)?.toInt()
}

private fun ReadableMap.getBooleanOrNull(key: String): Boolean? {
  return if (hasKey(key) && !isNull(key)) getBoolean(key) else null
}

private fun ReadableMap.getMapOrNull(key: String): ReadableMap? {
  return if (hasKey(key) && !isNull(key)) getMap(key) else null
}

private fun ReadableMap.getArrayOrNull(key: String): ReadableArray? {
  return if (hasKey(key) && !isNull(key)) getArray(key) else null
}

private fun ReadableMap.getTagList(key: String): List<String>? {
  if (!hasKey(key) || isNull(key)) {
    return null
  }
  return when (getType(key)) {
    ReadableType.Array -> getArray(key)?.toStringList()?.takeIf { it.isNotEmpty() }
    ReadableType.String -> parseTagString(getString(key))
    else -> null
  }
}

private fun parseTagString(value: String?): List<String>? {
  if (value.isNullOrBlank()) {
    return null
  }
  val parts = value.split(Regex("[#|/、,，\\s]+"))
    .map { it.trim() }
    .filter { it.isNotEmpty() }
  return if (parts.isEmpty()) null else parts
}

internal fun ReadableArray?.toStringList(): List<String> {
  if (this == null) {
    return emptyList()
  }
  val list = mutableListOf<String>()
  for (index in 0 until size()) {
    val value = getString(index)
    if (!value.isNullOrBlank()) {
      list.add(value)
    }
  }
  return list
}

private fun ReadableMap.toLayerConfig(): LayerConfig {
  val vod = getArrayOrNull("vodLayers").toStringList()
  val live = getArrayOrNull("liveLayers").toStringList()
  val custom = getArrayOrNull("customLayers").toStringList()
  return LayerConfig(vod, live, custom)
}

private fun ReadableMap.toSource(): TuiplayerShortVideoSource {
  val typeString = getStringOrNull("type")
  val sourceType = when (typeString?.lowercase()) {
    "url" -> TuiplayerShortVideoSource.SourceType.URL
    else -> TuiplayerShortVideoSource.SourceType.FILE_ID
  }

  val appId = getDoubleOrNull("appId")?.toInt()
  val fileId = getStringOrNull("fileId")
  val url = getStringOrNull("url")
  val cover = getStringOrNull("coverPictureUrl")
  val pSign = getStringOrNull("pSign")
  val extViewType = getIntOrNull("extViewType")
  val autoPlay = getBooleanOrNull("autoPlay")

  val videoConfigMap = getMapOrNull("videoConfig")
  val videoConfig = if (videoConfigMap != null) {
    TuiplayerShortVideoSource.VideoConfig(
      preloadBufferSizeInMB = videoConfigMap.getDoubleOrNull("preloadBufferSizeInMB")?.toFloat(),
      preDownloadSizeInMB = videoConfigMap.getDoubleOrNull("preDownloadSize")
    )
  } else {
    null
  }
  val metadata = getMapOrNull("meta")?.toShortVideoMetadata()
  val subtitles = getArrayOrNull("subtitles")?.toSubtitleList()

  return TuiplayerShortVideoSource(
    type = sourceType,
    appId = appId,
    fileId = fileId,
    url = url,
    coverPictureUrl = cover,
    pSign = pSign,
    extViewType = extViewType,
    autoPlay = autoPlay,
    videoConfig = videoConfig,
    metadata = metadata,
    subtitles = subtitles
  )
}

private fun ReadableArray?.toSubtitleList(): List<TuiplayerShortVideoSource.Subtitle> {
  if (this == null) {
    return emptyList()
  }
  val result = mutableListOf<TuiplayerShortVideoSource.Subtitle>()
  for (index in 0 until size()) {
    val map = getMap(index) ?: continue
    val url = map.getStringOrNull("url") ?: continue
    val name = map.getStringOrNull("name")
    val mimeType = map.getStringOrNull("mimeType")
    result.add(TuiplayerShortVideoSource.Subtitle(name, url, mimeType))
  }
  return result
}

private fun ReadableMap.toShortVideoMetadata(): TuiplayerShortVideoSource.Metadata? {
  val name = getStringOrNull("name")
  val icon = getStringOrNull("icon")
  val type = getTagList("type")
  val details = getStringOrNull("details")
  val likeCount = getDoubleOrNull("likeCount")?.toLong()
  val favoriteCount = getDoubleOrNull("favoriteCount")?.toLong()
  val isShowPaly = getBooleanOrNull("isShowPaly")
  
  // State fields
  val isLiked = getBooleanOrNull("isLiked")
  val isBookmarked = getBooleanOrNull("isBookmarked")

  android.util.Log.d("TuiplayerMeta", "toShortVideoMetadata - type: $type")

  val metadata = TuiplayerShortVideoSource.Metadata(
    name = name,
    icon = icon,
    type = type,
    details = details,
    likeCount = likeCount,
    favoriteCount = favoriteCount,
    isShowPaly = isShowPaly,
    isLiked = isLiked,
    isBookmarked = isBookmarked
  )
  return metadata.takeIf { it.hasValue }
}

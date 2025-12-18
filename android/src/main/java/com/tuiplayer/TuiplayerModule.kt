package com.tuiplayer

import com.tuiplayer.NativeTuiplayerSpec
import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReadableType
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.IllegalViewOperationException
import com.facebook.react.uimanager.UIManagerHelper
import com.tencent.qcloud.tuiplayer.core.TUIPlayerConfig
import com.tencent.qcloud.tuiplayer.core.TUIPlayerCore
import com.tuiplayer.shortvideo.TuiplayerShortVideoView
import com.tuiplayer.shortvideo.TuiplayerShortVideoSource
import com.tuiplayer.shortvideo.toStringList
import java.lang.reflect.Modifier

@ReactModule(name = TuiplayerModule.NAME)
class TuiplayerModule(reactContext: ReactApplicationContext) :
  NativeTuiplayerSpec(reactContext) {

  init {
    NativeLibraryLoader.ensureLoaded()
  }

  override fun getName(): String {
    return NAME
  }

  override fun initialize(config: ReadableMap?) {
    val enableLog = config?.getBooleanOrNull("enableLog") ?: false
    val licenseUrl = config?.getStringOrNull("licenseUrl")
    val licenseKey = config?.getStringOrNull("licenseKey")

    if (isInitialized && licenseUrl.isNullOrBlank() && licenseKey.isNullOrBlank()) {
      // Already initialised with previous config; nothing to do when no license provided.
      return
    }

    UiThreadUtil.runOnUiThread {
      val builder = TUIPlayerConfig.Builder().enableLog(enableLog)
      if (!licenseUrl.isNullOrBlank() && !licenseKey.isNullOrBlank()) {
        builder.licenseUrl(licenseUrl)
        builder.licenseKey(licenseKey)
      }
      TUIPlayerCore.init(reactApplicationContext, builder.build())
      isInitialized = true
    }
  }

  companion object {
    const val NAME = "Tuiplayer"
    @Volatile
    private var isInitialized: Boolean = false

    private const val CLASSES_LIST_PLAY_MODE =
      "com.tencent.qcloud.tuiplayer.shortvideo.common.TUIVideoConst\$ListPlayMode"
    private const val CLASSES_RESOLUTION_TYPE =
      "com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants\$TUIResolutionType"

    private const val ERROR_NO_UI_MANAGER = "E_TUIP_NO_UI_MANAGER"
    private const val ERROR_VIEW_NOT_FOUND = "E_TUIP_VIEW_NOT_FOUND"
    private const val ERROR_INVALID_VIEW_TYPE = "E_TUIP_INVALID_VIEW_TYPE"
    private const val ERROR_UNEXPECTED = "E_TUIP_UNEXPECTED"
  }

  private object NativeLibraryLoader {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
      if (loaded) {
        return
      }
      synchronized(this) {
        if (loaded) {
          return
        }
        try {
          System.loadLibrary("liteavsdk")
        } catch (_: UnsatisfiedLinkError) {
          // LiteAV may already be loaded by the host app.
        }
        System.loadLibrary("tctuiplcore")
        loaded = true
      }
    }
  }

  override fun getShortVideoConstants(): WritableMap {
    return Arguments.createMap().apply {
      putMap("listPlayMode", reflectStaticIntConstants(CLASSES_LIST_PLAY_MODE))
      putMap("resolutionType", reflectStaticIntConstants(CLASSES_RESOLUTION_TYPE))
    }
  }

  override fun getCurrentShortVideoSource(viewTag: Double, promise: Promise) {
    withShortVideoView(
      viewTag,
      promise,
      "Failed to obtain the current short video source."
    ) { shortVideoView ->
      val sourceMap = shortVideoView.getCurrentSourceSnapshot()
      val index = shortVideoView.getCurrentIndex()
      if (sourceMap == null && index == null) {
        null
      } else {
        Arguments.createMap().apply {
          if (index != null) {
            putInt("index", index)
          }
          if (sourceMap != null) {
            putMap("source", sourceMap)
          }
        }
      }
    }
  }

  override fun getShortVideoDataCount(viewTag: Double, promise: Promise) {
    withShortVideoView(
      viewTag,
      promise,
      "Failed to obtain the short video data count."
    ) { shortVideoView ->
      shortVideoView.getDataCount()
    }
  }

  override fun getShortVideoDataByIndex(viewTag: Double, index: Double, promise: Promise) {
    val targetIndex = index.toInt()
    withShortVideoView(
      viewTag,
      promise,
      "Failed to obtain the short video data by index."
    ) { shortVideoView ->
      shortVideoView.getSourceSnapshotAt(targetIndex)
    }
  }

  override fun removeShortVideoData(viewTag: Double, index: Double, promise: Promise) {
    val targetIndex = index.toInt()
    withShortVideoView(
      viewTag,
      promise,
      "Failed to remove short video data."
    ) { shortVideoView ->
      shortVideoView.removeData(targetIndex)
      null
    }
  }

  override fun removeShortVideoRange(viewTag: Double, index: Double, count: Double, promise: Promise) {
    val startIndex = index.toInt()
    val removeCount = count.toInt()
    withShortVideoView(
      viewTag,
      promise,
      "Failed to remove short video data range."
    ) { shortVideoView ->
      shortVideoView.removeRangeData(startIndex, removeCount)
      null
    }
  }

  override fun removeShortVideoDataByIndexes(viewTag: Double, indexes: ReadableArray, promise: Promise) {
    val indexList = indexes.toIntList()
    withShortVideoView(
      viewTag,
      promise,
      "Failed to remove short video data by indexes."
    ) { shortVideoView ->
      shortVideoView.removeDataByIndexes(indexList)
      null
    }
  }

  override fun addShortVideoData(viewTag: Double, source: ReadableMap, index: Double, promise: Promise) {
    val payload = source.toShortVideoSource() ?: return promise.resolve(null)
    withShortVideoView(
      viewTag,
      promise,
      "Failed to add short video data."
    ) { shortVideoView ->
      val targetIndex = if (index.toInt() < 0) shortVideoView.getDataCount() else index.toInt()
      shortVideoView.addData(payload, targetIndex)
      null
    }
  }

  override fun addShortVideoRange(viewTag: Double, sources: ReadableArray, startIndex: Double, promise: Promise) {
    val payloads = sources.toShortVideoSourceList()
    if (payloads.isEmpty()) {
      promise.resolve(null)
      return
    }
    withShortVideoView(
      viewTag,
      promise,
      "Failed to add short video data range."
    ) { shortVideoView ->
      val targetIndex = if (startIndex.toInt() < 0) shortVideoView.getDataCount() else startIndex.toInt()
      shortVideoView.addRangeData(payloads, targetIndex)
      null
    }
  }

  override fun replaceShortVideoData(viewTag: Double, source: ReadableMap, index: Double, promise: Promise) {
    val payload = source.toShortVideoSource() ?: return promise.resolve(null)
    val targetIndex = index.toInt()
    withShortVideoView(
      viewTag,
      promise,
      "Failed to replace short video data."
    ) { shortVideoView ->
      shortVideoView.replaceData(payload, targetIndex)
      null
    }
  }

  override fun replaceShortVideoRange(viewTag: Double, sources: ReadableArray, startIndex: Double, promise: Promise) {
    val payloads = sources.toShortVideoSourceList()
    if (payloads.isEmpty()) {
      promise.resolve(null)
      return
    }
    val targetIndex = startIndex.toInt()
    withShortVideoView(
      viewTag,
      promise,
      "Failed to replace short video data range."
    ) { shortVideoView ->
      shortVideoView.replaceRangeData(payloads, targetIndex)
      null
    }
  }

  override fun callShortVideoVodPlayer(viewTag: Double, command: String, options: ReadableMap?, promise: Promise) {
    withShortVideoView(
      viewTag,
      promise,
      "Failed to execute VOD player command: $command"
    ) { shortVideoView ->
      shortVideoView.handleVodPlayerCommand(command, options)
    }
  }

  private fun withShortVideoView(
    viewTag: Double,
    promise: Promise,
    errorMessage: String,
    block: (TuiplayerShortVideoView) -> Any?
  ) {
    val resolvedTag = viewTag.toInt()
    UiThreadUtil.runOnUiThread {
      val uiManager = UIManagerHelper.getUIManagerForReactTag(reactApplicationContext, resolvedTag)
      if (uiManager == null) {
        promise.reject(
          ERROR_NO_UI_MANAGER,
          "Unable to find UIManager for view tag $resolvedTag."
        )
        return@runOnUiThread
      }
      try {
        val view = uiManager.resolveView(resolvedTag)
        val shortVideoView = view as? TuiplayerShortVideoView
        if (shortVideoView == null) {
          promise.reject(
            ERROR_INVALID_VIEW_TYPE,
            "View for tag $resolvedTag is not TuiplayerShortVideoView."
          )
          return@runOnUiThread
        }
        val result = try {
          block(shortVideoView)
        } catch (error: Throwable) {
          promise.reject(
            ERROR_UNEXPECTED,
            errorMessage,
            error
          )
          return@runOnUiThread
        }
        promise.resolve(result)
      } catch (error: IllegalViewOperationException) {
        promise.reject(
          ERROR_VIEW_NOT_FOUND,
          "No view found for tag $resolvedTag.",
          error
        )
      } catch (error: Throwable) {
        promise.reject(
          ERROR_UNEXPECTED,
          errorMessage,
          error
        )
      }
    }
  }

  private fun reflectStaticIntConstants(className: String): WritableMap {
    val map = Arguments.createMap()
    try {
      val clazz = Class.forName(className)
      clazz.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
        .forEach { field ->
          map.putInt(field.name, field.getInt(null))
        }
    } catch (_: Throwable) {
      // Ignore, return empty map
    }
    return map
  }

}

private fun ReadableMap.getStringOrNull(key: String): String? {
  return if (hasKey(key) && !isNull(key)) getString(key) else null
}

private fun ReadableMap.getBooleanOrNull(key: String): Boolean? {
  return if (hasKey(key) && !isNull(key)) getBoolean(key) else null
}

private fun ReadableMap.getDoubleOrNull(key: String): Double? {
  return if (hasKey(key) && !isNull(key)) getDouble(key) else null
}

private fun ReadableMap.getIntOrNull(key: String): Int? {
  return getDoubleOrNull(key)?.toInt()
}

private fun ReadableMap.getMapOrNull(key: String): ReadableMap? {
  return if (hasKey(key) && !isNull(key)) getMap(key) else null
}

private fun ReadableMap.getArrayOrNull(key: String): ReadableArray? {
  return if (hasKey(key) && !isNull(key)) getArray(key) else null
}

private fun ReadableArray.toIntList(): List<Int> {
  val list = ArrayList<Int>(size())
  for (index in 0 until size()) {
    if (!isNull(index)) {
      list.add(getInt(index))
    }
  }
  return list
}

private fun ReadableArray.toShortVideoSourceList(): List<TuiplayerShortVideoSource> {
  val result = mutableListOf<TuiplayerShortVideoSource>()
  for (index in 0 until size()) {
    val map = getMap(index) ?: continue
    val source = map.toShortVideoSource() ?: continue
    result.add(source)
  }
  return result
}

private fun ReadableMap.toShortVideoSource(): TuiplayerShortVideoSource? {
  val type = when (getStringOrNull("type")?.lowercase()) {
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
  if (type == TuiplayerShortVideoSource.SourceType.FILE_ID && fileId.isNullOrBlank()) {
    return null
  }
  if (type == TuiplayerShortVideoSource.SourceType.URL && url.isNullOrBlank()) {
    return null
  }
  return TuiplayerShortVideoSource(
    type = type,
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

private fun ReadableArray.toSubtitleList(): List<TuiplayerShortVideoSource.Subtitle> {
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
  val showCover = getBooleanOrNull("showCover")
  val playText = getStringOrNull("playText")
  val moreText = getStringOrNull("moreText")
  val watchMoreText = getStringOrNull("watchMoreText")
  val likeCount = getDoubleOrNull("likeCount")?.toLong()
  val favoriteCount = getDoubleOrNull("favoriteCount")?.toLong()
  val isShowPaly = getBooleanOrNull("isShowPaly")
  val isLiked = getBooleanOrNull("isLiked")
  val isBookmarked = getBooleanOrNull("isBookmarked")

  val metadata = TuiplayerShortVideoSource.Metadata(
    name = name,
    icon = icon,
    type = type,
    details = details,
    showCover = showCover,
    playText = playText,
    moreText = moreText ?: watchMoreText,
    watchMoreText = watchMoreText,
    likeCount = likeCount,
    favoriteCount = favoriteCount,
    isShowPaly = isShowPaly,
    isLiked = isLiked,
    isBookmarked = isBookmarked
  )
  return metadata.takeIf { it.hasValue }
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
  val parts = value.split("#")
    .map { it.trim() }
    .filter { it.isNotEmpty() }
  return if (parts.isEmpty()) null else parts
}

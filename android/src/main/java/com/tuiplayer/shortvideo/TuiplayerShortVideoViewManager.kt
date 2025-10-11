package com.tuiplayer.shortvideo

import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.module.annotations.ReactModule
import com.facebook.react.uimanager.SimpleViewManager
import com.facebook.react.uimanager.ThemedReactContext
import com.facebook.react.uimanager.ViewManagerDelegate
import com.facebook.react.viewmanagers.TuiplayerShortVideoViewManagerDelegate
import com.facebook.react.viewmanagers.TuiplayerShortVideoViewManagerInterface

@ReactModule(name = TuiplayerShortVideoViewManager.NAME)
internal class TuiplayerShortVideoViewManager :
  SimpleViewManager<TuiplayerShortVideoView>(),
  TuiplayerShortVideoViewManagerInterface<TuiplayerShortVideoView> {

  companion object {
    const val NAME = "TuiplayerShortVideoView"
  }

  private val delegate: ViewManagerDelegate<TuiplayerShortVideoView> =
    TuiplayerShortVideoViewManagerDelegate(this)

  override fun getDelegate(): ViewManagerDelegate<TuiplayerShortVideoView> {
    return delegate
  }

  override fun getName(): String = NAME

  override fun createViewInstance(context: ThemedReactContext): TuiplayerShortVideoView {
    return TuiplayerShortVideoView(context)
  }

  override fun setSources(view: TuiplayerShortVideoView, value: ReadableArray?) {
    view.setSources(parseSources(value))
  }

  override fun setAutoPlay(view: TuiplayerShortVideoView, value: Boolean) {
    view.setAutoPlay(value)
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

  private fun ReadableMap.toSource(): TuiplayerShortVideoSource {
    val typeString = if (hasKey("type") && !isNull("type")) getString("type") else null
    val sourceType = when (typeString?.lowercase()) {
      "url" -> TuiplayerShortVideoSource.SourceType.URL
      else -> TuiplayerShortVideoSource.SourceType.FILE_ID
    }

    val appId = if (hasKey("appId") && !isNull("appId")) getDouble("appId").toInt() else null
    val fileId = if (hasKey("fileId") && !isNull("fileId")) getString("fileId") else null
    val url = if (hasKey("url") && !isNull("url")) getString("url") else null
    val cover = if (hasKey("coverPictureUrl") && !isNull("coverPictureUrl")) getString("coverPictureUrl") else null
    val pSign = if (hasKey("pSign") && !isNull("pSign")) getString("pSign") else null

    return TuiplayerShortVideoSource(
      type = sourceType,
      appId = appId,
      fileId = fileId,
      url = url,
      coverPictureUrl = cover,
      pSign = pSign
    )
  }
}

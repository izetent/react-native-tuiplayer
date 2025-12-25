package com.txplayer

import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReadableArray
import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.Arguments
import com.facebook.react.module.annotations.ReactModule
import com.txplayer.rnuiplayer.common.TxplayerEventDispatcher
import com.txplayer.rnuiplayer.player.RNShortController
import com.txplayer.rnuiplayer.player.RNShortEngine
import com.txplayer.rnuiplayer.tools.RNTransformer
import com.txplayer.rnuiplayer.view.RNShortVideoItemView
import com.txplayer.rnuiplayer.view.RNViewRegistry
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlayerBitrateItem

@ReactModule(name = TxplayerModule.NAME)
class TxplayerModule(reactContext: ReactApplicationContext) :
  NativeTxplayerSpec(reactContext) {

  private val shortEngine = RNShortEngine(reactContext)

  init {
    TxplayerEventDispatcher.init(reactContext)
  }

  override fun getName(): String = NAME

  override fun setPlayerConfig(config: ReadableMap, promise: Promise) {
    try {
      shortEngine.setConfig(config)
      promise.resolve(null)
    } catch (error: Throwable) {
      promise.reject("E_PLAYER_CONFIG", error)
    }
  }

  override fun setMonetAppInfo(appId: Double, authId: Double, srAlgorithmType: Double, promise: Promise) {
    try {
      shortEngine.setMonetAppInfo(appId.toLong(), authId.toInt(), srAlgorithmType.toInt())
      promise.resolve(null)
    } catch (error: Throwable) {
      promise.reject("E_MONET", error)
    }
  }

  override fun createShortController(promise: Promise) {
    try {
      val controllerId = shortEngine.createShortController()
      promise.resolve(controllerId.toDouble())
    } catch (error: Throwable) {
      promise.reject("E_CREATE_CONTROLLER", error)
    }
  }

  override fun shortControllerSetModels(controllerId: Double, sources: ReadableArray, promise: Promise) {
    withController(controllerId, promise) { controller ->
      val result = controller.setModels(RNTransformer.transformVideoSources(sources))
      promise.resolve(result.toDouble())
    }
  }

  override fun shortControllerAppendModels(controllerId: Double, sources: ReadableArray, promise: Promise) {
    withController(controllerId, promise) { controller ->
      val result = controller.appendModels(RNTransformer.transformVideoSources(sources))
      promise.resolve(result.toDouble())
    }
  }

  override fun shortControllerBindVideoView(controllerId: Double, viewTag: Double, index: Double, promise: Promise) {
    withController(controllerId, promise) { controller ->
      controller.bindVideoView(viewTag.toInt(), index.toInt())
      promise.resolve(null)
    }
  }

  override fun shortControllerPreBindVideo(controllerId: Double, viewTag: Double, index: Double, promise: Promise) {
    withController(controllerId, promise) { controller ->
      controller.preBindVideo(viewTag.toInt(), index.toInt())
      promise.resolve(null)
    }
  }

  override fun shortControllerSetVodStrategy(controllerId: Double, strategy: ReadableMap, promise: Promise) {
    withController(controllerId, promise) { controller ->
      controller.setVodStrategy(RNTransformer.transformVodStrategy(strategy))
      promise.resolve(null)
    }
  }

  override fun shortControllerStartCurrent(controllerId: Double, promise: Promise) {
    withController(controllerId, promise) { controller ->
      promise.resolve(controller.startCurrent().toDouble())
    }
  }

  override fun shortControllerSetVideoLoop(controllerId: Double, isLoop: Boolean, promise: Promise) {
    withController(controllerId, promise) { controller ->
      controller.setVideoLoop(isLoop)
      promise.resolve(null)
    }
  }

  override fun shortControllerSwitchResolution(controllerId: Double, resolution: Double, switchType: Double, promise: Promise) {
    withController(controllerId, promise) { controller ->
      controller.switchResolution(resolution.toLong(), switchType.toInt())
      promise.resolve(null)
    }
  }

  override fun shortControllerRelease(controllerId: Double, promise: Promise) {
    withController(controllerId, promise) { controller ->
      controller.release()
      promise.resolve(null)
    }
  }

  override fun vodPlayerStartPlay(viewTag: Double, source: ReadableMap, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      val videoSource = RNTransformer.transformVideoSource(source)
      view.vodController.startPlay(videoSource)
      promise.resolve(null)
    }
  }

  override fun vodPlayerPause(viewTag: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.pause()
      promise.resolve(null)
    }
  }

  override fun vodPlayerResume(viewTag: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.resume()
      promise.resolve(null)
    }
  }

  override fun vodPlayerSetRate(viewTag: Double, rate: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.setRate(rate)
      promise.resolve(null)
    }
  }

  override fun vodPlayerSetMute(viewTag: Double, mute: Boolean, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.setMute(mute)
      promise.resolve(null)
    }
  }

  override fun vodPlayerSeekTo(viewTag: Double, time: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.seekTo(time)
      promise.resolve(null)
    }
  }

  override fun vodPlayerSwitchResolution(viewTag: Double, resolution: Double, switchType: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.switchResolution(resolution.toLong(), switchType.toInt())
      promise.resolve(null)
    }
  }

  override fun vodPlayerGetSupportResolution(viewTag: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      val result = Arguments.createArray()
      val items: List<TUIPlayerBitrateItem> = view.vodController.getSupportResolution()
      items.forEach { item ->
        val map = Arguments.createMap()
        map.putInt("index", item.index)
        map.putInt("width", item.width)
        map.putInt("height", item.height)
        map.putInt("bitrate", item.bitrate)
        result.pushMap(map)
      }
      promise.resolve(result)
    }
  }

  override fun vodPlayerSetMirror(viewTag: Double, mirror: Boolean, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.setMirror(mirror)
      promise.resolve(null)
    }
  }

  override fun vodPlayerSetStringOption(viewTag: Double, value: String, key: ReadableMap, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.setStringOption(value, normalizeDynamic(key))
      promise.resolve(null)
    }
  }

  override fun vodPlayerSelectSubtitle(viewTag: Double, trackIndex: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.selectSubtitleTrack(trackIndex.toInt())
      promise.resolve(null)
    }
  }

  override fun vodPlayerGetDuration(viewTag: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      promise.resolve(view.vodController.duration)
    }
  }

  override fun vodPlayerGetCurrentPlayTime(viewTag: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      promise.resolve(view.vodController.currentPlayTime)
    }
  }

  override fun vodPlayerIsPlaying(viewTag: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      promise.resolve(view.vodController.isPlaying)
    }
  }

  override fun vodPlayerRelease(viewTag: Double, promise: Promise) {
    withPlayer(viewTag, promise) { view ->
      view.vodController.release()
      promise.resolve(null)
    }
  }

  override fun addListener(eventName: String) {}

  override fun removeListeners(count: Double) {}

  private fun withController(id: Double, promise: Promise, block: (RNShortController) -> Unit) {
    val controller = shortEngine.getController(id.toInt())
    if (controller == null) {
      promise.reject("E_NO_CONTROLLER", "Controller $id not found")
    } else {
      block(controller)
    }
  }

  private fun withPlayer(viewTag: Double, promise: Promise, block: (RNShortVideoItemView) -> Unit) {
    val view = RNViewRegistry.get(viewTag.toInt())
    if (view == null) {
      promise.reject("E_NO_VIEW", "View $viewTag not found")
    } else {
      block(view)
    }
  }

  private fun normalizeDynamic(value: Any?): Any? {
    return when (value) {
      is ReadableMap -> value.toHashMap()
      is ReadableArray -> value.toArrayList()
      else -> value
    }
  }

  companion object {
    const val NAME = "Txplayer"
  }
}

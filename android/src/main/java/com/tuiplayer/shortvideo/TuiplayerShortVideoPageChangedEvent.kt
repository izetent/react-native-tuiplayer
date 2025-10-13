package com.tuiplayer.shortvideo

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter

@Suppress("DEPRECATION")
internal class TuiplayerShortVideoPageChangedEvent(
  surfaceId: Int,
  viewId: Int,
  private val index: Int,
  private val total: Int
) : Event<TuiplayerShortVideoPageChangedEvent>(surfaceId, viewId) {

  override fun getEventName(): String = EVENT_NAME

  override fun getEventData(): WritableMap {
    return Arguments.createMap().apply {
      putInt("index", index)
      putInt("total", total)
    }
  }

  @Deprecated("Dispatch via RCTModernEventEmitter")
  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, EVENT_NAME, getEventData())
  }

  companion object {
    const val EVENT_NAME = "topPageChanged"
  }
}

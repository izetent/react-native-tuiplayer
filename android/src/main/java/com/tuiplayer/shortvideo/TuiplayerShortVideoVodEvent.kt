package com.tuiplayer.shortvideo

import com.facebook.react.bridge.Arguments
import com.facebook.react.bridge.WritableMap
import com.facebook.react.uimanager.events.Event
import com.facebook.react.uimanager.events.RCTEventEmitter

@Suppress("DEPRECATION")
internal class TuiplayerShortVideoVodEvent(
  surfaceId: Int,
  viewId: Int,
  private val type: String,
  private val payload: WritableMap?
) : Event<TuiplayerShortVideoVodEvent>(surfaceId, viewId) {

  override fun getEventName(): String = EVENT_NAME

  override fun getEventData(): WritableMap {
    return Arguments.createMap().apply {
      putString("type", type)
      if (payload != null) {
        putMap("payload", payload)
      }
    }
  }

  @Deprecated("Dispatch via RCTModernEventEmitter")
  override fun dispatch(rctEventEmitter: RCTEventEmitter) {
    rctEventEmitter.receiveEvent(viewTag, EVENT_NAME, getEventData())
  }

  companion object {
    const val EVENT_NAME = "topVodEvent"
  }
}

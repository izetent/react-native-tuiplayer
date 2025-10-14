package com.tuiplayer.shortvideo.layer

import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource
import com.tuiplayer.shortvideo.TuiplayerShortVideoSource

internal interface TuiplayerLayerHost {
  fun resolveSource(model: TUIVideoSource): TuiplayerShortVideoSource?
  fun resolveIndex(model: TUIVideoSource): Int?
  fun emitOverlayAction(model: TUIVideoSource, action: String)
}

internal interface TuiplayerHostAwareLayer {
  fun attachHost(host: TuiplayerLayerHost)
  fun onPlaybackStateChanged(paused: Boolean) {}
}

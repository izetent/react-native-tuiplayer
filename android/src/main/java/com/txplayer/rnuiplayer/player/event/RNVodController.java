package com.txplayer.rnuiplayer.player.event;

import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableArray;
import com.facebook.react.bridge.WritableMap;
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerController;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIFileVideoInfo;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource;
import com.tencent.qcloud.tuiplayer.core.api.ui.player.ITUIVodPlayer;
import com.tencent.qcloud.tuiplayer.core.api.ui.player.TUIVodObserver;
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIBaseVideoView;
import com.tencent.qcloud.tuiplayer.core.api.ui.view.vod.TUIVodViewListener;
import com.tencent.qcloud.tuiplayer.core.tools.TUIPlayerLog;
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoItemView;
import com.txplayer.rnuiplayer.common.RNConstant;
import com.txplayer.rnuiplayer.common.TxplayerEventDispatcher;
import com.txplayer.rnuiplayer.tools.RNUtils;
import com.txplayer.rnuiplayer.view.RNShortVideoItemView;
import com.tencent.rtmp.TXTrackInfo;
import com.tencent.rtmp.ui.TXSubtitleView;

import java.util.List;
import java.util.Map;

public class RNVodController
    implements TUIVodViewListener, TUIVodObserver {

  private static final String TAG = "RNVodController";

  private final ReactContext reactContext;
  private final RNShortVideoItemView parentView;
  private TUIPlayerController controller;
  private TUIVideoSource curSource;

  public RNVodController(ReactContext context, RNShortVideoItemView parentView) {
    this.reactContext = context;
    this.parentView = parentView;
  }

  private int getViewTag() {
    return parentView.getId();
  }

  private void emitEvent(String eventName, @Nullable Map<String, Object> payload) {
    WritableMap params = TxplayerEventDispatcher.createParams();
    params.putInt("viewTag", getViewTag());
    if (payload != null) {
      params.putMap("event", Arguments.makeNativeMap(payload));
    }
    TxplayerEventDispatcher.emit(eventName, params);
  }

  @Override
  public void onControllerPlayerAttached(TUIPlayerController tuiPlayerController) {}

  @Override
  public void onPlayerControllerBind(TUIPlayerController playerController) {
    controller = playerController;
    controller.addPlayerObserver(this);
    emitEvent(RNConstant.EVENT_CONTROLLER_BIND, null);
  }

  @Override
  public void onPlayerControllerUnBind(TUIPlayerController playerController) {
    controller = null;
    parentView.hideSubtitleLayer();
    emitEvent(RNConstant.EVENT_CONTROLLER_UNBIND, null);
  }

  @Override
  public void onBindData(TUIVideoSource tuiVideoSource) {
    curSource = tuiVideoSource;
  }

  @Override
  public void onExtInfoChanged(TUIVideoSource tuiVideoSource) {
    curSource = tuiVideoSource;
  }

  @Override
  public void onViewRecycled(TUIBaseVideoView tuiBaseVideoView) {}

  @Override
  public void onShortVideoDestroyed() {
    onShortVideoDestroyedInternal();
  }

  public void onShortVideoDestroyedInternal() {
    if (controller != null) {
      controller.stop();
    }
  }

  public void startPlay(@NonNull TUIVideoSource source) {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      ITUIVodPlayer player = (ITUIVodPlayer) controller.getPlayer();
      player.startPlay(source);
    }
  }

  public void pause() {
    if (controller != null) {
      controller.pause();
    }
  }

  public void resume() {
    if (controller != null) {
      controller.resume();
    }
  }

  public void setRate(double rate) {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      ((ITUIVodPlayer) controller.getPlayer()).setRate((float) rate);
    }
  }

  public void setMute(boolean mute) {
    if (controller != null && controller.getPlayer() != null) {
      controller.getPlayer().setMute(mute);
    }
  }

  public void seekTo(double time) {
    if (controller != null) {
      controller.seekTo((float) time);
    }
  }

  public void setStringOption(String value, @Nullable Object key) {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      ((ITUIVodPlayer) controller.getPlayer()).setStringOption(value, key);
    }
  }

  public double getDuration() {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      return ((ITUIVodPlayer) controller.getPlayer()).getDuration();
    }
    return 0;
  }

  public double getCurrentPlayTime() {
    if (controller != null && controller.getPlayer() instanceof ITUIVodPlayer) {
      return ((ITUIVodPlayer) controller.getPlayer()).getCurrentPlayTime();
    }
    return 0;
  }

  public boolean isPlaying() {
    return controller != null && controller.isPlaying();
  }

  public void release() {
    onShortVideoDestroyedInternal();
    if (curSource != null) {
      curSource.attachView(null);
    }
  }

  @Override
  public void onPlayEvent(ITUIVodPlayer player, int event, Bundle bundle) {
    Map<String, Object> payload = RNUtils.getParams(event, bundle);
    emitEvent(RNConstant.EVENT_PLAY_EVENT, payload);
  }

  @Override
  public void onPlayPrepare() {}

  @Override
  public void onPlayBegin() {}

  @Override
  public void onPlayLoading() {}

  @Override
  public void onPlayLoadingEnd() {}

  @Override
  public void onPlayProgress(long l, long l1, long l2) {}

  @Override
  public void onSeek(float v) {}

  @Override
  public void onError(int i, String s, Bundle bundle) {
    Map<String, Object> payload = RNUtils.getParams(i, bundle);
    emitEvent(RNConstant.EVENT_PLAY_EVENT, payload);
  }

  @Override
  public void onRcvFirstIframe() {}

  @Override
  public void onRcvAudioTrackInformation(List<TXTrackInfo> list) {}

  @Override
  public void onRcvTrackInformation(List<TXTrackInfo> list) {}

  @Override
  public void onRcvSubTitleTrackInformation(List<TXTrackInfo> list) {
    if (controller == null || controller.getPlayer() == null || list == null || list.isEmpty()) {
      parentView.hideSubtitleLayer();
      return;
    }
    if (controller.getPlayer() instanceof ITUIVodPlayer) {
      ITUIVodPlayer player = (ITUIVodPlayer) controller.getPlayer();
      TXSubtitleView subtitleView = parentView.getSubtitleView();
      player.setSubtitleView(subtitleView);
      player.selectTrack(list.get(0).trackIndex);
      parentView.showSubtitleLayer();
      emitSubtitleTracks(list);
    }
  }

  @Override
  public void onRecFileVideoInfo(TUIFileVideoInfo tuiFileVideoInfo) {}

  @Override
  public void onResolutionChanged(long l, long l1) {}

  @Override
  public void onFirstFrameRendered() {
    emitEvent(
        RNConstant.EVENT_PLAY_EVENT,
        RNUtils.getParams(RNConstant.PLAY_EVT_FIRST_FRAME_RENDERED, new Bundle()));
  }

  @Override
  public void onPlayEnd() {}

  @Override
  public void onRetryConnect(int i, Bundle bundle) {}

  @Override
  public void onPlayPause() {}

  @Override
  public void onPlayStop() {}

  public void selectSubtitleTrack(int trackIndex) {
    if (controller == null || controller.getPlayer() == null) {
      return;
    }
    if (!(controller.getPlayer() instanceof ITUIVodPlayer)) {
      return;
    }
    ITUIVodPlayer player = (ITUIVodPlayer) controller.getPlayer();
    if (trackIndex < 0) {
      player.setSubtitleView(null);
      parentView.hideSubtitleLayer();
    } else {
      TXSubtitleView subtitleView = parentView.getSubtitleView();
      player.setSubtitleView(subtitleView);
      player.selectTrack(trackIndex);
      parentView.showSubtitleLayer();
    }
  }

  private void emitSubtitleTracks(List<TXTrackInfo> tracks) {
    WritableMap params = TxplayerEventDispatcher.createParams();
    params.putInt("viewTag", getViewTag());
    WritableArray trackArray = Arguments.createArray();
    for (TXTrackInfo info : tracks) {
      WritableMap trackMap = Arguments.createMap();
      trackMap.putInt("trackIndex", info.trackIndex);
      if (info.language != null) {
        trackMap.putString("language", info.language);
      }
      String trackName = info.getName();
      if (trackName != null) {
        trackMap.putString("name", trackName);
      }
      trackMap.putInt("type", info.trackType);
      trackArray.pushMap(trackMap);
    }
    params.putArray("tracks", trackArray);
    TxplayerEventDispatcher.emit(RNConstant.EVENT_SUBTITLE_TRACKS, params);
  }
}

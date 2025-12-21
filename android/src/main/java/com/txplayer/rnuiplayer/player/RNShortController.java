package com.txplayer.rnuiplayer.player;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.facebook.react.bridge.UiThreadUtil;
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerBridge;
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerManager;
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerVodStrategy;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIPlaySource;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource;
import com.tencent.qcloud.tuiplayer.core.api.tools.TUIDataUtils;
import com.tencent.qcloud.tuiplayer.core.api.ui.player.ITUIVodPlayer;
import com.tencent.qcloud.tuiplayer.core.api.ui.view.TUIBaseVideoView;
import com.tencent.qcloud.tuiplayer.core.preload.TUIVideoDataHolder;
import com.tencent.qcloud.tuiplayer.core.tools.TUIPlayerLog;
import com.txplayer.rnuiplayer.view.RNShortVideoItemView;
import com.txplayer.rnuiplayer.view.RNViewRegistry;

import java.util.List;

public class RNShortController implements TUIPlayerBridge {

  private static final String TAG = "RNShortController";

  private final TUIPlayerManager manager;
  private final TUIVideoDataHolder dataHolder;
  private final RNShortEngineObserver engineObserver;
  private final int controllerId;
  private int currentIndex;
  private boolean isLoop = true;

  public RNShortController(Context context, int controllerId, RNShortEngineObserver observer) {
    this.controllerId = controllerId;
    this.engineObserver = observer;
    this.manager = new TUIPlayerManager(context, this);
    this.dataHolder = manager.getDataHolder();
  }

  public long setModels(List<TUIVideoSource> sources) {
    List<TUIPlaySource> copy = TUIDataUtils.copyModels(sources);
    return manager.setModels(copy);
  }

  public long appendModels(List<TUIVideoSource> sources) {
    List<TUIPlaySource> copy = TUIDataUtils.copyModels(sources);
    return manager.appendModels(copy);
  }

  public long startCurrent() {
    return manager.startCurrent();
  }

  public void setVodStrategy(@Nullable TUIPlayerVodStrategy strategy) {
    if (strategy != null) {
      manager.updateVodStrategy(strategy);
    }
  }

  public void setVideoLoop(boolean loop) {
    this.isLoop = loop;
  }

  public void release() {
    TUIPlayerLog.i(TAG, "release controller " + controllerId);
    manager.releasePlayers();
    engineObserver.onRelease(controllerId);
  }

  public void bindVideoView(int viewTag, int index) {
    bindVideoViewInternal(viewTag, index, false);
  }

  public void preBindVideo(int viewTag, int index) {
    bindVideoViewInternal(viewTag, index, true);
  }

  private void bindVideoViewInternal(int viewTag, int index, boolean isPreBind) {
    UiThreadUtil.runOnUiThread(
        () -> {
          RNShortVideoItemView itemView = RNViewRegistry.get(viewTag);
          if (itemView == null) {
            TUIPlayerLog.e(TAG, "bindVideoView met a null view, viewTag:" + viewTag);
            return;
          }
          final int count = dataHolder.size();
          if (index >= count || index < 0) {
            TUIPlayerLog.e(TAG, "bindVideoView failed, index outOfRange,index:" + index);
            return;
          }
          TUIBaseVideoView videoView = (TUIBaseVideoView) itemView.getVideoItemView();
          videoView.bindVideoModel(dataHolder.getSource(index));
          if (isPreBind) {
            if (Math.abs(index - currentIndex) > 2) {
              TUIPlayerLog.w(
                  TAG,
                  "skip preRender index:" + index + ", diff from current:" + currentIndex);
            } else {
              manager.preRenderOnView(videoView);
            }
          } else {
            currentIndex = index;
            manager.bindVideoView(videoView);
            handlePlayerLoopMode(videoView);
          }
        });
  }

  private void handlePlayerLoopMode(@Nullable TUIBaseVideoView itemView) {
    if (itemView != null && itemView.getController() != null) {
      itemView.getController().setLoop(isLoop);
    }
  }

  @Override
  public int getCurrentScrollState() {
    return 0;
  }

  @Override
  public int getCurrentPlayingIndex() {
    return currentIndex;
  }

  @Override
  public void onCurrentPlayEnd() {}

  @Override
  public void postHandlePlayCurrent(int i) {}

  @Override
  public void postOnMain(Runnable runnable) {
    UiThreadUtil.runOnUiThread(runnable);
  }

  @Override
  public void onVodPlayerReady(ITUIVodPlayer ituiVodPlayer, TUIVideoSource tuiVideoSource) {}

  @Override
  public void changeCurPos(int i) {}
}

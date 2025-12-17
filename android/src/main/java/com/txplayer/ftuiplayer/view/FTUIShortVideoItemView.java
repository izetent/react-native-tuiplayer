package com.txplayer.ftuiplayer.view;

import android.content.Context;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;

import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.WritableMap;
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants;
import com.tencent.qcloud.tuiplayer.shortvideo.ui.view.TUIShortVideoItemView;
import com.txplayer.ftuiplayer.common.FTUIConstant;
import com.txplayer.ftuiplayer.common.TxplayerEventDispatcher;
import com.txplayer.ftuiplayer.player.event.FTUIVodController;

public class FTUIShortVideoItemView extends FrameLayout {

  private final TUIShortVideoItemView itemView;
  private final FTUIVodController vodController;

  public FTUIShortVideoItemView(@NonNull Context context) {
    super(context);
    ReactContext reactContext = (ReactContext) context;
    itemView =
        new TUIShortVideoItemView(context, TUIConstants.RenderViewType.TEXTURE_VIEW);
    itemView.setClickable(false);
    itemView.setFocusableInTouchMode(false);
    itemView.setLongClickable(false);
    addView(itemView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    vodController = new FTUIVodController(reactContext, this);
    itemView.addVideoItemViewListener(vodController);
    itemView.createDisplayView();
  }

  public TUIShortVideoItemView getVideoItemView() {
    return itemView;
  }

  public FTUIVodController getVodController() {
    return vodController;
  }

  public void dispose() {
    vodController.onShortVideoDestroyed();
    itemView.onViewDestroyed();
    WritableMap params = TxplayerEventDispatcher.createParams();
    params.putInt("viewTag", getId());
    FTUIViewRegistry.unregister(getId());
    TxplayerEventDispatcher.emit(FTUIConstant.EVENT_VIEW_DISPOSED, params);
  }

  @Override
  public void setId(int id) {
    int oldId = getId();
    super.setId(id);
    if (oldId != id) {
      FTUIViewRegistry.unregister(oldId);
      FTUIViewRegistry.register(id, this);
    }
  }
}

package com.txplayer.ftuiplayer.view;

import androidx.annotation.NonNull;

import com.facebook.react.uimanager.SimpleViewManager;
import com.facebook.react.uimanager.ThemedReactContext;
import com.txplayer.ftuiplayer.common.FTUIConstant;

public class FTUIPlayerViewManager extends SimpleViewManager<FTUIShortVideoItemView> {

  @NonNull
  @Override
  public String getName() {
    return FTUIConstant.VIEW_TYPE;
  }

  @NonNull
  @Override
  protected FTUIShortVideoItemView createViewInstance(@NonNull ThemedReactContext context) {
    return new FTUIShortVideoItemView(context);
  }

  @Override
  public void onDropViewInstance(@NonNull FTUIShortVideoItemView view) {
    view.dispose();
    super.onDropViewInstance(view);
  }
}

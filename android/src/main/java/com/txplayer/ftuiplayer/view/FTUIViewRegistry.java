package com.txplayer.ftuiplayer.view;

import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class FTUIViewRegistry {

  private static final ConcurrentMap<Integer, FTUIShortVideoItemView> VIEW_MAP =
      new ConcurrentHashMap<>();

  private FTUIViewRegistry() {}

  public static void register(int id, FTUIShortVideoItemView view) {
    if (id != android.view.View.NO_ID) {
      VIEW_MAP.put(id, view);
    }
  }

  public static void unregister(int id) {
    if (id != android.view.View.NO_ID) {
      VIEW_MAP.remove(id);
    }
  }

  @Nullable
  public static FTUIShortVideoItemView get(int id) {
    return VIEW_MAP.get(id);
  }
}

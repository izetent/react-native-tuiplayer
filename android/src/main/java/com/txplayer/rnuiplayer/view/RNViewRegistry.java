package com.txplayer.rnuiplayer.view;

import androidx.annotation.Nullable;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class RNViewRegistry {

  private static final ConcurrentMap<Integer, RNShortVideoItemView> VIEW_MAP =
      new ConcurrentHashMap<>();

  private RNViewRegistry() {}

  public static void register(int id, RNShortVideoItemView view) {
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
  public static RNShortVideoItemView get(int id) {
    return VIEW_MAP.get(id);
  }
}

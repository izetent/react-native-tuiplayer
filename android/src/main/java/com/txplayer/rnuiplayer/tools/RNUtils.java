package com.txplayer.rnuiplayer.tools;

import android.os.Bundle;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public final class RNUtils {

  private RNUtils() {}

  public static Map<String, Object> getParams(int event, Bundle bundle) {
    Map<String, Object> param = new HashMap<>();
    if (event != 0) {
      param.put("event", event);
    }
    if (bundle != null && !bundle.isEmpty()) {
      Set<String> keySet = bundle.keySet();
      for (String key : keySet) {
        Object val = bundle.get(key);
        param.put(key, val);
      }
    }
    return param;
  }
}

package com.txplayer.rnuiplayer.tools;

import androidx.annotation.Nullable;

import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableType;
import com.tencent.qcloud.tuiplayer.core.TUIPlayerConfig;
import com.tencent.qcloud.tuiplayer.core.api.TUIPlayerVodStrategy;
import com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants;
import com.tencent.qcloud.tuiplayer.core.api.model.TUIVideoSource;

import java.util.ArrayList;
import java.util.List;

public final class RNTransformer {

  private RNTransformer() {}

  public static TUIPlayerConfig transformToConfig(ReadableMap map) {
    boolean enableLog = !map.hasKey("enableLog") || map.getBoolean("enableLog");
    String licenseUrl = map.hasKey("licenseUrl") ? map.getString("licenseUrl") : "";
    String licenseKey = map.hasKey("licenseKey") ? map.getString("licenseKey") : "";
    return new TUIPlayerConfig.Builder()
        .enableLog(enableLog)
        .licenseUrl(licenseUrl)
        .licenseKey(licenseKey)
        .build();
  }

  public static List<TUIVideoSource> transformVideoSources(ReadableArray array) {
    List<TUIVideoSource> sourceList = new ArrayList<>();
    for (int i = 0; i < array.size(); i++) {
      ReadableMap item = array.getMap(i);
      if (item != null) {
        sourceList.add(transformVideoSource(item));
      }
    }
    return sourceList;
  }

  public static TUIVideoSource transformVideoSource(ReadableMap map) {
    TUIVideoSource source = new TUIVideoSource();
    if (map.hasKey("videoURL") && map.getType("videoURL") != ReadableType.Null) {
      source.setVideoURL(map.getString("videoURL"));
    }
    if (map.hasKey("coverPictureUrl") && map.getType("coverPictureUrl") != ReadableType.Null) {
      source.setCoverPictureUrl(map.getString("coverPictureUrl"));
    }
    if (map.hasKey("appId") && map.getType("appId") != ReadableType.Null) {
      source.setAppId(map.getInt("appId"));
    }
    if (map.hasKey("fileId") && map.getType("fileId") != ReadableType.Null) {
      source.setFileId(map.getString("fileId"));
    }
    if (map.hasKey("pSign") && map.getType("pSign") != ReadableType.Null) {
      source.setPSign(map.getString("pSign"));
    }
    if (map.hasKey("isAutoPlay") && map.getType("isAutoPlay") != ReadableType.Null) {
      source.setAutoPlay(map.getBoolean("isAutoPlay"));
    } else {
      source.setAutoPlay(true);
    }
    if (map.hasKey("extInfo") && map.getType("extInfo") != ReadableType.Null) {
      ReadableMap ext = map.getMap("extInfo");
      if (ext != null) {
        source.setExtInfoAndNotify(ext.toHashMap());
      }
    }
    return source;
  }

  public static TUIPlayerVodStrategy transformVodStrategy(@Nullable ReadableMap map) {
    TUIPlayerVodStrategy.Builder builder = new TUIPlayerVodStrategy.Builder();
    if (map != null) {
      if (map.hasKey("preloadCount") && map.getType("preloadCount") != ReadableType.Null) {
        builder.setPreloadCount(map.getInt("preloadCount"));
      }
      if (map.hasKey("preDownloadSize") && map.getType("preDownloadSize") != ReadableType.Null) {
        builder.setPreDownloadSize((float) map.getDouble("preDownloadSize"));
      }
      if (map.hasKey("preloadBufferSizeInMB") && map.getType("preloadBufferSizeInMB") != ReadableType.Null) {
        builder.setPreLoadBufferSize((float) map.getDouble("preloadBufferSizeInMB"));
      }
      if (map.hasKey("maxBufferSize") && map.getType("maxBufferSize") != ReadableType.Null) {
        builder.setMaxBufferSize((float) map.getDouble("maxBufferSize"));
      }
      if (map.hasKey("preferredResolution") && map.getType("preferredResolution") != ReadableType.Null) {
        builder.setPreferredResolution(map.getDouble("preferredResolution"));
      }
      if (map.hasKey("progressInterval") && map.getType("progressInterval") != ReadableType.Null) {
        builder.setProgressInterval(map.getInt("progressInterval"));
      }
      if (map.hasKey("renderMode") && map.getType("renderMode") != ReadableType.Null) {
        builder.setRenderMode(map.getInt("renderMode"));
      }
      if (map.hasKey("enableSuperResolution") && map.getType("enableSuperResolution") != ReadableType.Null) {
        boolean enableSR = map.getBoolean("enableSuperResolution");
        builder.setSuperResolutionMode(
            enableSR
                ? TUIConstants.TUISuperResolution.SUPER_RESOLUTION_ASR
                : TUIConstants.TUISuperResolution.SUPER_RESOLUTION_NONE);
      }
    }
    builder.setPrePlayStrategy(TUIConstants.TUIPrePlayStrategy.TUIPrePlayStrategyAdjacent);
    return builder.build();
  }
}

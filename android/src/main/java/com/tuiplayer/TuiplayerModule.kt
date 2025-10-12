package com.tuiplayer

import com.facebook.react.bridge.ReadableMap
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.UiThreadUtil
import com.facebook.react.bridge.WritableMap
import com.facebook.react.module.annotations.ReactModule
import com.tencent.qcloud.tuiplayer.core.TUIPlayerConfig
import com.tencent.qcloud.tuiplayer.core.TUIPlayerCore
import com.facebook.react.bridge.Arguments
import java.lang.reflect.Modifier

@ReactModule(name = TuiplayerModule.NAME)
class TuiplayerModule(reactContext: ReactApplicationContext) :
  NativeTuiplayerSpec(reactContext) {

  init {
    NativeLibraryLoader.ensureLoaded()
  }

  override fun getName(): String {
    return NAME
  }

  override fun initialize(config: ReadableMap?) {
    val enableLog = config?.getBooleanOrNull("enableLog") ?: false
    val licenseUrl = config?.getStringOrNull("licenseUrl")
    val licenseKey = config?.getStringOrNull("licenseKey")

    if (isInitialized && licenseUrl.isNullOrBlank() && licenseKey.isNullOrBlank()) {
      // Already initialised with previous config; nothing to do when no license provided.
      return
    }

    UiThreadUtil.runOnUiThread {
      val builder = TUIPlayerConfig.Builder().enableLog(enableLog)
      if (!licenseUrl.isNullOrBlank() && !licenseKey.isNullOrBlank()) {
        builder.licenseUrl(licenseUrl)
        builder.licenseKey(licenseKey)
      }
      TUIPlayerCore.init(reactApplicationContext, builder.build())
      isInitialized = true
    }
  }

  companion object {
    const val NAME = "Tuiplayer"
    @Volatile
    private var isInitialized: Boolean = false

    private const val CLASSES_LIST_PLAY_MODE =
      "com.tencent.qcloud.tuiplayer.shortvideo.common.TUIVideoConst\$ListPlayMode"
    private const val CLASSES_RESOLUTION_TYPE =
      "com.tencent.qcloud.tuiplayer.core.api.common.TUIConstants\$TUIResolutionType"
  }

  private object NativeLibraryLoader {
    @Volatile
    private var loaded = false

    fun ensureLoaded() {
      if (loaded) {
        return
      }
      synchronized(this) {
        if (loaded) {
          return
        }
        try {
          System.loadLibrary("liteavsdk")
        } catch (_: UnsatisfiedLinkError) {
          // LiteAV may already be loaded by the host app.
        }
        System.loadLibrary("tctuiplcore")
        loaded = true
      }
    }
  }

  override fun getShortVideoConstants(): WritableMap {
    return Arguments.createMap().apply {
      putMap("listPlayMode", reflectStaticIntConstants(CLASSES_LIST_PLAY_MODE))
      putMap("resolutionType", reflectStaticIntConstants(CLASSES_RESOLUTION_TYPE))
    }
  }

  private fun reflectStaticIntConstants(className: String): WritableMap {
    val map = Arguments.createMap()
    try {
      val clazz = Class.forName(className)
      clazz.fields
        .filter { Modifier.isStatic(it.modifiers) && it.type == Int::class.javaPrimitiveType }
        .forEach { field ->
          map.putInt(field.name, field.getInt(null))
        }
    } catch (_: Throwable) {
      // Ignore, return empty map
    }
    return map
  }

}

private fun ReadableMap.getStringOrNull(key: String): String? {
  return if (hasKey(key) && !isNull(key)) getString(key) else null
}

private fun ReadableMap.getBooleanOrNull(key: String): Boolean? {
  return if (hasKey(key) && !isNull(key)) getBoolean(key) else null
}

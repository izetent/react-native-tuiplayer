import {
  type ElementRef,
  forwardRef,
  useImperativeHandle,
  useMemo,
  useRef,
} from 'react';
import { StyleSheet, type StyleProp, type ViewStyle } from 'react-native';

import NativeTuiplayer from './NativeTuiplayer';
import TuiplayerShortVideoNativeComponent, {
  type NativeShortVideoSource,
  type NativeVodStrategy,
  type NativeLiveStrategy,
  type NativePreferredResolution,
  Commands,
} from './TuiplayerShortVideoViewNativeComponent';
import type {
  ShortVideoSource,
  TuiplayerLicenseConfig,
  TuiplayerVodStrategyOptions,
  TuiplayerLiveStrategyOptions,
  PreferredResolution,
  TuiplayerLayerConfig,
} from './types';

export const initialize = (config: TuiplayerLicenseConfig) => {
  NativeTuiplayer.initialize(config);
};

const nativeConstants = NativeTuiplayer.getShortVideoConstants
  ? NativeTuiplayer.getShortVideoConstants()
  : undefined;

const FALLBACK_PLAY_MODE = Object.freeze({
  MODE_LIST_LOOP: 0,
  MODE_ONE_LOOP: 1,
  MODE_CUSTOM: 2,
} as const);

const FALLBACK_RESOLUTION_TYPE = Object.freeze({
  GLOBAL: 0,
  CURRENT: 1,
} as const);

/**
 * TUIVideoConst.ListPlayMode 对应的播放模式常量。
 */
export const TuiplayerListPlayMode: Readonly<Record<string, number>> =
  Object.freeze({
    ...FALLBACK_PLAY_MODE,
    ...(nativeConstants?.listPlayMode as Record<string, number> | undefined),
  });

/**
 * TUIConstants.TUIResolutionType 对应的分辨率切换目标常量。
 */
export const TuiplayerResolutionType: Readonly<Record<string, number>> =
  Object.freeze({
    ...FALLBACK_RESOLUTION_TYPE,
    ...(nativeConstants?.resolutionType as Record<string, number> | undefined),
  });

export type TuiplayerShortVideoViewHandle = {
  /**
   * 跳转到指定索引，`smooth` 为 true 时启用平滑滚动。
   */
  startPlayIndex: (index: number, smooth?: boolean) => void;
  /**
   * 更新短视频播放模式。
   */
  setPlayMode: (mode: number) => void;
  /**
   * 立即释放原生资源。
   */
  release: () => void;
  /**
   * 续播当前视频。
   */
  resume: () => void;
  /**
   * 切换当前或全局的首选分辨率。
   */
  switchResolution: (resolution: number, target: number) => void;
  /**
   * 暂停正在进行的预加载任务。
   */
  pausePreload: () => void;
  /**
   * 从当前索引恢复预加载任务。
   */
  resumePreload: () => void;
  /**
   * 运行时启用或禁用用户滑动手势。
   */
  setUserInputEnabled: (enabled: boolean) => void;
};

export type TuiplayerShortVideoViewProps = {
  sources: ShortVideoSource[];
  autoPlay?: boolean;
  style?: StyleProp<ViewStyle>;
  initialIndex?: number;
  paused?: boolean;
  /**
   * 播放模式常量，默认使用 MODE_ONE_LOOP。
   */
  playMode?: number;
  /**
   * 是否允许用户滑动，默认允许。
   */
  userInputEnabled?: boolean;
  /**
   * 自定义滚动速度（每英寸毫秒）。
   */
  pageScrollMsPerInch?: number;
  /**
   * 需要挂载的原生图层类名集合。
   */
  layers?: TuiplayerLayerConfig;
  vodStrategy?: TuiplayerVodStrategyOptions;
  liveStrategy?: TuiplayerLiveStrategyOptions;
  onPageChanged?: (event: {
    nativeEvent: {
      index: number;
      total: number;
    };
  }) => void;
  onEndReached?: (event: {
    nativeEvent: {
      index: number;
      total: number;
    };
  }) => void;
};

export const TuiplayerShortVideoView = forwardRef<
  TuiplayerShortVideoViewHandle,
  TuiplayerShortVideoViewProps
>(
  (
    {
      sources,
      autoPlay = true,
      style,
      initialIndex = -1,
      paused = false,
      playMode,
      userInputEnabled = true,
      pageScrollMsPerInch,
      layers,
      vodStrategy,
      liveStrategy,
      onPageChanged,
      onEndReached,
    },
    ref
  ) => {
    const nativeRef =
      useRef<ElementRef<typeof TuiplayerShortVideoNativeComponent>>(null);

    const normalizedSources = useMemo<NativeShortVideoSource[]>(() => {
      return sources.map((item) => ({
        type: item.type ?? 'fileId',
        appId: item.appId,
        fileId: item.fileId,
        url: item.url,
        coverPictureUrl: item.coverPictureUrl,
        pSign: item.pSign,
        extViewType: item.extViewType,
        autoPlay: item.autoPlay,
        videoConfig: item.videoConfig,
      }));
    }, [sources]);

    const normalizedVodStrategy = useMemo<NativeVodStrategy | undefined>(() => {
      if (!vodStrategy) {
        return undefined;
      }

      return {
        preloadCount: vodStrategy.preloadCount,
        preDownloadSize: vodStrategy.preDownloadSize,
        preLoadBufferSize: vodStrategy.preLoadBufferSize,
        maxBufferSize: vodStrategy.maxBufferSize,
        preferredResolution: vodStrategy.preferredResolution
          ? normalizePreferredResolution(vodStrategy.preferredResolution)
          : undefined,
        progressInterval: vodStrategy.progressInterval,
        renderMode: vodStrategy.renderMode,
        mediaType: vodStrategy.mediaType,
        resumeMode:
          typeof vodStrategy.resumeMode === 'number'
            ? String(vodStrategy.resumeMode)
            : vodStrategy.resumeMode,
        enableAutoBitrate: vodStrategy.enableAutoBitrate,
        enableAccurateSeek: vodStrategy.enableAccurateSeek,
        audioNormalization: vodStrategy.audioNormalization,
        retainPreVod: vodStrategy.retainPreVod,
        superResolutionMode:
          typeof vodStrategy.superResolutionMode === 'number'
            ? String(vodStrategy.superResolutionMode)
            : vodStrategy.superResolutionMode,
        retryCount: vodStrategy.retryCount,
        prePlayStrategy: vodStrategy.prePlayStrategy,
      } satisfies NativeVodStrategy;
    }, [vodStrategy]);

    const normalizedLiveStrategy = useMemo<
      NativeLiveStrategy | undefined
    >(() => {
      if (!liveStrategy) {
        return undefined;
      }
      return {
        renderMode: liveStrategy.renderMode,
        retainPreLive: liveStrategy.retainPreLive,
        prePlayStrategy: liveStrategy.prePlayStrategy,
      } satisfies NativeLiveStrategy;
    }, [liveStrategy]);

    useImperativeHandle(ref, () => ({
      startPlayIndex: (index: number, smooth = false) => {
        if (nativeRef.current != null) {
          Commands.startPlayIndex(nativeRef.current, index, smooth);
        }
      },
      setPlayMode: (mode: number) => {
        if (nativeRef.current != null) {
          Commands.setPlayMode(nativeRef.current, mode);
        }
      },
      release: () => {
        if (nativeRef.current != null) {
          Commands.release(nativeRef.current);
        }
      },
      resume: () => {
        if (nativeRef.current != null) {
          Commands.resume(nativeRef.current);
        }
      },
      switchResolution: (resolution: number, target: number) => {
        if (nativeRef.current != null) {
          Commands.switchResolution(nativeRef.current, resolution, target);
        }
      },
      pausePreload: () => {
        if (nativeRef.current != null) {
          Commands.pausePreload(nativeRef.current);
        }
      },
      resumePreload: () => {
        if (nativeRef.current != null) {
          Commands.resumePreload(nativeRef.current);
        }
      },
      setUserInputEnabled: (enabled: boolean) => {
        if (nativeRef.current != null) {
          Commands.setUserInputEnabled(nativeRef.current, enabled);
        }
      },
    }));

    const normalizedLayers = useMemo(() => {
      if (!layers) {
        return undefined;
      }
      return {
        vodLayers: layers.vodLayers,
        liveLayers: layers.liveLayers,
        customLayers: layers.customLayers,
      };
    }, [layers]);

    return (
      <TuiplayerShortVideoNativeComponent
        ref={nativeRef}
        style={StyleSheet.compose(styles.container, style)}
        sources={normalizedSources}
        autoPlay={autoPlay}
        initialIndex={initialIndex}
        paused={paused}
        playMode={playMode ?? -1}
        userInputEnabled={userInputEnabled}
        pageScrollMsPerInch={pageScrollMsPerInch}
        layers={normalizedLayers}
        vodStrategy={normalizedVodStrategy}
        liveStrategy={normalizedLiveStrategy}
        onPageChanged={onPageChanged}
        onEndReached={onEndReached}
      />
    );
  }
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
});

export type {
  ShortVideoSource,
  TuiplayerLicenseConfig,
  TuiplayerVodStrategyOptions,
  TuiplayerLiveStrategyOptions,
} from './types';

export type { TuiplayerLayerConfig } from './types';

function normalizePreferredResolution(value: PreferredResolution | undefined) {
  if (!value) {
    return undefined;
  }
  return {
    width: value.width,
    height: value.height,
  } satisfies NativePreferredResolution;
}

import {
  type ElementRef,
  forwardRef,
  useCallback,
  useEffect,
  useImperativeHandle,
  useMemo,
  useRef,
  useState,
} from 'react';
import {
  StyleSheet,
  type StyleProp,
  type ViewStyle,
  findNodeHandle,
} from 'react-native';

import NativeTuiplayer, {
  type CurrentShortVideoInfo,
  type ShortVideoSourcePayload,
} from './NativeTuiplayer';
import TuiplayerShortVideoNativeComponent, {
  type NativeShortVideoSource,
  type NativeShortVideoMeta,
  type NativeVodStrategy,
  type NativeLiveStrategy,
  type NativePreferredResolution,
  Commands,
} from './TuiplayerShortVideoViewNativeComponent';
import type {
  ShortVideoSource,
  ShortVideoOverlayMeta,
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

type VodPlayerTargetOptions = {
  index?: number;
};

type VodSwitchResolutionOptions = VodPlayerTargetOptions & {
  target?: number;
};

type VodPlayerCommand =
  | 'startPlay'
  | 'resumePlay'
  | 'pause'
  | 'stop'
  | 'seekTo'
  | 'isPlaying'
  | 'setLoop'
  | 'isLoop'
  | 'setRate'
  | 'getDuration'
  | 'getCurrentPlaybackTime'
  | 'getPlayableDuration'
  | 'setMute'
  | 'setAudioPlayoutVolume'
  | 'setMirror'
  | 'setBitrateIndex'
  | 'getBitrateIndex'
  | 'getSupportResolution'
  | 'setRenderRotation'
  | 'setRenderMode'
  | 'getWidth'
  | 'getHeight'
  | 'switchResolution'
  | 'setAudioNormalization'
  | 'enableHardwareDecode';

type VodPlayerCommandOptions = Record<string, unknown>;

export type VodPlayerSupportResolution = Readonly<{
  index: number;
  width: number;
  height: number;
  bitrate: number;
}>;

export type TuiplayerVodPlayerHandle = {
  startPlay: (
    source: ShortVideoSource,
    options?: VodPlayerTargetOptions
  ) => Promise<void>;
  resume: (options?: VodPlayerTargetOptions) => Promise<void>;
  pause: (options?: VodPlayerTargetOptions) => Promise<void>;
  stop: (
    options?: VodPlayerTargetOptions & { clearLastImage?: boolean }
  ) => Promise<void>;
  seekTo: (time: number, options?: VodPlayerTargetOptions) => Promise<void>;
  isPlaying: (options?: VodPlayerTargetOptions) => Promise<boolean>;
  setLoop: (loop: boolean, options?: VodPlayerTargetOptions) => Promise<void>;
  isLoop: (options?: VodPlayerTargetOptions) => Promise<boolean>;
  setRate: (rate: number, options?: VodPlayerTargetOptions) => Promise<void>;
  getDuration: (options?: VodPlayerTargetOptions) => Promise<number>;
  getCurrentPlaybackTime: (options?: VodPlayerTargetOptions) => Promise<number>;
  getPlayableDuration: (options?: VodPlayerTargetOptions) => Promise<number>;
  setMute: (mute: boolean, options?: VodPlayerTargetOptions) => Promise<void>;
  setAudioPlayoutVolume: (
    volume: number,
    options?: VodPlayerTargetOptions
  ) => Promise<void>;
  setMirror: (
    mirror: boolean,
    options?: VodPlayerTargetOptions
  ) => Promise<void>;
  setBitrateIndex: (
    index: number,
    options?: VodPlayerTargetOptions
  ) => Promise<void>;
  getBitrateIndex: (options?: VodPlayerTargetOptions) => Promise<number>;
  getSupportResolution: (
    options?: VodPlayerTargetOptions
  ) => Promise<VodPlayerSupportResolution[]>;
  setRenderRotation: (
    rotation: number,
    options?: VodPlayerTargetOptions
  ) => Promise<void>;
  setRenderMode: (
    mode: number,
    options?: VodPlayerTargetOptions
  ) => Promise<void>;
  getWidth: (options?: VodPlayerTargetOptions) => Promise<number>;
  getHeight: (options?: VodPlayerTargetOptions) => Promise<number>;
  switchResolution: (
    resolution: number,
    options?: VodSwitchResolutionOptions & VodPlayerTargetOptions
  ) => Promise<boolean | void>;
  setAudioNormalization: (
    value: number,
    options?: VodPlayerTargetOptions
  ) => Promise<void>;
  enableHardwareDecode: (
    enable: boolean,
    options?: VodPlayerTargetOptions
  ) => Promise<boolean | void>;
};
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
  /**
   * 在页面切换等场景下同步播放状态，避免黑屏或意外恢复播放。
   */
  syncPlaybackState: () => void;
  /**
   * 更新指定索引的视频元信息（点赞、评论数等）。
   */
  updateMeta: (index: number, meta: ShortVideoOverlayMeta) => void;
  /**
   * 获取当前正在播放的短视频信息。
   */
  getCurrentSource: () => Promise<CurrentShortVideoInfo | null>;
  /**
   * 获取当前列表内的数据条数。
   */
  getDataCount: () => Promise<number>;
  /**
   * 按索引获取列表中的数据快照。
   */
  getDataByIndex: (index: number) => Promise<ShortVideoSourcePayload | null>;
  /**
   * 移除指定位置的数据。
   */
  removeData: (index: number) => Promise<void>;
  /**
   * 移除一段区间的数据。
   */
  removeRangeData: (index: number, count: number) => Promise<void>;
  /**
   * 按索引集合批量移除数据。
   */
  removeDataByIndexes: (indexes: number[]) => Promise<void>;
  /**
   * 在指定位置插入一条数据。
   */
  addData: (source: ShortVideoSource, index?: number) => Promise<void>;
  /**
   * 在指定位置插入一组数据。
   */
  addRangeData: (
    sources: ShortVideoSource[],
    startIndex?: number
  ) => Promise<void>;
  /**
   * 替换指定位置的数据。
   */
  replaceData: (source: ShortVideoSource, index: number) => Promise<void>;
  /**
   * 替换指定区间的数据。
   */
  replaceRangeData: (
    sources: ShortVideoSource[],
    startIndex: number
  ) => Promise<void>;
  /**
   * VOD 播放器控制接口。
   */
  vodPlayer: TuiplayerVodPlayerHandle;
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
  onTopReached?: (event: {
    nativeEvent: {
      offset: number;
    };
  }) => void;
  onEndReached?: (event: {
    nativeEvent: {
      index: number;
      total: number;
    };
  }) => void;
  onVodEvent?: (event: {
    nativeEvent: {
      type: string;
      payload?: Record<string, unknown>;
    };
  }) => void;
  onReady?: () => void;
};

type PendingNativeCommand = (
  ref: ElementRef<typeof TuiplayerShortVideoNativeComponent>
) => void;

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
      onTopReached,
      onEndReached,
      onVodEvent,
      onReady: onReadyProp,
    },
    ref
  ) => {
    const nativeRef =
      useRef<ElementRef<typeof TuiplayerShortVideoNativeComponent>>(null);
    const [nativeReady, setNativeReady] = useState(false);
    const nativeReadyRef = useRef(false);
    const pendingNativeCommands = useRef<PendingNativeCommand[]>([]);

    const flushPendingNativeCommands = useCallback(() => {
      if (!nativeReadyRef.current) {
        return;
      }
      const view = nativeRef.current;
      if (!view) {
        return;
      }
      const queue = pendingNativeCommands.current;
      pendingNativeCommands.current = [];
      queue.forEach((command) => {
        try {
          command(view);
        } catch (error) {
          console.warn('[ShortVideo] 执行原生命令失败:', error);
        }
      });
    }, []);

    const runOrQueueNativeCommand = useCallback(
      (command: PendingNativeCommand) => {
        const view = nativeRef.current;
        if (nativeReadyRef.current && view) {
          try {
            command(view);
          } catch (error) {
            console.warn('[ShortVideo] 执行原生命令失败:', error);
          }
        } else {
          pendingNativeCommands.current.push(command);
        }
      },
      []
    );

    useEffect(() => {
      return () => {
        nativeReadyRef.current = false;
        pendingNativeCommands.current = [];
      };
    }, []);

    useEffect(() => {
      if (nativeReady) {
        flushPendingNativeCommands();
      }
    }, [nativeReady, flushPendingNativeCommands]);

    const buildSourcePayload = useCallback(
      (item: ShortVideoSource): ShortVideoSourcePayload => ({
        type: item.type ?? 'fileId',
        appId: item.appId,
        fileId: item.fileId,
        url: item.url,
        coverPictureUrl: item.coverPictureUrl,
        pSign: item.pSign,
        extViewType: item.extViewType,
        autoPlay: item.autoPlay,
        videoConfig: item.videoConfig,
        meta: item.meta,
      }),
      []
    );

    const toNativeSource = useCallback(
      (item: ShortVideoSource): NativeShortVideoSource =>
        buildSourcePayload(item) as NativeShortVideoSource,
      [buildSourcePayload]
    );

    const normalizedSources = useMemo<NativeShortVideoSource[]>(() => {
      return sources.map(toNativeSource);
    }, [sources, toNativeSource]);

    const effectiveSources = useMemo<NativeShortVideoSource[]>(() => {
      if (nativeReady) {
        return normalizedSources;
      }
      return [] as NativeShortVideoSource[];
    }, [nativeReady, normalizedSources]);

    const handleNativeReady = useCallback(() => {
      if (nativeReadyRef.current) {
        return;
      }
      nativeReadyRef.current = true;
      setNativeReady(true);
      if (typeof onReadyProp === 'function') {
        onReadyProp();
      }
    }, [onReadyProp]);

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

    const serializeVodOptions = useCallback(
      (
        _command: VodPlayerCommand,
        options?: VodPlayerCommandOptions
      ): Record<string, unknown> | undefined => {
        if (options == null) {
          return undefined;
        }
        const payload: Record<string, unknown> = {};
        for (const [key, value] of Object.entries(options)) {
          if (value == null) {
            continue;
          }
          if (key === 'source' && typeof value === 'object') {
            payload.source = buildSourcePayload(value as ShortVideoSource);
          } else if (key === 'sources' && Array.isArray(value)) {
            payload.sources = (value as ShortVideoSource[]).map((item) =>
              buildSourcePayload(item)
            );
          } else {
            payload[key] = value;
          }
        }
        return payload;
      },
      [buildSourcePayload]
    );

    const invokeVodPlayer = useCallback(
      async <Result = unknown,>(
        command: VodPlayerCommand,
        options?: VodPlayerCommandOptions
      ): Promise<Result> => {
        if (!nativeReadyRef.current || nativeRef.current == null) {
          throw new Error(
            'TuiplayerShortVideoView native instance is not ready.'
          );
        }
        const viewTag = findNodeHandle(nativeRef.current);
        if (viewTag == null) {
          throw new Error('Unable to resolve native view tag');
        }
        const payload = serializeVodOptions(command, options);
        const result = await NativeTuiplayer.callShortVideoVodPlayer(
          viewTag,
          command,
          payload
        );
        return result as Result;
      },
      [nativeRef, serializeVodOptions]
    );

    const vodPlayerHandle = useMemo<TuiplayerVodPlayerHandle>(
      () => ({
        startPlay: async (source, options) => {
          await invokeVodPlayer('startPlay', {
            ...options,
            source,
          });
        },
        resume: async (options) => {
          await invokeVodPlayer('resumePlay', options);
        },
        pause: async (options) => {
          await invokeVodPlayer('pause', options);
        },
        stop: async (options) => {
          await invokeVodPlayer('stop', options);
        },
        seekTo: async (time, options) => {
          await invokeVodPlayer('seekTo', { ...options, time });
        },
        isPlaying: (options) => invokeVodPlayer<boolean>('isPlaying', options),
        setLoop: async (loop, options) => {
          await invokeVodPlayer('setLoop', { ...options, loop });
        },
        isLoop: (options) => invokeVodPlayer<boolean>('isLoop', options),
        setRate: async (rate, options) => {
          await invokeVodPlayer('setRate', { ...options, rate });
        },
        getDuration: (options) =>
          invokeVodPlayer<number>('getDuration', options),
        getCurrentPlaybackTime: (options) =>
          invokeVodPlayer<number>('getCurrentPlaybackTime', options),
        getPlayableDuration: (options) =>
          invokeVodPlayer<number>('getPlayableDuration', options),
        setMute: async (mute, options) => {
          await invokeVodPlayer('setMute', { ...options, mute });
        },
        setAudioPlayoutVolume: async (volume, options) => {
          await invokeVodPlayer('setAudioPlayoutVolume', {
            ...options,
            volume,
          });
        },
        setMirror: async (mirror, options) => {
          await invokeVodPlayer('setMirror', { ...options, mirror });
        },
        setBitrateIndex: async (index, options) => {
          await invokeVodPlayer('setBitrateIndex', { ...options, index });
        },
        getBitrateIndex: (options) =>
          invokeVodPlayer<number>('getBitrateIndex', options),
        getSupportResolution: (options) =>
          invokeVodPlayer<VodPlayerSupportResolution[]>(
            'getSupportResolution',
            options
          ),
        setRenderRotation: async (rotation, options) => {
          await invokeVodPlayer('setRenderRotation', {
            ...options,
            rotation,
          });
        },
        setRenderMode: async (mode, options) => {
          await invokeVodPlayer('setRenderMode', {
            ...options,
            mode,
          });
        },
        getWidth: (options) => invokeVodPlayer<number>('getWidth', options),
        getHeight: (options) => invokeVodPlayer<number>('getHeight', options),
        switchResolution: (resolution, options) =>
          invokeVodPlayer<boolean | void>('switchResolution', {
            ...options,
            resolution,
          }),
        setAudioNormalization: async (value, options) => {
          await invokeVodPlayer('setAudioNormalization', {
            ...options,
            value,
          });
        },
        enableHardwareDecode: (enable, options) =>
          invokeVodPlayer<boolean | void>('enableHardwareDecode', {
            ...options,
            enable,
          }),
      }),
      [invokeVodPlayer]
    );

    useImperativeHandle(
      ref,
      () => ({
        startPlayIndex: (index: number, smooth = false) => {
          runOrQueueNativeCommand((view) => {
            Commands.startPlayIndex(view, index, smooth);
          });
        },
        setPlayMode: (mode: number) => {
          runOrQueueNativeCommand((view) => {
            Commands.setPlayMode(view, mode);
          });
        },
        release: () => {
          runOrQueueNativeCommand((view) => {
            Commands.release(view);
          });
        },
        resume: () => {
          runOrQueueNativeCommand((view) => {
            Commands.resume(view);
          });
        },
        switchResolution: (resolution: number, target: number) => {
          runOrQueueNativeCommand((view) => {
            Commands.switchResolution(view, resolution, target);
          });
        },
        pausePreload: () => {
          runOrQueueNativeCommand((view) => {
            Commands.pausePreload(view);
          });
        },
        resumePreload: () => {
          runOrQueueNativeCommand((view) => {
            Commands.resumePreload(view);
          });
        },
        setUserInputEnabled: (enabled: boolean) => {
          runOrQueueNativeCommand((view) => {
            Commands.setUserInputEnabled(view, enabled);
          });
        },
        syncPlaybackState: () => {
          runOrQueueNativeCommand((view) => {
            Commands.syncPlaybackState(view);
          });
        },
        updateMeta: (index: number, meta: ShortVideoOverlayMeta) => {
          runOrQueueNativeCommand((view) => {
            Commands.updateMeta(view, index, normalizeOverlayMeta(meta));
          });
        },
        getCurrentSource: async () => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return null;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return null;
          }
          return NativeTuiplayer.getCurrentShortVideoSource(viewTag);
        },
        getDataCount: async () => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return 0;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return 0;
          }
          return NativeTuiplayer.getShortVideoDataCount(viewTag);
        },
        getDataByIndex: async (index: number) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return null;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return null;
          }
          return NativeTuiplayer.getShortVideoDataByIndex(viewTag, index);
        },
        removeData: async (index: number) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return;
          }
          await NativeTuiplayer.removeShortVideoData(viewTag, index);
        },
        removeRangeData: async (index: number, count: number) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return;
          }
          await NativeTuiplayer.removeShortVideoRange(viewTag, index, count);
        },
        removeDataByIndexes: async (indexes: number[]) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return;
          }
          await NativeTuiplayer.removeShortVideoDataByIndexes(viewTag, indexes);
        },
        addData: async (source: ShortVideoSource, index = -1) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return;
          }
          const payload = buildSourcePayload(source);
          await NativeTuiplayer.addShortVideoData(viewTag, payload, index);
        },
        addRangeData: async (list: ShortVideoSource[], startIndex = -1) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return;
          }
          const payload = list.map(buildSourcePayload);
          await NativeTuiplayer.addShortVideoRange(
            viewTag,
            payload,
            startIndex
          );
        },
        replaceData: async (source: ShortVideoSource, index = -1) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return;
          }
          const payload = buildSourcePayload(source);
          await NativeTuiplayer.replaceShortVideoData(viewTag, payload, index);
        },
        replaceRangeData: async (
          list: ShortVideoSource[],
          startIndex: number
        ) => {
          if (!nativeReadyRef.current || nativeRef.current == null) {
            return;
          }
          const viewTag = findNodeHandle(nativeRef.current);
          if (viewTag == null) {
            return;
          }
          const payload = list.map(buildSourcePayload);
          await NativeTuiplayer.replaceShortVideoRange(
            viewTag,
            payload,
            startIndex
          );
        },
        vodPlayer: vodPlayerHandle,
      }),
      [buildSourcePayload, runOrQueueNativeCommand, vodPlayerHandle]
    );

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
        sources={effectiveSources}
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
        onTopReached={onTopReached}
        onEndReached={onEndReached}
        onVodEvent={onVodEvent}
        onReady={handleNativeReady}
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
  ShortVideoOverlayMeta,
  TuiplayerLicenseConfig,
  TuiplayerVodStrategyOptions,
  TuiplayerLiveStrategyOptions,
  TuiplayerLayerConfig,
  ShortVideoSourceSnapshot,
  CurrentShortVideoInfo,
} from './types';

function normalizePreferredResolution(value: PreferredResolution | undefined) {
  if (!value) {
    return undefined;
  }
  return {
    width: value.width,
    height: value.height,
  } satisfies NativePreferredResolution;
}

function normalizeOverlayMeta(
  meta: ShortVideoOverlayMeta
): NativeShortVideoMeta {
  return {
    authorName: meta.authorName,
    authorAvatar: meta.authorAvatar,
    title: meta.title,
    likeCount:
      typeof meta.likeCount === 'number' && Number.isFinite(meta.likeCount)
        ? meta.likeCount
        : undefined,
    commentCount:
      typeof meta.commentCount === 'number' &&
      Number.isFinite(meta.commentCount)
        ? meta.commentCount
        : undefined,
    favoriteCount:
      typeof meta.favoriteCount === 'number' &&
      Number.isFinite(meta.favoriteCount)
        ? meta.favoriteCount
        : undefined,
    isLiked: meta.isLiked,
    isBookmarked: meta.isBookmarked,
    isFollowed: meta.isFollowed,
    watchMoreText: meta.watchMoreText,
  };
}

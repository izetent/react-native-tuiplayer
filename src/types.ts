import type { Component, RefObject } from 'react';
import { findNodeHandle } from 'react-native';

import type {
  NativePlayerConfig,
  NativeSubtitleSource,
  NativeVodSource,
  NativeVodStrategy,
} from './NativeTxplayer';

export type {
  NativePlayerConfig,
  NativeSubtitleSource,
  NativeVodSource,
  NativeVodStrategy,
  NativeBitrateItem,
} from './NativeTxplayer';

export const PLAYER_VIEW_MANAGER = 'TUIShortVideoItemView';

export const EVENT_PLAY_EVENT = 'txplayer.onPlayEvent';
export const EVENT_CONTROLLER_BIND = 'txplayer.onControllerBind';
export const EVENT_CONTROLLER_UNBIND = 'txplayer.onControllerUnbind';
export const EVENT_VIEW_DISPOSED = 'txplayer.onViewDisposed';
export const EVENT_SUBTITLE_TRACKS = 'txplayer.onSubtitleTracks';

export interface RNPlayerConfig {
  licenseUrl: string;
  licenseKey: string;
  enableLog?: boolean;
}

export interface RNSubtitleSource {
  url: string;
  mimeType: string;
  name?: string;
}

export interface RNSubtitleTrackInfo {
  trackIndex: number;
  name?: string;
  language?: string;
  type?: number;
}

export interface RNVideoSource {
  videoURL?: string;
  coverPictureUrl?: string;
  appId?: number;
  fileId?: string;
  pSign?: string;
  isAutoPlay?: boolean;
  extInfo?: Record<string, unknown> | null;
  subtitleSources?: RNSubtitleSource[] | null;
}

export interface RNPlayerVodStrategy {
  preloadCount?: number;
  preDownloadSize?: number;
  preloadBufferSizeInMB?: number;
  maxBufferSize?: number;
  preferredResolution?: number;
  progressInterval?: number;
  renderMode?: number;
  enableSuperResolution?: boolean;
}

export type RNPlayerViewHandle =
  | number
  | null
  | undefined
  | Component<any, any>
  | RefObject<Component<any, any>>
  | { current?: any };

export interface RNVodEvent {
  [key: string]: unknown;
}

export interface RNVodControlListener {
  onVodControllerBind?: () => void;
  onVodControllerUnBind?: () => void;
  onVodPlayerEvent?: (event: RNVodEvent) => void;
  onRcvFirstIframe?: (event: RNVodEvent) => void;
  onPlayBegin?: (event: RNVodEvent) => void;
  onPlayEnd?: (event: RNVodEvent) => void;
  onSubtitleTracksUpdate?: (tracks: RNSubtitleTrackInfo[]) => void;
}

export enum TUIPlayerState {
  INIT = 'INIT',
  PLAYING = 'PLAYING',
  PAUSE = 'PAUSE',
  LOADING = 'LOADING',
  END = 'END',
}

export const RNMonetConstant = {
  SR_ALGORITHM_TYPE_STANDARD: 1,
  SR_ALGORITHM_TYPE_PROFESSIONAL_HIGH_QUALITY: 2,
  SR_ALGORITHM_TYPE_PROFESSIONAL_FAST: 3,
} as const;

export const TUIResolutionType = {
  GLOBAL: -1,
  CURRENT: -2,
} as const;

export type RNTUIResolutionType =
  | (typeof TUIResolutionType)[keyof typeof TUIResolutionType]
  | number;

export interface RNPlayerBitrateItem {
  index: number;
  width: number;
  height: number;
  bitrate: number;
}

export const TXVodPlayEvent = {
  PLAY_EVT_ERROR_INVALID_LICENSE: -5,
  PLAY_EVT_CONNECT_SUCC: 2001,
  PLAY_EVT_RTMP_STREAM_BEGIN: 2002,
  PLAY_EVT_RCV_FIRST_I_FRAME: 2003,
  PLAY_EVT_PLAY_BEGIN: 2004,
  PLAY_EVT_PLAY_PROGRESS: 2005,
  PLAY_EVT_PLAY_END: 2006,
  PLAY_EVT_PLAY_LOADING: 2007,
  PLAY_EVT_START_VIDEO_DECODER: 2008,
  PLAY_EVT_CHANGE_RESOLUTION: 2009,
  PLAY_EVT_GET_PLAYINFO_SUCC: 2010,
  PLAY_EVT_CHANGE_ROTATION: 2011,
  PLAY_EVT_GET_MESSAGE: 2012,
  PLAY_EVT_VOD_PLAY_PREPARED: 2013,
  PLAY_EVT_VOD_LOADING_END: 2014,
  PLAY_EVT_STREAM_SWITCH_SUCC: 2015,
  PLAY_EVT_RENDER_FIRST_FRAME_ON_VIEW: 2033,
  PLAY_ERR_NET_DISCONNECT: -2301,
  PLAY_ERR_GET_RTMP_ACC_URL_FAIL: -2302,
  PLAY_ERR_FILE_NOT_FOUND: -2303,
  PLAY_ERR_HEVC_DECODE_FAIL: -2304,
  PLAY_ERR_HLS_KEY: -2305,
  PLAY_ERR_GET_PLAYINFO_FAIL: -2306,
  PLAY_ERR_STREAM_SWITCH_FAIL: -2307,
  PLAY_WARNING_VIDEO_DECODE_FAIL: 2101,
  PLAY_WARNING_AUDIO_DECODE_FAIL: 2102,
  PLAY_WARNING_RECONNECT: 2103,
  PLAY_WARNING_RECV_DATA_LAG: 2104,
  PLAY_WARNING_VIDEO_PLAY_LAG: 2105,
  PLAY_WARNING_HW_ACCELERATION_FAIL: 2106,
  PLAY_WARNING_VIDEO_DISCONTINUITY: 2107,
  PLAY_WARNING_DNS_FAIL: 3001,
  PLAY_WARNING_SEVER_CONN_FAIL: 3002,
  PLAY_WARNING_SHAKE_FAIL: 3003,
  PLAY_WARNING_READ_WRITE_FAIL: 3005,
  PLAY_WARNING_SPEAKER_DEVICE_ABNORMAL: 1205,
  VOD_PLAY_EVT_SEEK_COMPLETE: 2019,
  VOD_PLAY_EVT_VIDEO_SEI: 2030,
  VOD_PLAY_EVT_HEVC_DOWNGRADE_PLAYBACK: 2031,
  VOD_PLAY_EVT_LOOP_ONCE_COMPLETE: 6001,
  PLAY_EVT_FIRST_FRAME_RENDERED: 50001,
  EVT_EVENT: 'event',
  EVT_UTC_TIME: 'EVT_UTC_TIME',
  EVT_BLOCK_DURATION: 'EVT_BLOCK_DURATION',
  EVT_TIME: 'EVT_TIME',
  EVT_DESCRIPTION: 'EVT_MSG',
  EVT_PARAM1: 'EVT_PARAM1',
  EVT_PARAM2: 'EVT_PARAM2',
  EVT_VIDEO_WIDTH: 'EVT_WIDTH',
  EVT_VIDEO_HEIGHT: 'EVT_HEIGHT',
  EVT_GET_MSG: 'EVT_GET_MSG',
  EVT_PLAY_COVER_URL: 'EVT_PLAY_COVER_URL',
  EVT_PLAY_URL: 'EVT_PLAY_URL',
  EVT_PLAY_NAME: 'EVT_PLAY_NAME',
  EVT_PLAY_DESCRIPTION: 'EVT_PLAY_DESCRIPTION',
  EVT_PLAY_PROGRESS_MS: 'EVT_PLAY_PROGRESS_MS',
  EVT_PLAY_DURATION_MS: 'EVT_PLAY_DURATION_MS',
  EVT_PLAY_PROGRESS: 'EVT_PLAY_PROGRESS',
  EVT_PLAY_DURATION: 'EVT_PLAY_DURATION',
  EVT_PLAYABLE_DURATION_MS: 'EVT_PLAYABLE_DURATION_MS',
  EVT_PLAYABLE_DURATION: 'EVT_PLAYABLE_DURATION',
  EVT_PLAYABLE_RATE: 'EVT_PLAYABLE_RATE',
  EVT_IMAGESPRIT_WEBVTTURL: 'EVT_IMAGESPRIT_WEBVTTURL',
  EVT_IMAGESPRIT_IMAGEURL_LIST: 'EVT_IMAGESPRIT_IMAGEURL_LIST',
  EVT_DRM_TYPE: 'EVT_DRM_TYPE',
  EVT_KEY_WATER_MARK_TEXT: 'EVT_KEY_WATER_MARK_TEXT',
  EVT_KEY_SEI_TYPE: 'EVT_KEY_SEI_TYPE',
  EVT_KEY_SEI_SIZE: 'EVT_KEY_SEI_SIZE',
  EVT_KEY_SEI_DATA: 'EVT_KEY_SEI_DATA',
  EVT_PLAY_PDT_TIME_MS: 'EVT_PLAY_PDT_TIME_MS',
  SUPER_RESOLUTION_OPTION_KEY: 'PARAM_SUPER_RESOLUTION_TYPE',
} as const;

const DEFAULT_VOD_STRATEGY: NativeVodStrategy = {
  preloadCount: 3,
  preDownloadSize: 1,
  preloadBufferSizeInMB: 0.5,
  maxBufferSize: 10,
  preferredResolution: 720 * 1280,
  progressInterval: 500,
  renderMode: 1,
  enableSuperResolution: false,
};

export function serializePlayerConfig(
  config: RNPlayerConfig
): NativePlayerConfig {
  return {
    licenseUrl: config.licenseUrl,
    licenseKey: config.licenseKey,
    enableLog: config.enableLog ?? true,
  };
}

export function serializeVideoSource(source: RNVideoSource): NativeVodSource {
  return {
    videoURL: source.videoURL,
    coverPictureUrl: source.coverPictureUrl,
    appId: source.appId,
    fileId: source.fileId,
    pSign: source.pSign,
    isAutoPlay: source.isAutoPlay ?? true,
    extInfo: source.extInfo ?? null,
    subtitleSources: serializeSubtitleSources(source.subtitleSources),
  };
}

function serializeSubtitleSources(
  sources?: RNSubtitleSource[] | null
): NativeSubtitleSource[] | null {
  if (!sources || sources.length === 0) {
    return null;
  }
  return sources.map(serializeSubtitleSource);
}

function serializeSubtitleSource(
  source: RNSubtitleSource
): NativeSubtitleSource {
  return {
    url: source.url,
    mimeType: source.mimeType,
    name: source.name,
  };
}

export function serializeVideoSources(
  sources: RNVideoSource[]
): NativeVodSource[] {
  return sources.map(serializeVideoSource);
}

export function serializeVodStrategy(
  strategy?: RNPlayerVodStrategy
): NativeVodStrategy {
  return {
    ...DEFAULT_VOD_STRATEGY,
    ...(strategy ?? {}),
  };
}

export function resolveViewTag(handle: RNPlayerViewHandle): number {
  if (typeof handle === 'number') {
    return handle;
  }
  if (!handle) {
    throw new Error('RNPlayerView ref is required before binding');
  }
  const possibleRef = handle as { current?: any };
  if (possibleRef.current) {
    return resolveViewTag(possibleRef.current);
  }
  const nodeHandle = findNodeHandle(handle as Component<any, any>);
  if (nodeHandle == null) {
    throw new Error('Unable to resolve native view handle');
  }
  return nodeHandle;
}

import type * as React from 'react';
import type { ViewProps } from 'react-native';
// eslint-disable-next-line @react-native/no-deep-imports
import codegenNativeComponent from 'react-native/Libraries/Utilities/codegenNativeComponent';
// eslint-disable-next-line @react-native/no-deep-imports
import codegenNativeCommands from 'react-native/Libraries/Utilities/codegenNativeCommands';
import type {
  Double,
  Int32,
  WithDefault,
} from 'react-native/Libraries/Types/CodegenTypes';
import type { ShortVideoSourceSnapshot } from './types';

export type NativeShortVideoMeta = Readonly<{
  name?: string;
  icon?: string;
  type?: ReadonlyArray<string>;
  details?: string;
  authorName?: string;
  authorAvatar?: string;
  title?: string;
  likeCount?: Double;
  commentCount?: Double;
  favoriteCount?: Double;
  isLiked?: boolean;
  isBookmarked?: boolean;
  isFollowed?: boolean;
  showCover?: boolean;
  playText?: string;
  moreText?: string;
  watchMoreText?: string;
  isShowPaly?: boolean;
}>;

export type NativeSubtitleSource = Readonly<{
  name?: string;
  url: string;
  mimeType?: string;
}>;

export type NativeShortVideoSource = Readonly<{
  type?: WithDefault<string, 'fileId'>;
  appId?: Int32;
  fileId?: string;
  url?: string;
  coverPictureUrl?: string;
  pSign?: string;
  extViewType?: Int32;
  autoPlay?: boolean;
  videoConfig?: Readonly<{
    preloadBufferSizeInMB?: Double;
    preDownloadSize?: Double;
  }>;
  meta?: NativeShortVideoMeta;
  subtitles?: ReadonlyArray<NativeSubtitleSource>;
}>;

export type NativeLayerConfig = Readonly<{
  vodLayers?: string[];
  liveLayers?: string[];
  customLayers?: string[];
}>;

export type NativeSubtitleStyle = Readonly<{
  canvasWidth?: Int32;
  canvasHeight?: Int32;
  familyName?: string;
  fontSize?: Double;
  fontScale?: Double;
  fontColor?: Int32;
  bold?: boolean;
  outlineWidth?: Double;
  outlineColor?: Int32;
  lineSpace?: Double;
  startMargin?: Double;
  endMargin?: Double;
  verticalMargin?: Double;
}>;

export type NativePreferredResolution = Readonly<{
  width: Int32;
  height: Int32;
}>;

type NativeEventPayload<T> = Readonly<{
  index: Int32;
  total: Int32;
}> &
  T;

export type NativeEvent<T> = Readonly<{
  nativeEvent: NativeEventPayload<T>;
}>;

export type NativeVoidEvent = Readonly<{
  nativeEvent: Record<string, never>;
}>;

export type NativeVodStrategy = Readonly<{
  preloadCount?: Int32;
  preDownloadSize?: Double;
  preLoadBufferSize?: Double;
  maxBufferSize?: Double;
  preferredResolution?: NativePreferredResolution;
  progressInterval?: Int32;
  renderMode?: Int32;
  mediaType?: Int32;
  resumeMode?: string;
  enableAutoBitrate?: boolean;
  enableAccurateSeek?: boolean;
  audioNormalization?: Double;
  retainPreVod?: boolean;
  superResolutionMode?: string;
  retryCount?: Int32;
  prePlayStrategy?: string;
}>;

export type NativeLiveStrategy = Readonly<{
  renderMode?: Int32;
  retainPreLive?: boolean;
  prePlayStrategy?: string;
}>;

export interface TuiplayerShortVideoViewEndReachedEvent {
  index: Int32;
  total: Int32;
}

export interface TuiplayerShortVideoViewTopReachedEvent {
  offset: Int32;
}

export interface TuiplayerShortVideoViewPageChangedEvent {
  index: Int32;
  total: Int32;
}

export interface TuiplayerShortVideoViewPlaybackEvent {
  source?: ShortVideoSourceSnapshot;
}

export interface TuiplayerVodEvent {
  type: string;
  payload?: Readonly<Record<string, unknown> | undefined>;
}

export interface NativeProps extends ViewProps {
  sources?: ReadonlyArray<NativeShortVideoSource>;
  autoPlay?: WithDefault<boolean, true>;
  initialIndex?: WithDefault<Int32, -1>;
  paused?: WithDefault<boolean, false>;
  playMode?: WithDefault<Int32, -1>;
  userInputEnabled?: WithDefault<boolean, true>;
  pageScrollMsPerInch?: Double;
  layers?: NativeLayerConfig;
  vodStrategy?: NativeVodStrategy;
  liveStrategy?: NativeLiveStrategy;
  subtitleStyle?: NativeSubtitleStyle;
  onTopReached?: (
    event: NativeEvent<TuiplayerShortVideoViewTopReachedEvent>
  ) => void;
  onEndReached?: (
    event: NativeEvent<TuiplayerShortVideoViewEndReachedEvent>
  ) => void;
  onPageChanged?: (
    event: NativeEvent<TuiplayerShortVideoViewPageChangedEvent>
  ) => void;
  onPlaybackStart?: (
    event: NativeEvent<TuiplayerShortVideoViewPlaybackEvent>
  ) => void;
  onPlaybackEnd?: (
    event: NativeEvent<TuiplayerShortVideoViewPlaybackEvent>
  ) => void;
  onVodEvent?: (event: NativeEvent<TuiplayerVodEvent>) => void;
  onReady?: (event: NativeVoidEvent) => void;
}

const COMPONENT_NAME = 'TuiplayerShortVideoView';

const TuiplayerShortVideoNativeComponent =
  codegenNativeComponent<NativeProps>(COMPONENT_NAME);

type ComponentType = React.ElementRef<
  typeof TuiplayerShortVideoNativeComponent
>;

type NativeCommands = {
  startPlayIndex: (ref: ComponentType, index: Int32, smooth?: boolean) => void;
  setPlayMode: (ref: ComponentType, mode: Int32) => void;
  release: (ref: ComponentType) => void;
  resume: (ref: ComponentType) => void;
  switchResolution: (
    ref: ComponentType,
    resolution: Double,
    target: Int32
  ) => void;
  pausePreload: (ref: ComponentType) => void;
  resumePreload: (ref: ComponentType) => void;
  setUserInputEnabled: (ref: ComponentType, enabled: boolean) => void;
  updateMeta: (
    ref: ComponentType,
    index: Int32,
    meta: NativeShortVideoMeta
  ) => void;
  syncPlaybackState: (ref: ComponentType) => void;
  setOverlayVisible: (ref: ComponentType, visible: boolean) => void;
  setTopLoadingVisible: (ref: ComponentType, visible: boolean) => void;
  setBottomLoadingVisible: (ref: ComponentType, visible: boolean) => void;
};

export const Commands = codegenNativeCommands<NativeCommands>({
  supportedCommands: [
    'startPlayIndex',
    'setPlayMode',
    'release',
    'resume',
    'switchResolution',
    'pausePreload',
    'resumePreload',
    'setUserInputEnabled',
    'updateMeta',
    'syncPlaybackState',
    'setOverlayVisible',
    'setTopLoadingVisible',
    'setBottomLoadingVisible',
  ],
});

export default TuiplayerShortVideoNativeComponent;

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

export type NativeShortVideoMeta = Readonly<{
  authorName?: string;
  authorAvatar?: string;
  title?: string;
  likeCount?: Double;
  commentCount?: Double;
  favoriteCount?: Double;
  isLiked?: boolean;
  isBookmarked?: boolean;
  isFollowed?: boolean;
  watchMoreText?: string;
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
}>;

export type NativeLayerConfig = Readonly<{
  vodLayers?: string[];
  liveLayers?: string[];
  customLayers?: string[];
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

export interface TuiplayerShortVideoViewPageChangedEvent {
  index: Int32;
  total: Int32;
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
  onEndReached?: (
    event: NativeEvent<TuiplayerShortVideoViewEndReachedEvent>
  ) => void;
  onPageChanged?: (
    event: NativeEvent<TuiplayerShortVideoViewPageChangedEvent>
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
  ],
});

export default TuiplayerShortVideoNativeComponent;

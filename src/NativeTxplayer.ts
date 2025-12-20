import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type NativePlayerConfig = {
  licenseUrl: string;
  licenseKey: string;
  enableLog: boolean;
};

export type NativeVodSource = {
  videoURL?: string;
  coverPictureUrl?: string;
  appId?: number;
  fileId?: string;
  pSign?: string;
  isAutoPlay?: boolean;
  extInfo?: { [key: string]: unknown } | null;
};

export type NativeVodStrategy = {
  preloadCount: number;
  preDownloadSize: number;
  preloadBufferSizeInMB: number;
  maxBufferSize: number;
  preferredResolution: number;
  progressInterval: number;
  renderMode: number;
  enableSuperResolution: boolean;
};

export interface Spec extends TurboModule {
  setPlayerConfig(config: NativePlayerConfig): Promise<void>;
  setMonetAppInfo(
    appId: number,
    authId: number,
    srAlgorithmType: number
  ): Promise<void>;
  createShortController(): Promise<number>;
  shortControllerSetModels(
    controllerId: number,
    sources: NativeVodSource[]
  ): Promise<number>;
  shortControllerAppendModels(
    controllerId: number,
    sources: NativeVodSource[]
  ): Promise<number>;
  shortControllerBindVideoView(
    controllerId: number,
    viewTag: number,
    index: number
  ): Promise<void>;
  shortControllerPreBindVideo(
    controllerId: number,
    viewTag: number,
    index: number
  ): Promise<void>;
  shortControllerSetVodStrategy(
    controllerId: number,
    strategy: NativeVodStrategy
  ): Promise<void>;
  shortControllerStartCurrent(controllerId: number): Promise<number>;
  shortControllerSetVideoLoop(
    controllerId: number,
    isLoop: boolean
  ): Promise<void>;
  shortControllerRelease(controllerId: number): Promise<void>;
  vodPlayerStartPlay(viewTag: number, source: NativeVodSource): Promise<void>;
  vodPlayerPause(viewTag: number): Promise<void>;
  vodPlayerResume(viewTag: number): Promise<void>;
  vodPlayerSetRate(viewTag: number, rate: number): Promise<void>;
  vodPlayerSetMute(viewTag: number, mute: boolean): Promise<void>;
  vodPlayerSeekTo(viewTag: number, time: number): Promise<void>;
  vodPlayerSetStringOption(
    viewTag: number,
    value: string,
    key: unknown
  ): Promise<void>;
  vodPlayerGetDuration(viewTag: number): Promise<number>;
  vodPlayerGetCurrentPlayTime(viewTag: number): Promise<number>;
  vodPlayerIsPlaying(viewTag: number): Promise<boolean>;
  vodPlayerRelease(viewTag: number): Promise<void>;
  addListener?(eventName: string): void;
  removeListeners?(count: number): void;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Txplayer');

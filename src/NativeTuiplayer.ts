import { TurboModuleRegistry, type TurboModule } from 'react-native';

export type TuiplayerLicenseConfig = {
  licenseUrl?: string;
  licenseKey?: string;
  enableLog?: boolean;
};

export type ShortVideoConstants = {
  listPlayMode?: { [key: string]: number };
  resolutionType?: { [key: string]: number };
};

export type ShortVideoSourceConfigPayload = {
  preloadBufferSizeInMB?: number;
  preDownloadSize?: number;
};

export type ShortVideoSourcePayload = {
  type?: 'fileId' | 'url';
  appId?: number;
  fileId?: string;
  url?: string;
  coverPictureUrl?: string;
  pSign?: string;
  extViewType?: number;
  autoPlay?: boolean;
  videoConfig?: ShortVideoSourceConfigPayload;
  duration?: number;
};

export type CurrentShortVideoInfo = {
  index?: number;
  source?: ShortVideoSourcePayload;
};

export interface Spec extends TurboModule {
  initialize(config: TuiplayerLicenseConfig): void;
  getShortVideoConstants(): ShortVideoConstants;
  getCurrentShortVideoSource(
    viewTag: number
  ): Promise<CurrentShortVideoInfo | null>;
  getShortVideoDataCount(viewTag: number): Promise<number>;
  getShortVideoDataByIndex(
    viewTag: number,
    index: number
  ): Promise<ShortVideoSourcePayload | null>;
  removeShortVideoData(viewTag: number, index: number): Promise<void>;
  removeShortVideoRange(
    viewTag: number,
    index: number,
    count: number
  ): Promise<void>;
  removeShortVideoDataByIndexes(
    viewTag: number,
    indexes: number[]
  ): Promise<void>;
  addShortVideoData(
    viewTag: number,
    source: ShortVideoSourcePayload,
    index: number
  ): Promise<void>;
  addShortVideoRange(
    viewTag: number,
    sources: ShortVideoSourcePayload[],
    startIndex: number
  ): Promise<void>;
  replaceShortVideoData(
    viewTag: number,
    source: ShortVideoSourcePayload,
    index: number
  ): Promise<void>;
  replaceShortVideoRange(
    viewTag: number,
    sources: ShortVideoSourcePayload[],
    startIndex: number
  ): Promise<void>;
  callShortVideoVodPlayer(
    viewTag: number,
    command: string,
    options?: { [key: string]: unknown } | null
  ): Promise<unknown>;
}

export default TurboModuleRegistry.getEnforcing<Spec>('Tuiplayer');

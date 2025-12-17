import NativeTxplayer from './NativeTxplayer';
import { getOrCreateVodController } from './TUIVodPlayerController';
import {
  serializeVideoSources,
  serializeVodStrategy,
  resolveViewTag,
} from './types';
import type {
  FTUIPlayerVodStrategy,
  FTUIVideoSource,
  FTUIPlayerViewHandle,
} from './types';

export class FTUIPlayerShortController {
  private controllerId?: number;
  private initPromise: Promise<number>;
  private released = false;

  constructor() {
    this.initPromise = NativeTxplayer.createShortController().then((id) => {
      this.controllerId = id;
      return id;
    });
  }

  private async ensureControllerId() {
    if (this.released) {
      throw new Error('FTUIPlayerShortController has been released');
    }
    if (this.controllerId != null) {
      return this.controllerId;
    }
    return this.initPromise;
  }

  async setModels(sources: FTUIVideoSource[]) {
    const controllerId = await this.ensureControllerId();
    return NativeTxplayer.shortControllerSetModels(
      controllerId,
      serializeVideoSources(sources)
    );
  }

  async appendModels(sources: FTUIVideoSource[]) {
    const controllerId = await this.ensureControllerId();
    return NativeTxplayer.shortControllerAppendModels(
      controllerId,
      serializeVideoSources(sources)
    );
  }

  async bindVodPlayer(viewHandle: FTUIPlayerViewHandle, index: number) {
    const controllerId = await this.ensureControllerId();
    const viewTag = resolveViewTag(viewHandle);
    await NativeTxplayer.shortControllerBindVideoView(
      controllerId,
      viewTag,
      index
    );
    return getOrCreateVodController(viewTag);
  }

  async preCreateVodPlayer(viewHandle: FTUIPlayerViewHandle, index: number) {
    const controllerId = await this.ensureControllerId();
    const viewTag = resolveViewTag(viewHandle);
    await NativeTxplayer.shortControllerPreBindVideo(
      controllerId,
      viewTag,
      index
    );
  }

  async setVodStrategy(strategy: FTUIPlayerVodStrategy) {
    const controllerId = await this.ensureControllerId();
    await NativeTxplayer.shortControllerSetVodStrategy(
      controllerId,
      serializeVodStrategy(strategy)
    );
  }

  async startCurrent() {
    const controllerId = await this.ensureControllerId();
    return NativeTxplayer.shortControllerStartCurrent(controllerId);
  }

  async setVideoLoop(isLoop: boolean) {
    const controllerId = await this.ensureControllerId();
    await NativeTxplayer.shortControllerSetVideoLoop(controllerId, isLoop);
  }

  async release() {
    if (this.released) {
      return;
    }
    this.released = true;
    const controllerId =
      this.controllerId ?? (await this.initPromise.catch(() => undefined));
    if (controllerId != null) {
      await NativeTxplayer.shortControllerRelease(controllerId);
    }
  }
}

import NativeTxplayer from './NativeTxplayer';
import { getOrCreateVodController } from './TUIVodPlayerController';
import {
  serializeVideoSources,
  serializeVodStrategy,
  resolveViewTag,
} from './types';
import type {
  RNPlayerVodStrategy,
  RNVideoSource,
  RNPlayerViewHandle,
} from './types';

export class RNPlayerShortController {
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
      throw new Error('RNPlayerShortController has been released');
    }
    if (this.controllerId != null) {
      return this.controllerId;
    }
    return this.initPromise;
  }

  async setModels(sources: RNVideoSource[]) {
    const controllerId = await this.ensureControllerId();
    return NativeTxplayer.shortControllerSetModels(
      controllerId,
      serializeVideoSources(sources)
    );
  }

  async appendModels(sources: RNVideoSource[]) {
    const controllerId = await this.ensureControllerId();
    return NativeTxplayer.shortControllerAppendModels(
      controllerId,
      serializeVideoSources(sources)
    );
  }

  async bindVodPlayer(viewHandle: RNPlayerViewHandle, index: number) {
    const controllerId = await this.ensureControllerId();
    const viewTag = resolveViewTag(viewHandle);
    await NativeTxplayer.shortControllerBindVideoView(
      controllerId,
      viewTag,
      index
    );
    return getOrCreateVodController(viewTag);
  }

  async preCreateVodPlayer(viewHandle: RNPlayerViewHandle, index: number) {
    const controllerId = await this.ensureControllerId();
    const viewTag = resolveViewTag(viewHandle);
    await NativeTxplayer.shortControllerPreBindVideo(
      controllerId,
      viewTag,
      index
    );
  }

  async setVodStrategy(strategy: RNPlayerVodStrategy) {
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

  async switchResolution(resolution: number, switchType: number) {
    const controllerId = await this.ensureControllerId();
    await NativeTxplayer.shortControllerSwitchResolution(
      controllerId,
      resolution,
      switchType
    );
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

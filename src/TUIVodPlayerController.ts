import type { EmitterSubscription } from 'react-native';

import NativeTxplayer from './NativeTxplayer';
import { TxplayerEventEmitter } from './events';
import {
  EVENT_CONTROLLER_BIND,
  EVENT_CONTROLLER_UNBIND,
  EVENT_PLAY_EVENT,
  EVENT_VIEW_DISPOSED,
  serializeVideoSource,
  TUIPlayerState,
  TXVodPlayEvent,
} from './types';
import type {
  RNVideoSource,
  RNVodControlListener,
  RNVodEvent,
} from './types';

type PlayEventPayload = {
  viewTag: number;
  event: RNVodEvent;
};

type ControllerEventPayload = {
  viewTag: number;
};

const controllerCache = new Map<number, TUIVodPlayerController>();

export class TUIVodPlayerController {
  private subscriptions: EmitterSubscription[] = [];
  private listeners: Set<RNVodControlListener> = new Set();
  playerState: TUIPlayerState = TUIPlayerState.INIT;

  constructor(private readonly viewTag: number) {
    this.subscriptions = [
      TxplayerEventEmitter.addListener(
        EVENT_PLAY_EVENT,
        this.handlePlayEvent
      ),
      TxplayerEventEmitter.addListener(
        EVENT_CONTROLLER_BIND,
        this.handleBindEvent
      ),
      TxplayerEventEmitter.addListener(
        EVENT_CONTROLLER_UNBIND,
        this.handleUnBindEvent
      ),
    ];
  }

  private handlePlayEvent = (payload: PlayEventPayload) => {
    if (payload.viewTag !== this.viewTag) {
      return;
    }
    const event = payload.event ?? {};
    const eventCodeRaw = event[TXVodPlayEvent.EVT_EVENT] ?? event.event;
    const eventCode = typeof eventCodeRaw === 'number' ? eventCodeRaw : Number(eventCodeRaw);
    if (!Number.isFinite(eventCode)) {
      return;
    }
    switch (eventCode) {
      case TXVodPlayEvent.PLAY_EVT_RCV_FIRST_I_FRAME:
      case TXVodPlayEvent.PLAY_EVT_PLAY_BEGIN:
        this.updatePlayerState(TUIPlayerState.PLAYING);
        break;
      case TXVodPlayEvent.PLAY_EVT_PLAY_LOADING:
        if (this.playerState !== TUIPlayerState.PAUSE) {
          this.updatePlayerState(TUIPlayerState.LOADING);
        }
        break;
      case TXVodPlayEvent.PLAY_EVT_VOD_LOADING_END:
        if (this.playerState === TUIPlayerState.LOADING) {
          this.updatePlayerState(TUIPlayerState.PLAYING);
        }
        break;
      case TXVodPlayEvent.PLAY_EVT_PLAY_END:
        this.updatePlayerState(TUIPlayerState.END);
        break;
      default:
        break;
    }
    this.listeners.forEach((listener) =>
      listener.onVodPlayerEvent?.(event)
    );
  };

  private handleBindEvent = (payload: ControllerEventPayload) => {
    if (payload.viewTag !== this.viewTag) {
      return;
    }
    this.listeners.forEach((listener) => listener.onVodControllerBind?.());
  };

  private handleUnBindEvent = (payload: ControllerEventPayload) => {
    if (payload.viewTag !== this.viewTag) {
      return;
    }
    this.listeners.forEach((listener) => listener.onVodControllerUnBind?.());
  };

  private updatePlayerState(state: TUIPlayerState) {
    this.playerState = state;
  }

  addListener(listener: RNVodControlListener) {
    this.listeners.add(listener);
  }

  removeListener(listener: RNVodControlListener) {
    this.listeners.delete(listener);
  }

  clearListener() {
    this.listeners.clear();
  }

  async startPlay(source: RNVideoSource) {
    await NativeTxplayer.vodPlayerStartPlay(
      this.viewTag,
      serializeVideoSource(source)
    );
  }

  async pause() {
    await NativeTxplayer.vodPlayerPause(this.viewTag);
    this.updatePlayerState(TUIPlayerState.PAUSE);
  }

  async resume() {
    await NativeTxplayer.vodPlayerResume(this.viewTag);
    this.updatePlayerState(TUIPlayerState.PLAYING);
  }

  async setRate(rate: number) {
    await NativeTxplayer.vodPlayerSetRate(this.viewTag, rate);
  }

  async setMute(mute: boolean) {
    await NativeTxplayer.vodPlayerSetMute(this.viewTag, mute);
  }

  async seekTo(time: number) {
    await NativeTxplayer.vodPlayerSeekTo(this.viewTag, time);
  }

  async setStringOption(value: string, key: unknown) {
    await NativeTxplayer.vodPlayerSetStringOption(this.viewTag, value, key);
  }

  getDuration() {
    return NativeTxplayer.vodPlayerGetDuration(this.viewTag);
  }

  getCurrentPlayTime() {
    return NativeTxplayer.vodPlayerGetCurrentPlayTime(this.viewTag);
  }

  isPlaying() {
    return NativeTxplayer.vodPlayerIsPlaying(this.viewTag);
  }

  async release() {
    await NativeTxplayer.vodPlayerRelease(this.viewTag);
    this.dispose();
    controllerCache.delete(this.viewTag);
  }

  dispose() {
    this.listeners.clear();
    this.subscriptions.forEach((sub) => sub.remove());
    this.subscriptions = [];
  }
}

TxplayerEventEmitter.addListener(
  EVENT_VIEW_DISPOSED,
  (payload: ControllerEventPayload) => {
    const controller = controllerCache.get(payload.viewTag);
    if (controller) {
      controller.dispose();
      controllerCache.delete(payload.viewTag);
    }
  }
);

export function getOrCreateVodController(
  viewTag: number
): TUIVodPlayerController {
  const existed = controllerCache.get(viewTag);
  if (existed) {
    return existed;
  }
  const controller = new TUIVodPlayerController(viewTag);
  controllerCache.set(viewTag, controller);
  return controller;
}

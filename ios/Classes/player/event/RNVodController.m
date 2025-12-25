#import "RNVodController.h"

#import "RNConstant.h"
#import "RNEventDispatcher.h"
#import "RNShortVideoView.h"

@interface RNVodController ()

@property(nonatomic, weak) RNShortVideoView *containerView;
@property(nonatomic, strong) TUITXVodPlayer *currentPlayer;

@end

@implementation RNVodController

- (instancetype)initWithContainer:(RNShortVideoView *)container {
  self = [super init];
  if (self) {
    _containerView = container;
  }
  return self;
}

- (NSNumber *)viewTag {
  return self.containerView.reactTag;
}

- (void)emitEvent:(NSString *)event payload:(NSDictionary *)payload {
  NSNumber *tag = [self viewTag];
  if (!tag) {
    return;
  }
  NSMutableDictionary *body = [NSMutableDictionary dictionary];
  body[@"viewTag"] = tag;
  if (payload) {
    body[@"event"] = payload;
  }
  [RNEventDispatcher sendEvent:event body:body];
}

- (void)onBindController:(TUITXVodPlayer *)player {
  self.currentPlayer = player;
  [player setDelegate:self];
  [self emitEvent:RN_EVENT_CONTROLLER_BIND payload:nil];
}

- (void)onUnBindController {
  self.currentPlayer = nil;
  [self emitEvent:RN_EVENT_CONTROLLER_UNBIND payload:nil];
}

- (void)startPlayWithModel:(TUIPlayerVideoModel *)model {
  if (self.currentPlayer) {
    [self.currentPlayer startVodPlayWithModel:model];
  }
}

- (void)pause {
  [self.currentPlayer pausePlay];
}

- (void)resume {
  [self.currentPlayer resumePlay];
}

- (void)setRate:(double)rate {
  [self.currentPlayer setRate:rate];
}

- (void)setMute:(BOOL)mute {
  [self.currentPlayer setMute:mute];
}

- (NSArray<TUIPlayerBitrateItem *> *)getSupportResolution {
  if (!self.currentPlayer) {
    return @[];
  }
  NSArray<TUIPlayerBitrateItem *> *bitrates = [self.currentPlayer supportedBitrates];
  return bitrates ?: @[];
}

- (void)switchResolution:(long)resolution switchType:(NSInteger)switchType {
  TUITXVodPlayer *player = self.currentPlayer;
  if (!player) {
    return;
  }
  NSArray<TUIPlayerBitrateItem *> *bitrates = [player supportedBitrates];
  NSInteger targetIndex = -1;
  for (TUIPlayerBitrateItem *item in bitrates) {
    if (item.width * item.height == resolution) {
      targetIndex = item.index;
      break;
    }
  }
  if (targetIndex < 0 && bitrates.count > 0) {
    targetIndex = bitrates.firstObject.index;
  }
  if (targetIndex >= 0) {
    [player setBitrateIndex:targetIndex];
  }
}

- (void)setMirror:(BOOL)isMirror {
  [self.currentPlayer setMirror:isMirror];
}

- (void)seekToTime:(double)time {
  [self.currentPlayer seekToTime:time];
}

- (NSNumber *)duration {
  return self.currentPlayer ? @(self.currentPlayer.duration) : @(0);
}

- (NSNumber *)currentPlayTime {
  return self.currentPlayer ? @(self.currentPlayer.currentPlaybackTime) : @(0);
}

- (NSNumber *)isPlaying {
  return self.currentPlayer ? @(self.currentPlayer.isPlaying) : @(NO);
}

- (void)setStringOptionWithKey:(id)key value:(NSString *)value {
  if (!self.currentPlayer || !key) {
    return;
  }
  NSString *stringKey = [NSString stringWithFormat:@"%@", key];
  [self.currentPlayer setExtentOptionInfo:@{ value : stringKey }];
}

- (void)releasePlayer {
  [self onShortVideoDestroyed];
}

- (void)onShortVideoDestroyed {
  [self.currentPlayer stopPlay];
  self.currentPlayer = nil;
}

- (void)selectSubtitleTrack:(NSInteger)trackIndex {
  TUILOGW(@"[RNVodController] Subtitle selection is not implemented on iOS (requested index: %ld)",
          (long)trackIndex);
}

#pragma mark - TUIPlayerVodManagerDelegate

- (void)currentPlayer:(TUITXVodPlayer *)player {
}

#pragma mark - TUIVodObserver

- (void)onNetStatus:(TUITXVodPlayer *)player withParam:(NSDictionary *)param {
}

- (void)onPlayEvent:(TUITXVodPlayer *)player event:(int)EvtID withParam:(NSDictionary *)param {
  NSMutableDictionary *payload = [NSMutableDictionary dictionaryWithDictionary:param ?: @{}];
  payload[TMP_LAYER_EVENT_KEY] = @(EvtID);
  [self emitEvent:RN_EVENT_PLAY_EVENT payload:payload];
  if (EvtID == PLAY_EVT_RCV_FIRST_I_FRAME) {
    dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(20 * NSEC_PER_MSEC)),
                   dispatch_get_main_queue(), ^{
                     [self emitEvent:RN_EVENT_PLAY_EVENT
                             payload:@{TMP_LAYER_EVENT_KEY : @(PLAY_EVT_FIRST_FRAME_RENDERED)}];
                   });
  }
}

- (void)vodRenderModeChanged:(TUI_Enum_Type_RenderMode)renderMode {
}

@end

#import "RNVodController.h"

#import "RNConstant.h"
#import "RNEventDispatcher.h"
#import "RNShortVideoView.h"
#import <React/UIView+React.h>
#if __has_include(<TXLiteAVSDK_Player_Premium/TXTrackInfo.h>)
#import <TXLiteAVSDK_Player_Premium/TXTrackInfo.h>
#import <TXLiteAVSDK_Player_Premium/TXVodDef.h>
#elif __has_include(<TXLiteAVSDK_Player/TXTrackInfo.h>)
#import <TXLiteAVSDK_Player/TXTrackInfo.h>
#import <TXLiteAVSDK_Player/TXVodDef.h>
#endif
#import <math.h>
#import <stdlib.h>

static const double kMsPerSecond = 1000.0;

static NSNumber *RNNumberFromValue(id value) {
  if ([value isKindOfClass:[NSNumber class]]) {
    return value;
  }
  if ([value isKindOfClass:[NSString class]]) {
    const char *raw = [(NSString *)value UTF8String];
    if (raw == NULL) {
      return nil;
    }
    char *endptr = NULL;
    double parsed = strtod(raw, &endptr);
    if (endptr != raw && isfinite(parsed)) {
      return @(parsed);
    }
  }
  return nil;
}

static void NormalizeMsPair(NSMutableDictionary *payload, NSString *msKey, NSString *secKey) {
  NSNumber *msValue = RNNumberFromValue(payload[msKey]);
  if (msValue) {
    payload[secKey] = @(msValue.doubleValue / kMsPerSecond);
  }
  [payload removeObjectForKey:msKey];
}

@interface RNVodController ()

@property(nonatomic, weak) RNShortVideoView *containerView;
@property(nonatomic, strong) TUITXVodPlayer *currentPlayer;
@property(nonatomic, copy) NSArray<TXTrackInfo *> *lastSubtitleTracks;
@property(nonatomic, assign) NSInteger selectedSubtitleTrack;

@end

@implementation RNVodController

- (BOOL)autoPlayForModel:(TUIPlayerVideoModel *)model {
  BOOL autoPlay = YES;
  id extInfo = model.extInfo;
  if ([extInfo isKindOfClass:[NSDictionary class]]) {
    id value = ((NSDictionary *)extInfo)[@"isAutoPlay"];
    if ([value isKindOfClass:[NSNumber class]]) {
      autoPlay = ((NSNumber *)value).boolValue;
    }
  }
  return autoPlay;
}

- (NSInteger)intValueFromParam:(NSDictionary *)param key:(NSString *)key {
  id value = param[key];
  if ([value respondsToSelector:@selector(integerValue)]) {
    return ((NSNumber *)value).integerValue;
  }
  if ([value isKindOfClass:[NSString class]]) {
    return (NSInteger)((NSString *)value).doubleValue;
  }
  return 0;
}

- (void)updateVideoSizeFromPlayer:(TUITXVodPlayer *)player {
  if (!player) {
    return;
  }
  NSInteger width = player.width;
  NSInteger height = player.height;
  if (width > 0 && height > 0) {
    [self.containerView updateVideoSize:width height:height];
  }
}

- (void)maybeUpdateVideoSizeFromParams:(NSDictionary *)param player:(TUITXVodPlayer *)player {
  if (!param) {
    [self updateVideoSizeFromPlayer:player];
    return;
  }
  NSInteger width = [self intValueFromParam:param key:@"EVT_WIDTH"];
  NSInteger height = [self intValueFromParam:param key:@"EVT_HEIGHT"];
  if (width <= 0 || height <= 0) {
    width = [self intValueFromParam:param key:@"EVT_PARAM1"];
    height = [self intValueFromParam:param key:@"EVT_PARAM2"];
  }
  if (width > 0 && height > 0) {
    [self.containerView updateVideoSize:width height:height];
  } else {
    [self updateVideoSizeFromPlayer:player];
  }
}

- (instancetype)initWithContainer:(RNShortVideoView *)container {
  self = [super init];
  if (self) {
    _containerView = container;
    _selectedSubtitleTrack = -1;
  }
  return self;
}

#pragma mark - TUIShortVideoItemViewDelegate

- (void)cellPause {
  [self pause];
}

- (void)cellResume {
  [self resume];
}

- (void)didSeekToTime:(float)playTime {
  [self.currentPlayer seekToTime:playTime];
}

- (BOOL)cellIsPlaying {
  return self.currentPlayer ? self.currentPlayer.isPlaying : NO;
}

- (void)vodCustomCallbackEvent:(id)info {
  // No custom control events are wired on iOS for now.
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
  if (self.currentPlayer && self.currentPlayer != player) {
    [self.currentPlayer removeDelegate:self];
  }
  self.currentPlayer = player;
  [player addDelegate:self];
  [self.containerView resetVideoSize];
  if ([self.containerView.resizeMode isEqualToString:@"cover"]) {
    [player setRenderMode:TUI_RENDER_MODE_FILL_SCREEN];
  } else if ([self.containerView.resizeMode isEqualToString:@"contain"]) {
    [player setRenderMode:TUI_RENDER_MODE_FILL_EDGE];
  }
  [self emitEvent:RN_EVENT_CONTROLLER_BIND payload:nil];
  [self emitSubtitleTracksIfNeeded];
  [self updateVideoSizeFromPlayer:player];
}

- (void)onUnBindController {
  [self.currentPlayer removeDelegate:self];
  self.currentPlayer = nil;
  [self emitEvent:RN_EVENT_CONTROLLER_UNBIND payload:nil];
  [self.containerView resetVideoSize];
}

- (void)startPlayWithModel:(TUIPlayerVideoModel *)model {
  if (self.currentPlayer) {
    self.currentPlayer.isAutoPlay = [self autoPlayForModel:model];
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

- (void)setRenderMode:(NSInteger)renderMode {
  if (!self.currentPlayer) {
    return;
  }
  TUI_Enum_Type_RenderMode mode =
      (renderMode == 0 || renderMode == 2) ? TUI_RENDER_MODE_FILL_SCREEN : TUI_RENDER_MODE_FILL_EDGE;
  [self.currentPlayer setRenderMode:mode];
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
  [self.currentPlayer setExtentOptionInfo:@{ stringKey : (value ?: @"") }];
}

- (void)releasePlayer {
  [self onShortVideoDestroyed];
}

- (void)onShortVideoDestroyed {
  [self.currentPlayer stopPlay];
  [self.currentPlayer removeDelegate:self];
  self.currentPlayer = nil;
  self.lastSubtitleTracks = nil;
  self.selectedSubtitleTrack = -1;
  [self.containerView hideSubtitleLayer];
  [self.containerView resetVideoSize];
}

- (void)selectSubtitleTrack:(NSInteger)trackIndex {
  TUITXVodPlayer *player = self.currentPlayer;
  if (!player) {
    return;
  }
  self.selectedSubtitleTrack = trackIndex;
  if (trackIndex < 0) {
    NSArray<TXTrackInfo *> *tracks = self.lastSubtitleTracks ?: [player getSubtitleTrackInfo];
    for (TXTrackInfo *info in tracks) {
      [player deselectTrack:info.trackIndex];
    }
    [self.containerView hideSubtitleLayer];
    return;
  }
  [player selectTrack:trackIndex];
}

#pragma mark - TUIPlayerVodManagerDelegate

- (void)currentPlayer:(TUITXVodPlayer *)player {
}

- (void)player:(TUITXVodPlayer *)player willLoadVideoModel:(TUIPlayerVideoModel *)videoModel {
  player.isAutoPlay = [self autoPlayForModel:videoModel];
}

#pragma mark - TUIVodObserver

- (void)onNetStatus:(TUITXVodPlayer *)player withParam:(NSDictionary *)param {
}

- (void)onPlayer:(TUITXVodPlayer *)player subtitleData:(TXVodSubtitleData *)subtitleData {
  if (!subtitleData) {
    return;
  }
  if (self.selectedSubtitleTrack < 0) {
    return;
  }
  if (subtitleData.trackIndex >= 0 && self.selectedSubtitleTrack != subtitleData.trackIndex) {
    return;
  }
  [self.containerView showSubtitleText:subtitleData.subtitleData durationMs:subtitleData.durationMs];
}

- (void)onPlayEvent:(TUITXVodPlayer *)player event:(int)EvtID withParam:(NSDictionary *)param {
  NSMutableDictionary *payload = [NSMutableDictionary dictionaryWithDictionary:param ?: @{}];
  payload[TMP_LAYER_EVENT_KEY] = @(EvtID);
  [self normalizePlayEventPayload:payload];
  [self emitEvent:RN_EVENT_PLAY_EVENT payload:payload];
  if (EvtID == PLAY_EVT_VOD_PLAY_PREPARED || EvtID == PLAY_EVT_PLAY_BEGIN ||
      EvtID == PLAY_EVT_RCV_FIRST_I_FRAME) {
    [self maybeUpdateVideoSizeFromParams:param player:player];
  }
  if (EvtID == PLAY_EVT_VOD_PLAY_PREPARED || EvtID == PLAY_EVT_PLAY_BEGIN) {
    [self emitSubtitleTracksIfNeeded];
  }
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

- (void)normalizePlayEventPayload:(NSMutableDictionary *)payload {
  if (!payload) {
    return;
  }
  NormalizeMsPair(payload, @"EVT_PLAY_PROGRESS_MS", @"EVT_PLAY_PROGRESS");
  NormalizeMsPair(payload, @"EVT_PLAY_DURATION_MS", @"EVT_PLAY_DURATION");
  NormalizeMsPair(payload, @"EVT_PLAYABLE_DURATION_MS", @"EVT_PLAYABLE_DURATION");
  NormalizeMsPair(payload, @"EVT_PLAY_PDT_TIME_MS", @"EVT_PLAY_PDT_TIME");
}

- (void)emitSubtitleTracksIfNeeded {
  TUITXVodPlayer *player = self.currentPlayer;
  if (!player) {
    return;
  }
  NSArray<TXTrackInfo *> *tracks = [player getSubtitleTrackInfo];
  if (tracks.count == 0) {
    self.lastSubtitleTracks = nil;
    self.selectedSubtitleTrack = -1;
    [self.containerView hideSubtitleLayer];
    return;
  }
  if ([self subtitleTracksChanged:tracks]) {
    self.selectedSubtitleTrack = -1;
    [self.containerView hideSubtitleLayer];
  }
  self.lastSubtitleTracks = tracks;
  NSMutableArray *trackArray = [NSMutableArray arrayWithCapacity:tracks.count];
  for (TXTrackInfo *info in tracks) {
    NSMutableDictionary *track = [NSMutableDictionary dictionary];
    track[@"trackIndex"] = @(info.trackIndex);
    if (info.name.length > 0) {
      track[@"name"] = info.name;
    }
    if (info.language.length > 0) {
      track[@"language"] = info.language;
    }
    track[@"type"] = @(info.trackType);
    [trackArray addObject:track];
  }
  [self emitEvent:RN_EVENT_SUBTITLE_TRACKS payload:@{@"tracks" : trackArray}];
  if (self.selectedSubtitleTrack >= 0) {
    [player selectTrack:self.selectedSubtitleTrack];
  }
}

- (BOOL)subtitleTracksChanged:(NSArray<TXTrackInfo *> *)tracks {
  if (!self.lastSubtitleTracks) {
    return YES;
  }
  if (self.lastSubtitleTracks.count != tracks.count) {
    return YES;
  }
  for (NSInteger i = 0; i < tracks.count; i++) {
    TXTrackInfo *a = self.lastSubtitleTracks[i];
    TXTrackInfo *b = tracks[i];
    if (a.trackIndex != b.trackIndex) {
      return YES;
    }
    NSString *aName = a.name ?: @"";
    NSString *bName = b.name ?: @"";
    if (![aName isEqualToString:bName]) {
      return YES;
    }
    NSString *aLang = a.language ?: @"";
    NSString *bLang = b.language ?: @"";
    if (![aLang isEqualToString:bLang]) {
      return YES;
    }
  }
  return NO;
}

@end

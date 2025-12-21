#import <Foundation/Foundation.h>
#import <TUIPlayerShortVideo/TUIPlayerShortVideo-umbrella.h>

NS_ASSUME_NONNULL_BEGIN

@class RNShortVideoView;

@interface RNVodController : NSObject<TUIShortVideoItemViewDelegate, TUIPlayerVodManagerDelegate, TUIVodObserver>

- (instancetype)initWithContainer:(RNShortVideoView *)container;
- (void)onBindController:(TUITXVodPlayer *)player;
- (void)onUnBindController;
- (void)startPlayWithModel:(TUIPlayerVideoModel *)model;
- (void)pause;
- (void)resume;
- (void)setRate:(double)rate;
- (void)setMute:(BOOL)mute;
- (void)seekToTime:(double)time;
- (NSNumber *)duration;
- (NSNumber *)currentPlayTime;
- (NSNumber *)isPlaying;
- (void)setStringOptionWithKey:(id)key value:(NSString *)value;
- (void)releasePlayer;
- (void)onShortVideoDestroyed;
- (void)selectSubtitleTrack:(NSInteger)trackIndex;

@end

NS_ASSUME_NONNULL_END

#import <Foundation/Foundation.h>

#import <TUIPlayerShortVideo/TUIPlayerShortVideo-umbrella.h>

#import "RNShortEngineObserver.h"

NS_ASSUME_NONNULL_BEGIN

@interface RNShortController : NSObject

- (instancetype)initWithControllerId:(NSUInteger)controllerId
                            observer:(id<RNShortEngineObserver>)observer;

- (NSNumber *)setModels:(NSArray<TUIPlayerVideoModel *> *)models;
- (NSNumber *)appendModels:(NSArray<TUIPlayerVideoModel *> *)models;
- (void)setVodStrategy:(TUIPlayerVodStrategyModel *)strategy;
- (NSNumber *)startCurrent;
- (void)bindVideoView:(NSNumber *)viewTag index:(NSInteger)index;
- (void)preBindVideo:(NSNumber *)viewTag index:(NSInteger)index;
- (void)setVideoLoop:(BOOL)isLoop;
- (void)releaseController;

@end

NS_ASSUME_NONNULL_END

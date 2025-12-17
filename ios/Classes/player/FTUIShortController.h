#import <Foundation/Foundation.h>

#import <TUIPlayerShortVideo/TUIPlayerShortVideo-umbrella.h>

#import "FTUIShortEngineObserver.h"

NS_ASSUME_NONNULL_BEGIN

@interface FTUIShortController : NSObject

- (instancetype)initWithControllerId:(NSUInteger)controllerId
                            observer:(id<FTUIShortEngineObserver>)observer;

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

#import <UIKit/UIKit.h>

@class RNVodController;
@class TUIShortVideoItemView;

NS_ASSUME_NONNULL_BEGIN

@interface RNShortVideoView : UIView

@property(nonatomic, strong, readonly) RNVodController *vodController;
@property(nonatomic, strong, readonly) TUIShortVideoItemView *itemView;
@property(nonatomic, copy) NSString *resizeMode;
@property(nonatomic, assign) CGFloat videoWidth;
@property(nonatomic, assign) CGFloat videoHeight;
@property(nonatomic, assign, readonly) BOOL hasItemView;

- (void)updateVideoSize:(NSInteger)width height:(NSInteger)height;
- (void)resetVideoSize;
- (void)showSubtitleText:(NSString *)text durationMs:(int64_t)durationMs;
- (void)hideSubtitleLayer;
- (void)ensureItemView;

- (void)dispose;

@end

NS_ASSUME_NONNULL_END

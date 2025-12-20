#import <UIKit/UIKit.h>

@class RNVodController;
@class TUIShortVideoItemView;

NS_ASSUME_NONNULL_BEGIN

@interface RNShortVideoView : UIView

@property(nonatomic, strong, readonly) RNVodController *vodController;
@property(nonatomic, strong, readonly) TUIShortVideoItemView *itemView;

- (void)dispose;

@end

NS_ASSUME_NONNULL_END

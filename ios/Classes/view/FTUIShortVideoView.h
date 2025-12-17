#import <UIKit/UIKit.h>

@class FTUIVodController;
@class TUIShortVideoItemView;

NS_ASSUME_NONNULL_BEGIN

@interface FTUIShortVideoView : UIView

@property(nonatomic, strong, readonly) FTUIVodController *vodController;
@property(nonatomic, strong, readonly) TUIShortVideoItemView *itemView;

- (void)dispose;

@end

NS_ASSUME_NONNULL_END

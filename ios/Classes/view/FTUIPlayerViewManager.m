#import "FTUIPlayerViewManager.h"

#import "FTUIShortVideoView.h"

@implementation FTUIPlayerViewManager

RCT_EXPORT_MODULE(TUIShortVideoItemView)

- (UIView *)view {
  return [[FTUIShortVideoView alloc] initWithFrame:CGRectZero];
}

@end

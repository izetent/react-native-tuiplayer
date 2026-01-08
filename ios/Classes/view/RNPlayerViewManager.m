#import "RNPlayerViewManager.h"

#import "RNShortVideoView.h"

@implementation RNPlayerViewManager

RCT_EXPORT_MODULE(TUIShortVideoItemView)

- (UIView *)view {
  return [[RNShortVideoView alloc] initWithFrame:CGRectZero];
}

@end

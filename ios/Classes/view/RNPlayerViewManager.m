#import "RNPlayerViewManager.h"

#import "RNShortVideoView.h"

@implementation RNPlayerViewManager

RCT_EXPORT_MODULE(TUIShortVideoItemView)

RCT_EXPORT_VIEW_PROPERTY(resizeMode, NSString)
RCT_EXPORT_VIEW_PROPERTY(videoWidth, CGFloat)
RCT_EXPORT_VIEW_PROPERTY(videoHeight, CGFloat)

- (UIView *)view {
  return [[RNShortVideoView alloc] initWithFrame:CGRectZero];
}

@end

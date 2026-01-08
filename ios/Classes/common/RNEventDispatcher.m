#import "RNEventDispatcher.h"

static __weak RCTBridge *_rnBridge = nil;

@implementation RNEventDispatcher

+ (void)setupWithBridge:(RCTBridge *)bridge {
  _rnBridge = bridge;
}

+ (void)sendEvent:(NSString *)event body:(NSDictionary *)body {
  RCTBridge *bridge = _rnBridge;
  if (bridge == nil || event == nil) {
    return;
  }
  id payload = body ?: @{};
  [bridge enqueueJSCall:@"RCTDeviceEventEmitter"
                method:@"emit"
                  args:@[event, payload]
            completion:NULL];
}

@end

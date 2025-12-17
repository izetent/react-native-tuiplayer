#import "FTUIEventDispatcher.h"

static __weak RCTBridge *_ftuiBridge = nil;

@implementation FTUIEventDispatcher

+ (void)setupWithBridge:(RCTBridge *)bridge {
  _ftuiBridge = bridge;
}

+ (void)sendEvent:(NSString *)event body:(NSDictionary *)body {
  RCTBridge *bridge = _ftuiBridge;
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

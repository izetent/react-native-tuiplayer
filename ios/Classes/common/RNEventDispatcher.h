#import <React/RCTBridge.h>

NS_ASSUME_NONNULL_BEGIN

@interface RNEventDispatcher : NSObject

+ (void)setupWithBridge:(RCTBridge *)bridge;
+ (void)sendEvent:(NSString *)event body:(NSDictionary *)body;

@end

NS_ASSUME_NONNULL_END

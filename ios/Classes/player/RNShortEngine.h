#import <Foundation/Foundation.h>

#import "RNShortController.h"
#import "RNShortEngineObserver.h"

NS_ASSUME_NONNULL_BEGIN

@interface RNShortEngine : NSObject<RNShortEngineObserver>

- (NSNumber *)createShortController;
- (RNShortController *_Nullable)controllerForId:(NSNumber *)controllerId;
- (void)setConfig:(NSDictionary *)config;
- (void)setMonetAppInfoWithAppId:(NSInteger)appId
                           authId:(NSInteger)authId
                srAlgorithmType:(NSInteger)srAlgorithmType;

@end

NS_ASSUME_NONNULL_END

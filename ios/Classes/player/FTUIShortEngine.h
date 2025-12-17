#import <Foundation/Foundation.h>

#import "FTUIShortController.h"
#import "FTUIShortEngineObserver.h"

NS_ASSUME_NONNULL_BEGIN

@interface FTUIShortEngine : NSObject<FTUIShortEngineObserver>

- (NSNumber *)createShortController;
- (FTUIShortController *_Nullable)controllerForId:(NSNumber *)controllerId;
- (void)setConfig:(NSDictionary *)config;
- (void)setMonetAppInfoWithAppId:(NSInteger)appId
                           authId:(NSInteger)authId
                srAlgorithmType:(NSInteger)srAlgorithmType;

@end

NS_ASSUME_NONNULL_END

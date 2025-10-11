#import "Tuiplayer.h"
#import "react-native-tuiplayer-Swift.h"

@implementation Tuiplayer
- (void)initialize:(NSDictionary *)config
{
  [TuiplayerModule.shared initializeWith:config];
}

- (std::shared_ptr<facebook::react::TurboModule>)getTurboModule:
    (const facebook::react::ObjCTurboModule::InitParams &)params
{
    return std::make_shared<facebook::react::NativeTuiplayerSpecJSI>(params);
}

+ (NSString *)moduleName
{
  return @"Tuiplayer";
}

@end

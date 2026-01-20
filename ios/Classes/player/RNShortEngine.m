#import "RNShortEngine.h"

#import <TUIPlayerCore/TUIPlayerCore-umbrella.h>
#import "RNConstant.h"

static NSInteger rn_controller_seed = 0;

@interface RNShortEngine ()

@property(nonatomic, strong) NSMutableDictionary<NSNumber *, RNShortController *> *controllers;

@end

@implementation RNShortEngine

- (instancetype)init {
  self = [super init];
  if (self) {
    _controllers = [NSMutableDictionary dictionary];
  }
  return self;
}

- (NSNumber *)createShortController {
  @synchronized(self.controllers) {
    NSNumber *controllerId = @(rn_controller_seed++);
    RNShortController *controller =
        [[RNShortController alloc] initWithControllerId:controllerId.unsignedIntegerValue
                                                observer:self];
    self.controllers[controllerId] = controller;
    return controllerId;
  }
}

- (RNShortController *)controllerForId:(NSNumber *)controllerId {
  @synchronized(self.controllers) {
    return self.controllers[controllerId];
  }
}

- (void)setConfig:(NSDictionary *)config {
  TUIPlayerConfig *playerConfig = [TUIPlayerConfig new];
  NSNumber *enableLog = config[@"enableLog"];
  playerConfig.enableLog = enableLog != nil ? enableLog.boolValue : YES;
  [[TUIPlayerCore shareInstance] setPlayerConfig:playerConfig];
  NSString *licenseUrl = config[@"licenseUrl"];
  NSString *licenseKey = config[@"licenseKey"];
  if (licenseUrl.length > 0 && licenseKey.length > 0) {
    [TXLiveBase setLicenceURL:licenseUrl key:licenseKey];
  }
}

- (void)setMonetAppInfoWithAppId:(NSInteger)appId
                           authId:(NSInteger)authId
                srAlgorithmType:(NSInteger)srAlgorithmType {
  // Super resolution/Monet disabled for now to avoid TSR dependency.
  NSLog(@"[react-native-tuiplayer] setMonetAppInfo ignored: super resolution is disabled in this build.");
}

- (void)onRelease:(NSUInteger)controllerId {
  @synchronized(self.controllers) {
    [self.controllers removeObjectForKey:@(controllerId)];
  }
}

@end

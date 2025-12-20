#import <Foundation/Foundation.h>

@class RNShortVideoView;

NS_ASSUME_NONNULL_BEGIN

@interface RNViewRegistry : NSObject

+ (void)registerView:(RNShortVideoView *)view forTag:(NSNumber *)tag;
+ (void)unregisterViewForTag:(NSNumber *)tag;
+ (RNShortVideoView *_Nullable)viewForTag:(NSNumber *)tag;

@end

NS_ASSUME_NONNULL_END

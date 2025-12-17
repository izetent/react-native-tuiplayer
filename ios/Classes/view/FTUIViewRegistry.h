#import <Foundation/Foundation.h>

@class FTUIShortVideoView;

NS_ASSUME_NONNULL_BEGIN

@interface FTUIViewRegistry : NSObject

+ (void)registerView:(FTUIShortVideoView *)view forTag:(NSNumber *)tag;
+ (void)unregisterViewForTag:(NSNumber *)tag;
+ (FTUIShortVideoView *_Nullable)viewForTag:(NSNumber *)tag;

@end

NS_ASSUME_NONNULL_END

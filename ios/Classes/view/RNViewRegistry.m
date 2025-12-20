#import "RNViewRegistry.h"
#import "RNShortVideoView.h"

static NSMapTable<NSNumber *, RNShortVideoView *> *rn_view_table;

@implementation RNViewRegistry

+ (void)initialize {
  if (self == [RNViewRegistry class]) {
    rn_view_table = [NSMapTable strongToWeakObjectsMapTable];
  }
}

+ (void)registerView:(RNShortVideoView *)view forTag:(NSNumber *)tag {
  if (view && tag) {
    @synchronized (rn_view_table) {
      [rn_view_table setObject:view forKey:tag];
    }
  }
}

+ (void)unregisterViewForTag:(NSNumber *)tag {
  if (tag) {
    @synchronized (rn_view_table) {
      [rn_view_table removeObjectForKey:tag];
    }
  }
}

+ (RNShortVideoView *)viewForTag:(NSNumber *)tag {
  if (!tag) {
    return nil;
  }
  @synchronized (rn_view_table) {
    return [rn_view_table objectForKey:tag];
  }
}

@end

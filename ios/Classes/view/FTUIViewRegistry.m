#import "FTUIViewRegistry.h"
#import "FTUIShortVideoView.h"

static NSMapTable<NSNumber *, FTUIShortVideoView *> *ftui_view_table;

@implementation FTUIViewRegistry

+ (void)initialize {
  if (self == [FTUIViewRegistry class]) {
    ftui_view_table = [NSMapTable strongToWeakObjectsMapTable];
  }
}

+ (void)registerView:(FTUIShortVideoView *)view forTag:(NSNumber *)tag {
  if (view && tag) {
    @synchronized (ftui_view_table) {
      [ftui_view_table setObject:view forKey:tag];
    }
  }
}

+ (void)unregisterViewForTag:(NSNumber *)tag {
  if (tag) {
    @synchronized (ftui_view_table) {
      [ftui_view_table removeObjectForKey:tag];
    }
  }
}

+ (FTUIShortVideoView *)viewForTag:(NSNumber *)tag {
  if (!tag) {
    return nil;
  }
  @synchronized (ftui_view_table) {
    return [ftui_view_table objectForKey:tag];
  }
}

@end

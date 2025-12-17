#import "FTUIShortVideoView.h"

#import <React/UIView+React.h>
#import <TUIPlayerShortVideo/TUIShortVideoItemView.h>

#import "FTUIConstant.h"
#import "FTUIEventDispatcher.h"
#import "FTUIVodController.h"
#import "FTUIViewRegistry.h"

@interface FTUIShortVideoView ()

@property(nonatomic, strong) TUIShortVideoItemView *itemView;
@property(nonatomic, strong, readwrite) FTUIVodController *vodController;
@property(nonatomic, assign) BOOL disposed;

@end

@implementation FTUIShortVideoView

- (instancetype)initWithFrame:(CGRect)frame {
  self = [super initWithFrame:frame];
  if (self) {
    [self setupView];
  }
  return self;
}

- (instancetype)initWithCoder:(NSCoder *)coder {
  self = [super initWithCoder:coder];
  if (self) {
    [self setupView];
  }
  return self;
}

- (void)setupView {
  self.clipsToBounds = YES;
  self.itemView =
      [[TUIShortVideoItemView alloc] initWithFrame:self.bounds renderViewType:TUIRenderViewTypeTextureView];
  self.itemView.userInteractionEnabled = NO;
  self.vodController = [[FTUIVodController alloc] initWithContainer:self];
  [self.itemView addVideoItemViewListener:self.vodController];
  [self.itemView createDisplayView];
  [self addSubview:self.itemView];
}

- (void)layoutSubviews {
  [super layoutSubviews];
  self.itemView.frame = self.bounds;
}

- (void)setReactTag:(NSNumber *)reactTag {
  [super setReactTag:reactTag];
  [FTUIViewRegistry registerView:self forTag:reactTag];
}

- (void)dispose {
  if (self.disposed) {
    return;
  }
  self.disposed = YES;
  [self.vodController onShortVideoDestroyed];
  [self.itemView onViewDestroyed];
  NSNumber *tag = self.reactTag;
  if (tag) {
    [FTUIViewRegistry unregisterViewForTag:tag];
    [FTUIEventDispatcher sendEvent:FTUI_EVENT_VIEW_DISPOSED body:@{@"viewTag" : tag}];
  }
}

- (void)removeFromSuperview {
  [self dispose];
  [super removeFromSuperview];
}

- (void)dealloc {
  [self dispose];
}

@end

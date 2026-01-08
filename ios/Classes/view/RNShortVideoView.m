#import "RNShortVideoView.h"

#import <React/UIView+React.h>
#import <TUIPlayerShortVideo/TUIShortVideoItemView.h>

#import "RNConstant.h"
#import "RNEventDispatcher.h"
#import "RNVodController.h"
#import "RNViewRegistry.h"

@interface RNShortVideoView ()

@property(nonatomic, strong) TUIShortVideoItemView *itemView;
@property(nonatomic, strong, readwrite) RNVodController *vodController;
@property(nonatomic, assign) BOOL disposed;

@end

@implementation RNShortVideoView

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
  self.vodController = [[RNVodController alloc] initWithContainer:self];
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
  [RNViewRegistry registerView:self forTag:reactTag];
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
    [RNViewRegistry unregisterViewForTag:tag];
    [RNEventDispatcher sendEvent:RN_EVENT_VIEW_DISPOSED body:@{@"viewTag" : tag}];
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

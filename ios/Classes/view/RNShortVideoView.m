#import "RNShortVideoView.h"

#import <React/UIView+React.h>
#import <TUIPlayerShortVideo/TUIPlayerShortVideoUIManager.h>
#import <TUIPlayerShortVideo/TUIShortVideoItemView.h>
#import <TUIPlayerShortVideo/TUITableView.h>

#import "RNConstant.h"
#import "RNEventDispatcher.h"
#import "RNVodController.h"
#import "RNViewRegistry.h"

@interface RNShortVideoView () <UITableViewDataSource, UITableViewDelegate>

@property(nonatomic, strong) TUIShortVideoItemView *itemView;
@property(nonatomic, strong, readwrite) RNVodController *vodController;
@property(nonatomic, strong) TUIPlayerShortVideoUIManager *uiManager;
@property(nonatomic, strong) TUITableView *tableView;
@property(nonatomic, strong) UILabel *subtitleLabel;
@property(nonatomic, assign) NSUInteger subtitleToken;
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
  self.resizeMode = @"contain";
  self.uiManager = [[TUIPlayerShortVideoUIManager alloc] init];
  self.tableView = [[TUITableView alloc] initWithFrame:self.bounds style:UITableViewStylePlain];
  self.tableView.dataSource = self;
  self.tableView.delegate = self;
  self.tableView.scrollEnabled = NO;
  self.tableView.separatorStyle = UITableViewCellSeparatorStyleNone;
  self.tableView.backgroundColor = [UIColor clearColor];
  [self addSubview:self.tableView];

  self.vodController = [[RNVodController alloc] initWithContainer:self];
  [self.tableView reloadData];
  [self.tableView layoutIfNeeded];

  self.subtitleLabel = [[UILabel alloc] initWithFrame:CGRectZero];
  self.subtitleLabel.textColor = [UIColor whiteColor];
  self.subtitleLabel.backgroundColor = [[UIColor blackColor] colorWithAlphaComponent:0.5];
  self.subtitleLabel.font = [UIFont systemFontOfSize:14.0 weight:UIFontWeightSemibold];
  self.subtitleLabel.shadowColor = [[UIColor blackColor] colorWithAlphaComponent:0.7];
  self.subtitleLabel.shadowOffset = CGSizeMake(0, 1);
  self.subtitleLabel.textAlignment = NSTextAlignmentCenter;
  self.subtitleLabel.numberOfLines = 0;
  self.subtitleLabel.hidden = YES;
  self.subtitleLabel.layer.cornerRadius = 6.0;
  self.subtitleLabel.layer.masksToBounds = YES;
  [self addSubview:self.subtitleLabel];
}

- (void)layoutSubviews {
  [super layoutSubviews];
  self.tableView.frame = self.bounds;
  self.tableView.rowHeight = self.bounds.size.height;
  [self applyContainerLayout];
  [self layoutSubtitleLabel];
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
  self.itemView.delegate = nil;
  self.tableView.dataSource = nil;
  self.tableView.delegate = nil;
  self.subtitleLabel.hidden = YES;
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

#pragma mark - UITableViewDataSource

- (NSInteger)tableView:(UITableView *)tableView numberOfRowsInSection:(NSInteger)section {
  return 1;
}

- (UITableViewCell *)tableView:(UITableView *)tableView
         cellForRowAtIndexPath:(NSIndexPath *)indexPath {
  if (self.itemView == nil) {
    self.itemView = [TUIShortVideoItemView tableView:tableView
                               cellForRowAtIndexPath:indexPath
                                           uiManager:self.uiManager];
    self.itemView.userInteractionEnabled = NO;
    self.itemView.delegate = self.vodController;
    [self applyRenderMode];
    [self applyContainerLayout];
  }
  return self.itemView;
}

#pragma mark - UITableViewDelegate

- (CGFloat)tableView:(UITableView *)tableView heightForRowAtIndexPath:(NSIndexPath *)indexPath {
  return self.bounds.size.height;
}

#pragma mark - Props

- (void)setResizeMode:(NSString *)resizeMode {
  _resizeMode = [resizeMode copy];
  [self applyRenderMode];
  [self applyContainerLayout];
}

- (void)setVideoWidth:(CGFloat)videoWidth {
  _videoWidth = videoWidth;
  [self applyContainerLayout];
}

- (void)setVideoHeight:(CGFloat)videoHeight {
  _videoHeight = videoHeight;
  [self applyContainerLayout];
}

- (void)applyRenderMode {
  if (!self.vodController) {
    return;
  }
  if (_resizeMode.length == 0) {
    _resizeMode = @"contain";
  }
  if ([_resizeMode isEqualToString:@"cover"]) {
    [self.vodController setRenderMode:TUI_RENDER_MODE_FILL_SCREEN];
  } else if ([_resizeMode isEqualToString:@"contain"]) {
    [self.vodController setRenderMode:TUI_RENDER_MODE_FILL_EDGE];
  }
}

- (void)applyContainerLayout {
  if (!self.itemView || !self.itemView.videoBaseView || !self.itemView.videoBaseView.videoContainerView) {
    return;
  }
  UIView *container = self.itemView.videoBaseView.videoContainerView;
  CGRect bounds = self.bounds;
  if (self.videoWidth <= 0 || self.videoHeight <= 0) {
    container.frame = bounds;
    return;
  }
  CGFloat videoAspect = self.videoWidth / self.videoHeight;
  CGFloat viewAspect = bounds.size.width / MAX(bounds.size.height, 1.0);
  BOOL cover = [_resizeMode isEqualToString:@"cover"];
  CGFloat targetWidth = bounds.size.width;
  CGFloat targetHeight = bounds.size.height;
  if (cover) {
    if (videoAspect > viewAspect) {
      // fill height, crop width
      targetHeight = bounds.size.height;
      targetWidth = targetHeight * videoAspect;
    } else {
      // fill width, crop height
      targetWidth = bounds.size.width;
      targetHeight = targetWidth / videoAspect;
    }
  } else {
    if (videoAspect > viewAspect) {
      // fit width, letterbox height
      targetWidth = bounds.size.width;
      targetHeight = targetWidth / videoAspect;
    } else {
      // fit height, letterbox width
      targetHeight = bounds.size.height;
      targetWidth = targetHeight * videoAspect;
    }
  }
  CGFloat originX = CGRectGetMidX(bounds) - targetWidth / 2.0;
  CGFloat originY = CGRectGetMidY(bounds) - targetHeight / 2.0;
  container.frame = CGRectMake(originX, originY, targetWidth, targetHeight);
}

- (void)updateVideoSize:(NSInteger)width height:(NSInteger)height {
  if (width <= 0 || height <= 0) {
    return;
  }
  if ([NSThread isMainThread]) {
    _videoWidth = (CGFloat)width;
    _videoHeight = (CGFloat)height;
    [self applyContainerLayout];
  } else {
    dispatch_async(dispatch_get_main_queue(), ^{
      self->_videoWidth = (CGFloat)width;
      self->_videoHeight = (CGFloat)height;
      [self applyContainerLayout];
    });
  }
}

- (void)resetVideoSize {
  if ([NSThread isMainThread]) {
    _videoWidth = 0;
    _videoHeight = 0;
    [self applyContainerLayout];
  } else {
    dispatch_async(dispatch_get_main_queue(), ^{
      self->_videoWidth = 0;
      self->_videoHeight = 0;
      [self applyContainerLayout];
    });
  }
}

- (void)layoutSubtitleLabel {
  if (!self.subtitleLabel || self.subtitleLabel.hidden) {
    return;
  }
  CGFloat maxWidth = CGRectGetWidth(self.bounds) - 32.0;
  CGSize fit = [self.subtitleLabel sizeThatFits:CGSizeMake(maxWidth, CGFLOAT_MAX)];
  CGFloat width = MIN(maxWidth, fit.width + 16.0);
  CGFloat height = fit.height + 12.0;
  CGFloat x = (CGRectGetWidth(self.bounds) - width) / 2.0;
  CGFloat safeBottom = 0.0;
  if (@available(iOS 11.0, *)) {
    safeBottom = self.safeAreaInsets.bottom;
  }
  CGFloat y = CGRectGetHeight(self.bounds) - height - 24.0 - safeBottom;
  self.subtitleLabel.frame = CGRectMake(x, y, width, height);
}

- (void)showSubtitleText:(NSString *)text durationMs:(int64_t)durationMs {
  if (text.length == 0) {
    [self hideSubtitleLayer];
    return;
  }
  dispatch_async(dispatch_get_main_queue(), ^{
    self.subtitleToken += 1;
    NSUInteger token = self.subtitleToken;
    self.subtitleLabel.text = text;
    self.subtitleLabel.hidden = NO;
    [self setNeedsLayout];
    [self layoutIfNeeded];
    if (durationMs > 0) {
      dispatch_after(dispatch_time(DISPATCH_TIME_NOW, (int64_t)(durationMs * NSEC_PER_MSEC)),
                     dispatch_get_main_queue(), ^{
                       if (self.subtitleToken == token) {
                         [self hideSubtitleLayer];
                       }
                     });
    }
  });
}

- (void)hideSubtitleLayer {
  dispatch_async(dispatch_get_main_queue(), ^{
    self.subtitleToken += 1;
    self.subtitleLabel.text = @"";
    self.subtitleLabel.hidden = YES;
  });
}

- (void)ensureItemView {
  if (self.itemView != nil) {
    return;
  }
  NSIndexPath *indexPath = [NSIndexPath indexPathForRow:0 inSection:0];
  [self tableView:self.tableView cellForRowAtIndexPath:indexPath];
  [self.tableView reloadData];
}

- (BOOL)hasItemView {
  return self.itemView != nil;
}

@end

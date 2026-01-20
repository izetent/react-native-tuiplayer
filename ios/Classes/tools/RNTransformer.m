#import "RNTransformer.h"

@implementation RNTransformer

+ (NSArray<TUIPlayerVideoModel *> *)videoModelsFromArray:(NSArray<NSDictionary *> *)array {
  NSMutableArray *models = [NSMutableArray arrayWithCapacity:array.count];
  for (NSDictionary *dict in array) {
    TUIPlayerVideoModel *model = [self videoModelFromDictionary:dict];
    if (model) {
      [models addObject:model];
    }
  }
  return models;
}

+ (TUIPlayerVideoModel *)videoModelFromDictionary:(NSDictionary *)dict {
  TUIPlayerVideoModel *model = [[TUIPlayerVideoModel alloc] init];
  NSString *videoURL = dict[@"videoURL"];
  if (videoURL.length > 0) {
    model.videoURL = videoURL;
  }
  NSNumber *appId = dict[@"appId"];
  if (appId != nil) {
    model.appId = appId.intValue;
  }
  NSString *fileId = dict[@"fileId"];
  if (fileId.length > 0) {
    model.fileId = fileId;
  }
  NSString *pSign = dict[@"pSign"];
  if (pSign.length > 0) {
    model.pSign = pSign;
  }
  NSString *cover = dict[@"coverPictureUrl"];
  if (cover.length > 0) {
    model.coverPictureUrl = cover;
  }
  NSNumber *autoPlay = dict[@"isAutoPlay"];
  model.autoPlay = autoPlay != nil ? autoPlay.boolValue : YES;
  NSDictionary *extInfo = dict[@"extInfo"];
  if ([extInfo isKindOfClass:[NSDictionary class]]) {
    model.extInfo = extInfo;
  }
  return model;
}

+ (TUIPlayerVodStrategyModel *)strategyFromDictionary:(NSDictionary *)dict {
  TUIPlayerVodStrategyModel *strategy = [[TUIPlayerVodStrategyModel alloc] init];
  NSNumber *preloadCount = dict[@"preloadCount"];
  if (preloadCount) {
    strategy.preloadCount = preloadCount.intValue;
  }
  NSNumber *downloadSize = dict[@"preDownloadSize"];
  if (downloadSize) {
    strategy.preDownloadSize = downloadSize.doubleValue;
  }
  NSNumber *preloadBufferSize = dict[@"preloadBufferSizeInMB"];
  if (preloadBufferSize) {
    strategy.preloadBufferSizeInMB = preloadBufferSize.doubleValue;
  }
  NSNumber *maxBuffer = dict[@"maxBufferSize"];
  if (maxBuffer) {
    strategy.maxBufferSize = maxBuffer.doubleValue;
  }
  NSNumber *preferredResolution = dict[@"preferredResolution"];
  if (preferredResolution) {
    strategy.preferredResolution = preferredResolution.integerValue;
  }
  NSNumber *progressInterval = dict[@"progressInterval"];
  if (progressInterval) {
    strategy.progressInterval = progressInterval.intValue;
  }
  NSNumber *renderMode = dict[@"renderMode"];
  if (renderMode) {
    strategy.renderMode = renderMode.integerValue;
  }
  // Super resolution disabled for now to avoid TSR dependency.
  strategy.enableSuperResolution = NO;
  return strategy;
}

@end

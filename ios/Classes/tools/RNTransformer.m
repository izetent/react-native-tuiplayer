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
    model.videoUrl = videoURL;
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
  BOOL hasAutoPlay = [autoPlay isKindOfClass:[NSNumber class]];
  BOOL isAutoPlay = hasAutoPlay ? autoPlay.boolValue : YES;
  id extInfo = dict[@"extInfo"];
  if (extInfo == [NSNull null]) {
    extInfo = nil;
  }
  if ([extInfo isKindOfClass:[NSDictionary class]]) {
    NSMutableDictionary *merged = [((NSDictionary *)extInfo) mutableCopy];
    if (hasAutoPlay) {
      merged[@"isAutoPlay"] = @(isAutoPlay);
    }
    model.extInfo = [merged copy];
    [model extInfoChangeNotify];
  } else if (extInfo != nil) {
    model.extInfo = extInfo;
    [model extInfoChangeNotify];
  } else if (hasAutoPlay) {
    model.extInfo = @{@"isAutoPlay" : @(isAutoPlay)};
    [model extInfoChangeNotify];
  }
  NSArray *subtitleSources = dict[@"subtitleSources"];
  if ([subtitleSources isKindOfClass:[NSArray class]] && subtitleSources.count > 0) {
    NSMutableArray<TUIPlayerSubtitleModel *> *subtitles = [NSMutableArray array];
    for (id entry in subtitleSources) {
      if (![entry isKindOfClass:[NSDictionary class]]) {
        continue;
      }
      NSString *url = ((NSDictionary *)entry)[@"url"];
      if (url.length == 0) {
        continue;
      }
      TUIPlayerSubtitleModel *subtitle = [[TUIPlayerSubtitleModel alloc] init];
      subtitle.url = url;
      NSString *name = ((NSDictionary *)entry)[@"name"];
      if (name.length > 0) {
        subtitle.name = name;
      }
      NSString *mimeType = ((NSDictionary *)entry)[@"mimeType"];
      NSString *lowerMime = [mimeType.lowercaseString stringByTrimmingCharactersInSet:[NSCharacterSet whitespaceAndNewlineCharacterSet]];
      subtitle.mimeType = [lowerMime containsString:@"vtt"]
                              ? TUI_VOD_PLAYER_MIMETYPE_TEXT_VTT
                              : TUI_VOD_PLAYER_MIMETYPE_TEXT_SRT;
      [subtitles addObject:subtitle];
    }
    if (subtitles.count > 0) {
      model.subtitles = subtitles;
    }
  }
  return model;
}

+ (TUIPlayerVodStrategyModel *)strategyFromDictionary:(NSDictionary *)dict {
  TUIPlayerVodStrategyModel *strategy = [[TUIPlayerVodStrategyModel alloc] init];
  NSNumber *preloadCount = dict[@"preloadCount"];
  if (preloadCount) {
    strategy.mPreloadConcurrentCount = preloadCount.intValue;
  }
  NSNumber *downloadSize = dict[@"preDownloadSize"];
  if (downloadSize) {
    strategy.preDownloadSize = downloadSize.doubleValue;
  }
  NSNumber *preloadBufferSize = dict[@"preloadBufferSizeInMB"];
  if (preloadBufferSize) {
    strategy.mPreloadBufferSizeInMB = preloadBufferSize.doubleValue;
  }
  NSNumber *maxBuffer = dict[@"maxBufferSize"];
  if (maxBuffer) {
    strategy.maxBufferSize = maxBuffer.doubleValue;
  }
  NSNumber *preferredResolution = dict[@"preferredResolution"];
  if (preferredResolution) {
    strategy.mPreferredResolution = preferredResolution.integerValue;
  }
  NSNumber *progressInterval = dict[@"progressInterval"];
  if (progressInterval) {
    strategy.mProgressInterval = progressInterval.intValue;
  }
  NSNumber *renderMode = dict[@"renderMode"];
  if (renderMode) {
    strategy.mRenderMode = (TUI_Enum_Type_RenderMode)renderMode.integerValue;
  }
  // Super resolution disabled for now to avoid TSR dependency.
  strategy.superResolutionType = 0;
  return strategy;
}

@end

//
//  RNShortController.m
//  rnplayer_kit
//
//  Created by kongdywang on 21.8.24.
//

#import "RNShortController.h"
#import <TUIPlayerCore/TUIPlayerCore-umbrella.h>
#import "RNConstant.h"
#import "RNViewRegistry.h"
#import "RNVodController.h"
#import "RNShortVideoView.h"

@interface RNShortController()

@property (nonatomic, strong) TUIPlayerVodStrategyManager *vodStrategyManager;///策略管理
@property (nonatomic, strong) TUIPlayerVodManager* vodManager;
@property (nonatomic, strong) TUIPlayerVodPreLoadManager* preloadManager;
@property (nonatomic, strong) TUIPlayerRecordManager* recordManager;
@property (nonatomic, strong) NSMutableArray* dataArray;
@property (nonatomic, assign) long currentIndex;
@property (nonatomic, assign) long preIndex;
@property (nonatomic, strong) RNVodController *curVodController;

@property (nonatomic, strong) id<RNShortEngineObserver> engineObserver;
@property (nonatomic, assign) NSUInteger controllerId;

@property (nonatomic, assign) long cachePageIndex;
@property (nonatomic, assign) long cacheIndex;

@end

@implementation RNShortController

- (nonnull instancetype)initWithControllerId:(NSUInteger)controllerId observer:(nonnull id<RNShortEngineObserver>)observer {
    self = [super init];
    TUILOGI(@"start create shortController,controllerId %lu", controllerId)
    if (self) {
        self.vodStrategyManager = [[TUIPlayerVodStrategyManager alloc] init];
        self.dataArray = @[].mutableCopy;
        self.currentIndex = -1;
        self.preIndex = -1;
        self.engineObserver = observer;
        self.controllerId = controllerId;
        self.curVodController = nil;
        self.cachePageIndex = -1;
        self.cacheIndex = -1;
    }
    return self;
}

///清除点播播放器缓存
- (void)removeVodPlayerCache:(long)curIndex preIndex:(long)preIndex {
    @synchronized (self.dataArray) {
        TUILOGI(@"[removeVodPlayerCache] currentIndex:%ld", curIndex)
        bool isDonwFlip = (curIndex - preIndex) >= 0;
        ///  flutter 端的 viewPager，由于会先 itemBuilder，所以会先触发预播放，然后再触发当前页面的 bind，
        ///  由于 IOS 取播放器的时候，不会触发 LRU 的顺序更新，所以三播放器的情况下，会导致next 的播放器经常无法获取到
        ///如果开启了预播放上一个，那么上一个的播放器保持，只清除上上个播放器缓存
        ///如果没有开启，那么上一个播放器缓存及时清除
        long prePreIndex =  curIndex - 2;
        long previousIndex = curIndex - 1;
        long nextIndex = curIndex + 1;
        long nextNextIndex = curIndex + 2;
        [self removeVodPlayerCacheByIndex:curIndex - 3];
        [self removeVodPlayerCacheByIndex:prePreIndex];
        [self removeVodPlayerCacheByIndex:nextNextIndex];
        if (isDonwFlip) {
            [self removeVodPlayerCacheByIndex:previousIndex];
        } else {
            [self removeVodPlayerCacheByIndex:nextIndex];
        }
    }
}

-(void)removeVodPlayerCacheByIndex:(long)removeIndex {
    NSUInteger dataCount = self.dataArray.count;
    TUIPlayerDataModel *removeModel = nil;
    if (dataCount > removeIndex && removeIndex >= 0) {
        removeModel = self.dataArray[removeIndex];
    }
    if (removeModel && removeModel.modelType == TUI_MODEL_TYPE_VOD) {
        [self.vodManager removePlayerCache:removeModel.asVodModel];
    }
}

- (void)bindVideoView:(NSInteger)pageViewId index:(NSInteger)index isPreBind:(BOOL)isPreBind {
    NSUInteger dataCount = self.dataArray.count;
    if (index < 0 || index >= dataCount) {
        TUILOGE(@"bindVideoView failed, index outOfRange,index:%lu, isPreBind:%d", index, isPreBind)
        return;
    }
    RNShortVideoView *itemView = [RNViewRegistry viewForTag:@(pageViewId)];
    if (nil != itemView) {
        [itemView ensureItemView];
        TUIPlayerDataModel *dataModel = self.dataArray[index];
        if ([dataModel isKindOfClass:[TUIPlayerVideoModel class]]) {
            TUIPlayerVideoModel* vodModel = [dataModel asVodModel];
            [self.vodManager.vodPreLoadManager cancelPreLoadOperationWith:vodModel];
            __block TUIShortVideoItemView* videoItemView = itemView.itemView;
            [videoItemView setItemViewModel:vodModel];
            if (isPreBind) {
                TUILOGI(@"start preRender index:%lu", index)
                if (self.currentIndex == index && [self.vodManager currentVodPlayer]) {
                    TUITXVodPlayer *player = [self.vodManager currentVodPlayer];
                    if (player.status < TUITXVodPlayerStatusPrepared || player.status > TUITXVodPlayerStatusEnded) {
                        TUILOGW(@"prePlay a idle player,index:%lu", index)
                        [self.vodManager prePlayWithModel:vodModel type:1];

                        [self.vodManager setVideoWidget:videoItemView.videoBaseView.videoContainerView model:vodModel
                                     firstFrameCallBack:^(BOOL isFirstFrame) {
                            [videoItemView hiddenCoverImage:YES];
                        }];
                    } else {
                        TUILOGW(@"video is playing, jump prePlay opt,index:%lu", index)
                    }
                }
                else if (ABS(self.currentIndex - index) > 2) {
                    TUILOGW(@"jump preRender index:%lu, the difference between the index and the current coordinates is too large", self.currentIndex);
                }
                else {
                    TUILOGW(@"start prePlay a player, index:%lu", index)
                    [self.vodManager prePlayWithModel:vodModel type:1];

                    [self.vodManager setVideoWidget:videoItemView.videoBaseView.videoContainerView model:vodModel
                                 firstFrameCallBack:^(BOOL isFirstFrame) {
                        [videoItemView hiddenCoverImage:YES];
                    }];
                }
            } else {
                [self.vodManager resetAllPlayer];
                [self.vodManager muteAllPlayer];
                TUILOGI(@"mCurrentIndex start update to:%lu", index)
                if (self.curVodController != nil) {
                    [self.curVodController onUnBindController];
                    [self.vodManager removeDelegate:self.curVodController];
                    self.curVodController = nil;
                }
                [self.vodManager addDelegate:itemView.vodController];
                [self.vodManager.vodPreLoadManager setCurrentPlayingModel:vodModel];
                [self.vodManager playWithModel:vodModel];
                [self removeVodPlayerCache:index preIndex:self.currentIndex];
                self.curVodController = itemView.vodController;
                [itemView.vodController onBindController:self.vodManager.currentVodPlayer];
                self.preIndex = self.currentIndex;
                self.currentIndex = index;
                TUILOGI(@"mCurrentIndex updated to:%lu", self.currentIndex)
                [self.vodManager setVideoWidget:videoItemView.videoBaseView.videoContainerView model:vodModel
                             firstFrameCallBack:^(BOOL isFirstFrame) {
                    [videoItemView hiddenCoverImage:YES];
                }];
                [videoItemView getPlayer:self.vodManager.currentVodPlayer];
                if (self.cachePageIndex >= 0 && self.cacheIndex >= 0) {
                    [self bindVideoView:self.cachePageIndex index:self.cacheIndex isPreBind:YES];
                    self.cachePageIndex = -1;
                    self.cacheIndex = -1;
                }
            }
        } else {
            TUILOGE(@"unImpl modelType:%@", dataModel);
        }
    } else {
        TUILOGE(@"bindVideoView met a null view,viewId:%lu,isPreBind:%i", pageViewId, isPreBind);
    }
}

- (void)bindVideoView:(NSNumber *)viewTag index:(NSInteger)index {
    [self bindVideoView:viewTag.integerValue index:index isPreBind:NO];
}

- (void)preBindVideo:(NSNumber *)viewTag index:(NSInteger)index {
    self.cachePageIndex = viewTag.longValue;
    self.cacheIndex = index;
}

- (NSNumber *)setModels:(NSArray<TUIPlayerVideoModel *> *)models {
    @synchronized (self.dataArray) {
        self.currentIndex = 0;
        self.preIndex = 0;
        [self.vodManager removeAllPlayerCache];
        [self.vodManager stopAllPlayer];
        self.vodManager.currentVodPlayer = nil;
        [self.dataArray removeAllObjects];
        [self.dataArray addObjectsFromArray:models];
        [self.vodManager.vodPreLoadManager setPlayerModels:models];
        [self.vodManager.vodDataManager setShortVideoModels:models];
        return @(0);
    }
}

- (NSNumber *)appendModels:(NSArray<TUIPlayerVideoModel *> *)models {
    @synchronized (self.dataArray) {
        [self.dataArray addObjectsFromArray:models];
        [self.vodManager.vodPreLoadManager appendPlayerModels:models];
        [self.vodManager.vodDataManager appendShortVideoModels:models];
        TUIPlayerVideoModel *nextModel = [self getVodModel:self.currentIndex + 1];
        if (nil != nextModel) {
            [self.vodManager.vodPreLoadManager cancelPreLoadOperationWith:nextModel];
            [self.vodManager prePlayWithModel:nextModel type:1];
        }
        return @(0);
    }
}

- (TUIPlayerVideoModel*)getVodModel:(NSUInteger)index {
    TUIPlayerVideoModel* model = nil;
    if (index >= 0 && index < self.dataArray.count) {
        TUIPlayerDataModel* dataModel = self.dataArray[index];
        if (nil != dataModel && [dataModel isKindOfClass:[TUIPlayerVideoModel class]]) {
            model = dataModel.asVodModel;
        }
    }
    return model;
}


- (void)releaseController {
    TUILOGI(@"start release shortController,controllerId:%lu", self.controllerId)
    [self.vodManager.currentVodPlayer removeVideo];
    self.vodManager.currentVodPlayer = nil;
    [self.vodManager stopAllPlayer];
    [self.engineObserver onRelease:self.controllerId];
}

- (void)setVodStrategy:(TUIPlayerVodStrategyModel *)strategy {
    dispatch_async(dispatch_get_main_queue(), ^{
        [self.vodStrategyManager setVideoStrategy:strategy];
        [self.vodManager setVodStrategyManager:self.vodStrategyManager];
        self.vodManager.vodPreLoadManager.strategyManager = self.vodStrategyManager;
    });
}

- (NSNumber *)startCurrent {
    BOOL result = tuicpa();
    if (result == NO) {
        TUILOGE(@"[setModels] License checked failed! Player Premium license required!")
        return @(TUI_ERROR_INVALID_LICENSE);
    }
    if (nil != self.vodManager.currentVodPlayer) {
        [self.vodManager.currentVodPlayer resumePlay];
    }
    return @(0);
}

- (void)setVideoLoop:(BOOL)isLoop {
    if (self.vodManager) {
        self.vodManager.loop = isLoop;
    }
}

- (void)switchResolution:(long)resolution switchType:(NSInteger)switchType {
    [self.vodStrategyManager setPreferredResolution:resolution];
    TUITXVodPlayer *player = self.vodManager.currentVodPlayer;
    if (player == nil) {
        return;
    }
    NSArray<TUIPlayerBitrateItem *> *bitrates = [player supportedBitrates];
    NSInteger targetIndex = -1;
    for (TUIPlayerBitrateItem *item in bitrates) {
        if (item.width * item.height == resolution) {
            targetIndex = item.index;
            break;
        }
    }
    if (targetIndex < 0 && bitrates.count > 0) {
        targetIndex = bitrates.firstObject.index;
    }
    if (targetIndex >= 0) {
        [player setBitrateIndex:targetIndex];
    }
}

- (TUIPlayerRecordManager*)recordManager {
    if (self->_recordManager == nil) {
        self->_recordManager = [[TUIPlayerRecordManager alloc] init];
        self->_recordManager.strategyManager = self.vodStrategyManager;
    }
    return self->_recordManager;
}

- (TUIPlayerVodManager*)vodManager {
    if (self->_vodManager == nil) {
        self->_vodManager = [[TUIPlayerVodManager alloc] init];
        [self->_vodManager setVodStrategyManager:self.vodStrategyManager];
    }
    return self->_vodManager;
}

- (TUIPlayerVodPreLoadManager*)preloadManager {
    if (self->_preloadManager == nil) {
        self->_preloadManager = [[TUIPlayerVodPreLoadManager alloc] init];
        self->_preloadManager.strategyManager = self.vodStrategyManager;
    }
    return self->_preloadManager;
}

#pragma mark - cpa
static BOOL tuicpa(void){
    Class tpaClass = NSClassFromString(@"TUIPlayerAuth");
    SEL cpaSelector = NSSelectorFromString(@"cpa");
    if (!tpaClass || ![tpaClass respondsToSelector:cpaSelector]) {
        TUILOGE(@"[cpa] Missing TUIPlayerAuth or cpa selector.");
        return NO;
    }
    IMP cpaImp = [tpaClass methodForSelector:cpaSelector];
    if (!cpaImp) {
        TUILOGE(@"[cpa] Failed to load cpa implementation.");
        return NO;
    }
    BOOL (*cpaFunc)(id, SEL) = (BOOL (*)(id, SEL))cpaImp;
    BOOL result = cpaFunc(tpaClass, cpaSelector);
    return result;
}
@end

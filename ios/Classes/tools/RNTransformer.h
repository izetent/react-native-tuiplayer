#import <Foundation/Foundation.h>
#import <TUIPlayerShortVideo/TUIPlayerShortVideo-umbrella.h>

NS_ASSUME_NONNULL_BEGIN

@interface RNTransformer : NSObject

+ (NSArray<TUIPlayerVideoModel *> *)videoModelsFromArray:(NSArray<NSDictionary *> *)array;
+ (TUIPlayerVideoModel *)videoModelFromDictionary:(NSDictionary *)dict;
+ (TUIPlayerVodStrategyModel *)strategyFromDictionary:(NSDictionary *)dict;

@end

NS_ASSUME_NONNULL_END

//
//  RNShortEngineObserver.h
//  rnplayer_kit
//
//  Created by kongdywang on 21.8.24.
//

#import <Foundation/Foundation.h>

#ifndef RNShortEngineObserver_h
#define RNShortEngineObserver_h

@protocol RNShortEngineObserver <NSObject>

- (void)onRelease:(NSUInteger)controllerId;

@end


#endif /* RNShortEngineObserver_h */

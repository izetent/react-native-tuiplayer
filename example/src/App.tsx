import React, {
  Component,
  createRef,
  useCallback,
  useEffect,
  useRef,
  useState,
} from 'react';
import {
  FlatList,
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  Dimensions,
  TouchableOpacity,
} from 'react-native';
import type { ListRenderItemInfo } from 'react-native';
import {
  RNPlayerShortController,
  RNPlayerView,
  setTUIPlayerConfig,
  TUIVodPlayerController,
} from 'react-native-txplayer';
import type {
  RNPlayerBitrateItem,
  RNVideoSource,
  RNVodEvent,
} from 'react-native-txplayer';
import { parseSubtitles, type SubtitleCue } from './subtitleParser';

const VIDEO_URL =
  'https://gz001-1377187151.cos.ap-guangzhou.myqcloud.com/short1080/S00268-Hollywood%20Star%27s%20Fake%20Girlfriend/4/index.m3u8';
const COVER_URL =
  'https://gz001-1377187151.cos.ap-guangzhou.myqcloud.com/short1080/S00379-The%20Double%20Life/S00379-The%20Double%20Life.jpg';
const SUBTITLE_URL =
  'https://cdn.sparktube.top/SRT/S00269-My%20Sister%20Stole%20My%20Man/1_zh.vtt';

const { height: SCREEN_HEIGHT } = Dimensions.get('window');

const SOURCES: RNVideoSource[] = Array.from({ length: 24 }, (_, index) => ({
  videoURL: VIDEO_URL,
  coverPictureUrl: COVER_URL,
  subtitleSources: [
    {
      name: 'ex-cn-srt',
      url: SUBTITLE_URL,
      mimeType: 'text/vtt',
    },
  ],
  extInfo: { index },
}));

type FeedItem = {
  id: string;
  index: number;
};

export default function App() {
  const controllerRef = useRef<RNPlayerShortController | null>(null);
  const vodRef = useRef<TUIVodPlayerController | null>(null);
  const viewRefs = useRef<
    Record<number, React.RefObject<Component<any, any, any> | null>>
  >({});
  const activeIndexRef = useRef<number | null>(null);
  const [activeIndex, setActiveIndex] = useState<number | null>(null);
  const renderModeRef = useRef<number>(1);
  const [status, setStatus] = useState('正在初始化播放器…');
  const [subtitleText, setSubtitleText] = useState('');
  const subtitleTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  const subtitleCuesRef = useRef<SubtitleCue[] | null>(null);
  const subtitleCacheRef = useRef<Record<string, SubtitleCue[]>>({});
  const [availableResolutions, setAvailableResolutions] = useState<
    RNPlayerBitrateItem[]
  >([]);
  const [currentResolutionLabel, setCurrentResolutionLabel] = useState('自动');
  const [isAutoResolution, setIsAutoResolution] = useState(true);

  const stopSubtitleTimer = useCallback(() => {
    if (subtitleTimerRef.current) {
      clearInterval(subtitleTimerRef.current);
      subtitleTimerRef.current = null;
    }
  }, []);

  const startSubtitleTimer = useCallback(() => {
    stopSubtitleTimer();
    subtitleTimerRef.current = setInterval(async () => {
      const cues = subtitleCuesRef.current;
      const player = vodRef.current;
      const index = activeIndexRef.current;
      if (!cues || cues.length === 0 || !player || index == null) {
        return;
      }
      try {
        const current = await player.getCurrentPlayTime();
        const cue = cues.find((c) => current >= c.start && current <= c.end);
        setSubtitleText(cue ? cue.text : '');
      } catch (_error) {
        console.log('_error', _error);
        // ignore polling errors
      }
    }, 300);
  }, [stopSubtitleTimer]);

  const loadSubtitleSource = useCallback(
    async (source?: { url?: string | null } | null) => {
      stopSubtitleTimer();
      setSubtitleText('');
      subtitleCuesRef.current = null;
      const urlRaw = source?.url ?? '';
      if (!urlRaw) {
        return;
      }
      const url = String(urlRaw);
      try {
        let cues = subtitleCacheRef.current[url];
        if (!cues) {
          const res = await fetch(url);
          const text = await res.text();
          cues = parseSubtitles(text);
          subtitleCacheRef.current[url] = cues;
        }
        subtitleCuesRef.current = cues;
        startSubtitleTimer();
      } catch (error) {
        console.log('loadSubtitleSource error', error);
      }
    },
    [startSubtitleTimer, stopSubtitleTimer]
  );

  const pauseActiveIfPlaying = useCallback(async () => {
    const player = vodRef.current;
    const index = activeIndexRef.current;
    if (!player || index == null) {
      return false;
    }
    try {
      const isPlaying = await player.isPlaying();
      if (isPlaying) {
        await player.pause();
        setStatus(`离开第 ${index + 1} 条，已暂停`);
        return true;
      }
    } catch (error) {
      console.log('pauseActiveIfPlaying error', error);
    }
    return false;
  }, []);

  const ensureViewRef = useCallback((index: number) => {
    const existing = viewRefs.current[index];
    if (existing) {
      return existing;
    }
    const ref = createRef<Component<any, any, any> | null>();
    viewRefs.current[index] = ref;
    return ref;
  }, []);

  const bindAndStartRef = useRef<
    ((index: number, attempt?: number) => Promise<void>) | null
  >(null);

  const togglePlaybackForIndex = useCallback(async (index: number) => {
    // 若当前未聚焦，先聚焦并开始播放该条
    const binder = bindAndStartRef.current;
    if (activeIndexRef.current !== index && binder) {
      await binder(index, 0);
    }
    const player = vodRef.current;
    if (!player || activeIndexRef.current !== index) {
      setStatus('播放器未准备好');
      return;
    }
    const isPlaying = await player.isPlaying();
    if (isPlaying) {
      await player.pause();
      setStatus(`已暂停第 ${index + 1} 条`);
    } else {
      await player.resume();
      setStatus(`继续播放第 ${index + 1} 条`);
    }
  }, []);

  const toggleRenderMode = useCallback(async () => {
    const player = vodRef.current;
    const current = activeIndexRef.current;
    if (!player || current == null) {
      setStatus('当前没有正在控制的播放器');
      return;
    }
    const nextMode = renderModeRef.current === 1 ? 2 : 1;
    try {
      await player.setRenderMode(nextMode);
      renderModeRef.current = nextMode;
      setStatus(
        `第 ${current + 1} 条渲染模式已切换为 ${
          nextMode === 2 ? '填充裁剪' : '等比适配'
        }`
      );
    } catch (error) {
      console.log('setRenderMode error', error);
      setStatus(`切换失败: ${String(error)}`);
    }
  }, []);

  const pickResolutionByHeight = useCallback(
    (items: RNPlayerBitrateItem[], targetHeight: number) => {
      if (!items?.length) {
        return null;
      }
      const sorted = [...items].sort((a, b) => b.height - a.height);
      const exact = sorted.find(
        (item) => Math.abs(item.height - targetHeight) < 80
      );
      if (exact) {
        return exact;
      }
      const lower = sorted.find((item) => item.height <= targetHeight);
      return lower ?? sorted[0];
    },
    []
  );

  const refreshResolutions = useCallback(
    async (player: TUIVodPlayerController | null, autoPick = false) => {
      if (!player) {
        return;
      }
      try {
        const items = await player.getSupportResolution();
        console.log('[resolution] fetched items', items);
        setAvailableResolutions(items);
        if (autoPick && items.length) {
          const preferred =
            [1080, 720, 480]
              .map((height) => pickResolutionByHeight(items, height))
              .find(Boolean) ?? items[0];
          if (preferred) {
            await player.switchResolution(preferred.index);
            setCurrentResolutionLabel(`${preferred.height}p`);
            setIsAutoResolution(true);
            setStatus(`自动选择 ${preferred.height}p 清晰度`);
          }
        } else if (!items.length) {
          console.warn('[resolution] empty list; source may be single bitrate');
        }
      } catch (error) {
        console.log('refreshResolutions error', error);
      }
    },
    [pickResolutionByHeight]
  );

  const handleSwitchQuality = useCallback(
    async (targetHeight?: number) => {
      const player = vodRef.current;
      if (!player) {
        setStatus('播放器未准备好');
        return;
      }
      try {
        const items =
          availableResolutions.length > 0
            ? availableResolutions
            : await player.getSupportResolution();
        console.log('[resolution] switch request items', items);
        if (!items.length) {
          setStatus('当前清晰度列表为空');
          console.warn('[resolution] empty list when switching');
          return;
        }
        if (!availableResolutions.length) {
          setAvailableResolutions(items);
        }
        if (targetHeight == null) {
          const preferred =
            [1080, 720, 480]
              .map((height) => pickResolutionByHeight(items, height))
              .find(Boolean) ?? items[0];
          if (preferred) {
            await player.switchResolution(preferred.index);
            setCurrentResolutionLabel(`${preferred.height}p`);
            setIsAutoResolution(true);
            setStatus('已切换到自动优选清晰度');
          }
          return;
        }
        const match = pickResolutionByHeight(items, targetHeight);
        if (!match) {
          setStatus(`未找到 ${targetHeight}p 清晰度`);
          return;
        }
        await player.switchResolution(match.index);
        setCurrentResolutionLabel(`${match.height}p`);
        setIsAutoResolution(false);
        setStatus(`已切换到 ${match.height}p`);
      } catch (error) {
        console.log('handleSwitchQuality error', error);
        setStatus(`切换清晰度失败: ${String(error)}`);
      }
    },
    [availableResolutions, pickResolutionByHeight]
  );

  const bindAndStart = useCallback(
    async (index: number, attempt = 0) => {
      const controller = controllerRef.current;
      if (!controller) {
        return;
      }
      const ref = ensureViewRef(index);
      if (!ref.current) {
        if (attempt > 5) {
          console.warn('RNPlayerView not ready for index', index);
          return;
        }
        requestAnimationFrame(() => {
          const binder = bindAndStartRef.current;
          binder?.(index, attempt + 1);
        });
        return;
      }
      const previousIndex = activeIndexRef.current;
      if (previousIndex != null && previousIndex !== index) {
        await pauseActiveIfPlaying();
      }
      if (activeIndexRef.current === index) {
        return;
      }
      stopSubtitleTimer();
      activeIndexRef.current = index;
      setActiveIndex(index);
      setAvailableResolutions([]);
      setIsAutoResolution(true);
      setCurrentResolutionLabel('自动');
      try {
        const vodController = await controller.bindVodPlayer(ref, index);
        vodRef.current = vodController;
        vodController.clearListener();
        // Disable native subtitle rendering; we will render in RN.
        vodController.selectSubtitleTrack(-1).catch(() => {});
        // Prepare RN subtitle overlay based on source config.
        loadSubtitleSource(SOURCES[index]?.subtitleSources?.[0]);
        const handlePlayEvent = (event?: RNVodEvent) => {
          const payload = event as
            | { event?: unknown; EVT_EVENT?: unknown }
            | undefined;
          const codeRaw = payload?.event ?? payload?.EVT_EVENT;
          const code = Number(codeRaw);
          if (!Number.isNaN(code)) {
            console.log(
              '[event] onVodPlayerEvent code',
              code,
              'payload',
              payload
            );
          }
          if (code === 2010 || code === 2003 || code === 50001) {
            refreshResolutions(vodController, false);
          }
          if (
            code === 2015 /* PLAY_EVT_STREAM_SWITCH_SUCC */ ||
            code === 2009 /* PLAY_EVT_CHANGE_RESOLUTION */
          ) {
            setStatus(`清晰度切换成功，事件码 ${code}`);
          }
        };
        vodController.addListener({
          onSubtitleTracksUpdate: (_tracks) => {
            const source = SOURCES[index]?.subtitleSources?.[0];
            loadSubtitleSource(source);
          },
          onVodPlayerEvent: handlePlayEvent,
          onRcvFirstIframe: (event) => {
            console.log('onRcvFirstIframe', { index, event });
          },
          onPlayBegin: (event) => {
            console.log('onPlayBegin', { index, event });
          },
          onPlayEnd: (event) => {
            console.log('onPlayEnd', { index, event });
          },
        });
        await controller.startCurrent();
        setStatus(`播放第 ${index + 1} 条`);
        await refreshResolutions(vodController, true);
        const nextRef = viewRefs.current[index + 1];
        if (nextRef?.current) {
          controller.preCreateVodPlayer(nextRef, index + 1).catch(() => {});
        }
      } catch (error) {
        console.log('bind/start error', error);
        setStatus(`绑定失败 #${index + 1}: ${String(error)}`);
      }
    },
    [
      ensureViewRef,
      pauseActiveIfPlaying,
      stopSubtitleTimer,
      loadSubtitleSource,
      refreshResolutions,
    ]
  );
  bindAndStartRef.current = bindAndStart;

  const onViewableItemsChanged = useRef(
    ({ viewableItems }: { viewableItems: any[] }) => {
      const firstVisible = viewableItems.find((item) => item.isViewable);
      if (firstVisible?.index == null) {
        pauseActiveIfPlaying();
        return;
      }
      const currentIndex = activeIndexRef.current;
      const currentStillVisible = viewableItems.some(
        (item) => item.index === currentIndex && item.isViewable
      );
      if (!currentStillVisible && currentIndex != null) {
        pauseActiveIfPlaying();
      }
      bindAndStart(firstVisible.index);
    }
  );

  useEffect(() => {
    let cancelled = false;
    async function initPlayer() {
      try {
        await setTUIPlayerConfig({
          licenseUrl: 'YOUR_LICENSE_URL',
          licenseKey: 'YOUR_LICENSE_KEY',
          enableLog: true,
        });
        const controller = new RNPlayerShortController();
        controllerRef.current = controller;
        await controller.setVodStrategy({ renderMode: renderModeRef.current });
        await controller.setModels(SOURCES);
        if (!cancelled) {
          setStatus('已加载播放源，等待列表渲染…');
          requestAnimationFrame(() => bindAndStart(0));
        } else {
          controller.release();
        }
      } catch (error) {
        console.log('error=', error);
        setStatus(`初始化失败: ${String(error)}`);
      }
    }
    initPlayer();
    return () => {
      cancelled = true;
      stopSubtitleTimer();
      controllerRef.current?.release();
      controllerRef.current = null;
    };
  }, [bindAndStart, stopSubtitleTimer]);

  const data = SOURCES.map<FeedItem>((_, index) => ({
    id: `video-${index}`,
    index,
  }));

  const renderItem = ({ item, index }: ListRenderItemInfo<FeedItem>) => {
    const ref = ensureViewRef(item.index);
    const qualityDisplay =
      isAutoResolution && currentResolutionLabel !== '自动'
        ? `自动（${currentResolutionLabel}）`
        : currentResolutionLabel || '自动';
    return (
      <View style={styles.card}>
        <RNPlayerView
          ref={ref}
          style={styles.player}
          resizeMode={index % 2 === 0 ? 'cover' : 'contain'}
          videoWidth={1080}
          videoHeight={1920}
          collapsable={false}
        />
        <View style={styles.overlay} pointerEvents="none">
          <Text style={styles.title}>短视频 #{item.index + 1}</Text>
        </View>
        {activeIndex === item.index && subtitleText ? (
          <View style={styles.subtitleOverlay} pointerEvents="none">
            <Text style={styles.subtitleText}>{subtitleText}</Text>
          </View>
        ) : null}
        <View style={styles.controls}>
          <TouchableOpacity
            style={styles.button}
            onPress={() => togglePlaybackForIndex(item.index)}
          >
            <Text style={styles.buttonText}>播放/暂停</Text>
          </TouchableOpacity>
          <TouchableOpacity
            style={[styles.button, styles.buttonSecondary]}
            onPress={toggleRenderMode}
          >
            <Text style={styles.buttonText}>切换尺寸</Text>
          </TouchableOpacity>
          {/* <TouchableOpacity
            style={[styles.button, styles.buttonPip]}
            onPress={requestPictureInPicture}
          >
            <Text style={styles.buttonText}>画中画</Text>
          </TouchableOpacity> */}
          {/* <TouchableOpacity
            style={[styles.button, styles.buttonTertiary]}
            onPress={logCurrentTimes}
          >
            <Text style={styles.buttonText}>打印时长</Text>
          </TouchableOpacity> */}
        </View>
        {activeIndex === item.index ? (
          <View style={styles.qualityBar}>
            <Text style={styles.qualityLabel}>清晰度：{qualityDisplay}</Text>
            <View style={styles.qualityButtons}>
              <TouchableOpacity
                style={[
                  styles.qualityButton,
                  isAutoResolution && styles.qualityButtonActive,
                ]}
                onPress={() => handleSwitchQuality()}
              >
                <Text style={styles.qualityButtonText}>自动</Text>
              </TouchableOpacity>
              {[480, 720, 1080].map((height) => {
                const isActive =
                  !isAutoResolution && currentResolutionLabel === `${height}p`;
                return (
                  <TouchableOpacity
                    key={height}
                    style={[
                      styles.qualityButton,
                      isActive && styles.qualityButtonActive,
                    ]}
                    onPress={() => handleSwitchQuality(height)}
                  >
                    <Text style={styles.qualityButtonText}>{height}p</Text>
                  </TouchableOpacity>
                );
              })}
            </View>
          </View>
        ) : null}
      </View>
    );
  };

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <Text style={styles.status}>{status}</Text>
      <FlatList
        data={data}
        keyExtractor={(item) => item.id}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        showsVerticalScrollIndicator={false}
        onViewableItemsChanged={onViewableItemsChanged.current}
        viewabilityConfig={{ itemVisiblePercentThreshold: 80 }}
        initialNumToRender={4}
        windowSize={6}
        maxToRenderPerBatch={4}
        pagingEnabled
        snapToInterval={SCREEN_HEIGHT}
        snapToAlignment="start"
        decelerationRate="fast"
      />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  listContent: {
    paddingBottom: 0,
  },
  status: {
    color: '#e2e8f0',
    textAlign: 'center',
    marginBottom: 8,
    fontSize: 12,
  },
  card: {
    height: SCREEN_HEIGHT,
    backgroundColor: '#000',
    justifyContent: 'center',
    alignItems: 'center',
  },
  player: {
    position: 'absolute',
    top: 200,
    right: 0,
    left: 0,
    bottom: 200,
    backgroundColor: 'red',
  },
  cover: {
    position: 'absolute',
    width: '100%',
    height: SCREEN_HEIGHT / 2,
    top: (SCREEN_HEIGHT - SCREEN_HEIGHT / 2) / 2,
    left: 0,
    opacity: 0.9,
  },
  overlay: {
    position: 'absolute',
    top: 24,
    left: 16,
    paddingVertical: 8,
    paddingHorizontal: 12,
    backgroundColor: 'rgba(0,0,0,0.5)',
    borderRadius: 12,
  },
  title: {
    color: '#fff',
    fontSize: 18,
    fontWeight: '700',
  },
  caption: {
    color: '#cbd5e1',
    fontSize: 13,
    marginTop: 6,
  },
  controls: {
    position: 'absolute',
    bottom: 128,
    left: 16,
    flexDirection: 'row',
    gap: 12,
  },
  subtitleOverlay: {
    position: 'absolute',
    bottom: 48,
    left: 16,
    right: 16,
    paddingVertical: 12,
    paddingHorizontal: 16,
    backgroundColor: 'rgba(0,0,0,0.6)',
    borderRadius: 12,
  },
  subtitleText: {
    color: '#f8fafc',
    fontSize: 16,
    textAlign: 'center',
    lineHeight: 22,
  },
  button: {
    backgroundColor: '#2563eb',
    paddingVertical: 10,
    paddingHorizontal: 16,
    borderRadius: 12,
    minWidth: 72,
    alignItems: 'center',
  },
  buttonSecondary: {
    backgroundColor: '#0ea5e9',
  },
  buttonPip: {
    backgroundColor: '#f97316',
  },
  buttonTertiary: {
    backgroundColor: '#059669',
  },
  buttonText: {
    color: '#fff',
    fontWeight: '700',
  },
  qualityBar: {
    position: 'absolute',
    top: 72,
    left: 16,
    right: 16,
    paddingVertical: 10,
    paddingHorizontal: 12,
    backgroundColor: 'rgba(0,0,0,0.55)',
    borderRadius: 12,
  },
  qualityLabel: {
    color: '#e2e8f0',
    fontSize: 12,
    marginBottom: 8,
  },
  qualityButtons: {
    flexDirection: 'row',
    gap: 8,
    flexWrap: 'wrap',
  },
  qualityButton: {
    paddingVertical: 8,
    paddingHorizontal: 12,
    borderRadius: 10,
    borderWidth: 1,
    borderColor: 'rgba(255,255,255,0.35)',
    minWidth: 72,
    alignItems: 'center',
  },
  qualityButtonActive: {
    backgroundColor: '#2563eb',
    borderColor: '#2563eb',
  },
  qualityButtonText: {
    color: '#fff',
    fontWeight: '700',
  },
});

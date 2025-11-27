import { useCallback, useEffect, useRef, useState } from 'react';
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  Pressable,
  InteractionManager,
} from 'react-native';
import {
  initialize,
  TuiplayerShortVideoView,
  TuiplayerListPlayMode,
  type ShortVideoSource,
  type ShortVideoOverlayMeta,
  type TuiplayerShortVideoViewHandle,
  type VodPlayerSupportResolution,
} from 'react-native-tuiplayer';

type Video = {
  id?: number;
  name?: string;
  img?: string;
  icon?: string;
  coverImg?: string;
  cover?: string;
  episode?: number;
  links720p?: string[] | string;
  authorName?: string;
  authorAvatar?: string;
  details?: string;
  type?: string;
  isLiked?: number;
  [key: string]: unknown;
};

const PAGE_SIZE = 10;
const LICENSE_URL = process.env.TUIPLAYER_LICENSE_URL ?? '';
const LICENSE_KEY = process.env.TUIPLAYER_LICENSE_KEY ?? '';

const OVERRIDE_VIDEO_URL =
  'https://gz001-1377187151.cos.ap-guangzhou.myqcloud.com/Short1080150w/S00329-The Dumb Billionaire Heiress In Love Part II/2.mp4';

const buildVideoSource = (video: Video): ShortVideoSource | null => {
  const resolvedUrl = OVERRIDE_VIDEO_URL;
  const coverPictureUrl = [
    video.img,
    video.icon,
    video.coverImg,
    video.cover,
  ].find(
    (value): value is string => typeof value === 'string' && value.length > 0
  );

  return {
    type: 'url',
    url: resolvedUrl,
    coverPictureUrl,
    autoPlay: true,
    meta: {
      name: video.name || video.details || '未命名视频',
      icon: coverPictureUrl,
      type: video.type,
      details: video.details || `观看全集>>第${video.episode ?? 1}集`,
      likeCount: 110,
      favoriteCount:
        pickNumber(video.favoriteCount, video.collectCount) || 9999,
      isShowPaly: true,
      isLiked: pickBoolean(video.isLiked, video.liked),
      isBookmarked: pickBoolean(video.isBookmarked, video.favorited),
    },
  };
};

const pickNumber = (...values: unknown[]): number | undefined => {
  for (const value of values) {
    if (typeof value === 'number' && Number.isFinite(value)) {
      return value;
    }
    if (typeof value === 'string') {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) {
        return parsed;
      }
    }
  }
  return undefined;
};

const ensureNumber = (value: unknown, fallback = 0): number => {
  if (typeof value === 'number' && Number.isFinite(value)) {
    return value;
  }
  if (typeof value === 'string') {
    const parsed = Number(value);
    if (Number.isFinite(parsed)) {
      return parsed;
    }
  }
  return fallback;
};

const pickBoolean = (...values: unknown[]): boolean | undefined => {
  for (const value of values) {
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'number') {
      if (value === 1) {
        return true;
      }
      if (value === 0) {
        return false;
      }
    }
    if (typeof value === 'string') {
      const normalized = value.trim().toLowerCase();
      if (['1', 'true', 'yes', 'y'].includes(normalized)) {
        return true;
      }
      if (['0', 'false', 'no', 'n'].includes(normalized)) {
        return false;
      }
    }
  }
  return undefined;
};

const hasValidMetaType = (
  value: ShortVideoOverlayMeta['type'] | undefined
): boolean => {
  if (typeof value === 'string') {
    return value.trim().length > 0;
  }
  if (Array.isArray(value)) {
    return value.some(
      (tag) => typeof tag === 'string' && tag.trim().length > 0
    );
  }
  return false;
};

const ensureMetaType = (
  meta: ShortVideoOverlayMeta,
  ...fallbacks: Array<ShortVideoOverlayMeta['type'] | undefined>
): ShortVideoOverlayMeta => {
  if (hasValidMetaType(meta.type)) {
    return meta;
  }
  const fallback = fallbacks.find(hasValidMetaType);
  if (!fallback) {
    const { ...rest } = meta;
    return rest;
  }
  return {
    ...meta,
    type: fallback,
  };
};

// 示例播放固定 URL，省去接口/token 请求
async function requestFeaturedVideos(
  _page: number,
  _limit: number = PAGE_SIZE
): Promise<Video[]> {
  return [
    {
      name: '调试视频',
      url: OVERRIDE_VIDEO_URL,
      cover: undefined,
    },
  ];
}

const TEST_SUBTITLE_URL =
  'https://cdn.sparktube.top/SRT/S00269-My%20Sister%20Stole%20My%20Man/1_zh.vtt';

export default function App() {
  const [autoPlay] = useState(true);
  const [paused, setPaused] = useState(false);
  const [sources, setSources] = useState<ShortVideoSource[]>([]);
  const [userInputEnabled] = useState(true);
  const [playModeValue] = useState(TuiplayerListPlayMode.MODE_LIST_LOOP ?? 0);
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [, setErrorMessage] = useState<string | null>(null);
  const [vodStatus, setVodStatus] = useState<{
    playing?: boolean;
    currentTime?: number;
    duration?: number;
    playable?: number;
    bitrateIndex?: number;
    resolutions?: VodPlayerSupportResolution[];
    loop?: boolean;
    rate?: number;
    muted?: boolean;
    error?: string;
  }>({});
  const [playerBottom, setPlayerBottom] = useState<10 | 11>(10);

  const fetchLockRef = useRef(false);
  const isMountedRef = useRef(true);
  const playerRef = useRef<TuiplayerShortVideoViewHandle>(null);
  const hasAutoStartedRef = useRef(false);
  const autoPlayRef = useRef(autoPlay);
  const pausedRef = useRef(paused);
  const vodStatusRef = useRef(vodStatus);
  const hasLoggedFirstRef = useRef(false);
  const overlaySeqRef = useRef(0);
  const togglePlayerBottom = useCallback(() => {
    setPlayerBottom((value) => (value === 10 ? 11 : 10));
  }, []);

  useEffect(() => {
    try {
      initialize({
        licenseUrl: LICENSE_URL,
        licenseKey: LICENSE_KEY,
        enableLog: true,
      });
    } catch (error) {
      console.error('Tuiplayer initialize failed:', error);
    }
  }, []);

  useEffect(() => {
    autoPlayRef.current = autoPlay;
  }, [autoPlay]);

  useEffect(() => {
    pausedRef.current = paused;
  }, [paused]);

  useEffect(() => {
    vodStatusRef.current = vodStatus;
  }, [vodStatus]);

  useEffect(() => {
    console.log('[ShortVideo] paused prop 更新', {
      paused,
      playingState: vodStatusRef.current.playing,
    });
  }, [paused]);

  const triggerInitialAutoPlay = useCallback(() => {
    InteractionManager.runAfterInteractions(() => {
      if (!isMountedRef.current) {
        return;
      }
      const handle = playerRef.current;
      if (!handle) {
        hasAutoStartedRef.current = false;
        return;
      }
      handle.startPlayIndex(0, false);
      if (autoPlayRef.current && !pausedRef.current) {
        handle.resume();
      }
      hasAutoStartedRef.current = true;
    });
  }, []);

  const logCurrentSource = useCallback(
    async (reason: string, pageIndex?: number, total?: number) => {
      if (!playerRef.current) {
        return;
      }
      try {
        const info = await playerRef.current.getCurrentSource();
        console.log('[ShortVideo] 当前资源', reason, {
          pageIndex,
          total,
          info,
        });
      } catch (error) {
        console.warn('[ShortVideo] 获取当前资源失败:', error);
      }
    },
    [playerRef]
  );

  useEffect(() => {
    if (sources.length === 0) {
      resetSources();
    }
    return () => {
      isMountedRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (sources.length > 0 && !hasAutoStartedRef.current && !loading) {
      triggerInitialAutoPlay();
    }
  }, [loading, sources, triggerInitialAutoPlay]);

  const ensureTestSubtitle = useCallback((list: ShortVideoSource[]) => {
    console.log(
      '[ShortVideo] ensureTestSubtitle 被调用，视频数量:',
      list.length
    );

    if (list.length === 0) {
      return list;
    }

    // 为所有视频添加字幕，方便调试
    const enhanced = list.map((video, index) => {
      const hasSubtitle = video.subtitles?.some(
        (item) => item.url === TEST_SUBTITLE_URL
      );

      if (hasSubtitle) {
        console.log(`[ShortVideo] 视频${index}已有字幕，跳过`);
        return video;
      }

      const videoWithSubtitle: ShortVideoSource = {
        ...video,
        subtitles: [
          ...(video.subtitles ?? []),
          {
            name: '中文字幕',
            url: TEST_SUBTITLE_URL,
            mimeType: 'text/vtt',
          },
        ],
      };

      console.log(`[ShortVideo] 添加字幕到视频${index}:`, {
        videoUrl: video.url?.substring(0, 50) + '...',
        subtitleUrl: TEST_SUBTITLE_URL,
        subtitlesCount: videoWithSubtitle.subtitles?.length,
      });

      return videoWithSubtitle;
    });

    return enhanced;
  }, []);

  const loadRemotePage = useCallback(
    async (
      targetPage: number,
      append: boolean,
      options?: { topLoading?: boolean; bottomLoading?: boolean }
    ) => {
      if (fetchLockRef.current) {
        return;
      }
      fetchLockRef.current = true;
      setLoading(true);
      if (!append) {
        setErrorMessage(null);
      }
      if (options?.topLoading) {
        playerRef.current?.setTopLoadingVisible(true);
      }
      if (options?.bottomLoading) {
        playerRef.current?.setBottomLoadingVisible(true);
      }

      try {
        const videos = await requestFeaturedVideos(targetPage, PAGE_SIZE);

        if (!isMountedRef.current) {
          return;
        }

        const nextSources = videos
          .map(buildVideoSource)
          .filter((item): item is ShortVideoSource => item !== null);

        setSources((prev) =>
          ensureTestSubtitle(append ? [...prev, ...nextSources] : nextSources)
        );
        setPage(targetPage);
        setHasMore(videos.length >= PAGE_SIZE);
        setErrorMessage(
          nextSources.length === 0 ? '接口未返回可播放的视频资源' : null
        );
      } catch (error) {
        console.error('加载接口视频失败:', error);
        if (!isMountedRef.current) {
          return;
        }
        setErrorMessage('加载接口数据失败，请稍后重试');
      } finally {
        if (isMountedRef.current) {
          setLoading(false);
        }
        if (options?.topLoading) {
          playerRef.current?.setTopLoadingVisible(false);
        }
        if (options?.bottomLoading) {
          playerRef.current?.setBottomLoadingVisible(false);
        }
        fetchLockRef.current = false;
      }
    },
    [ensureTestSubtitle]
  );

  const resetSources = useCallback(
    (withTopLoading = false) => {
      setPaused(false);
      hasAutoStartedRef.current = false;
      fetchLockRef.current = false;
      setPage(1);
      setHasMore(true);
      setLoading(false);
      setErrorMessage(null);
      hasLoggedFirstRef.current = false;
      setPlayerBottom(10);

      setSources([]);
      loadRemotePage(
        1,
        false,
        withTopLoading ? { topLoading: true } : undefined
      );
    },
    [loadRemotePage]
  );

  const loadMoreRemote = useCallback(() => {
    if (loading || !hasMore) {
      return;
    }
    loadRemotePage(page + 1, true, { bottomLoading: true });
  }, [hasMore, loading, loadRemotePage, page]);

  const handleEndReached = useCallback(() => {
    console.log('[ShortVideo] 到达列表末尾');
    loadMoreRemote();
  }, [loadMoreRemote]);

  const handleTopReached = useCallback(
    (event: { nativeEvent: { offset: number } }) => {
      console.log(
        '[ShortVideo] 已滑动到顶部，可下拉刷新',
        event.nativeEvent.offset
      );
      if (loading) {
        return;
      }
      resetSources(true);
    },
    [loading, resetSources]
  );

  const applyOverlayMeta = useCallback(
    (
      index: number,
      producer: (current: ShortVideoSource) => ShortVideoSource | null
    ) => {
      let nextMeta: ShortVideoOverlayMeta | null = null;
      setSources((prev) => {
        if (!Number.isInteger(index) || index < 0 || index >= prev.length) {
          return prev;
        }
        const current = prev[index]!;
        const updated = producer(current);
        if (!updated) {
          return prev;
        }
        if (updated === current) {
          return prev;
        }
        nextMeta = updated.meta ?? null;
        const next = prev.slice();
        next[index] = updated;
        return next;
      });
      if (nextMeta) {
        try {
          playerRef.current?.updateMeta(index, nextMeta);
        } catch (error) {
          console.warn('[ShortVideo] 更新原生 Meta 失败:', error);
        }
      }
    },
    []
  );

  const handleOverlayAction = useCallback(
    (payload?: Record<string, unknown>) => {
      const action =
        typeof payload?.action === 'string' ? payload.action : undefined;
      const indexRaw = payload?.index;
      const index =
        typeof indexRaw === 'number' && Number.isInteger(indexRaw)
          ? indexRaw
          : -1;
      if (index < 0 || action == null) {
        return;
      }
      const seq = ++overlaySeqRef.current;
      console.log('[ShortVideo] overlayAction 收到事件', {
        seq,
        action,
        index,
        pausedProp: pausedRef.current,
        playingState: vodStatusRef.current.playing,
        payload,
      });
      applyOverlayMeta(index, (current) => {
        const nativeMeta = (payload?.source as { meta?: ShortVideoOverlayMeta })
          ?.meta;
        const baseMeta: ShortVideoOverlayMeta = {
          ...(nativeMeta ?? {}),
          ...(current.meta ?? {}),
        };
        console.log('[ShortVideo] overlayAction meta 合并', {
          seq,
          index,
          action,
          currentMeta: current.meta,
          nativeMeta,
          mergedMeta: baseMeta,
        });
        const withMetaType = (
          meta: ShortVideoOverlayMeta
        ): ShortVideoOverlayMeta =>
          ensureMetaType(
            meta,
            baseMeta.type,
            current.meta?.type,
            nativeMeta?.type
          );
        switch (action) {
          case 'like': {
            const liked = baseMeta.isLiked ?? false;
            const base = ensureNumber(baseMeta.likeCount, 0);
            console.log('[ShortVideo] 点赞操作，当前状态：', {
              seq,
              liked,
              index,
              pausedProp: pausedRef.current,
              playingState: vodStatusRef.current.playing,
              currentMeta: current.meta,
              nativeMeta,
              baseMeta,
            });

            const newMeta = withMetaType({
              ...baseMeta,
              isLiked: !liked,
              likeCount: Math.max(0, base + (liked ? -1 : 1)),
            });
            console.log('[ShortVideo] 点赞操作完成', {
              seq,
              index,
              prevLiked: liked,
              nextLiked: newMeta.isLiked,
              prevCount: base,
              nextCount: newMeta.likeCount,
              newMetaType: newMeta.type,
            });
            console.log('[ShortVideo] 点赞 updateMeta 调用', {
              seq,
              index,
              meta: newMeta,
            });
            try {
              playerRef.current?.updateMeta(index, newMeta);
              console.log('[ShortVideo] 点赞 updateMeta 完成', { seq, index });
            } catch (error) {
              console.warn('[ShortVideo] 点赞 updateMeta 失败', {
                seq,
                index,
                error,
              });
            }

            return {
              ...current,
              meta: newMeta,
            };
          }
          case 'favorite': {
            const bookmarked = baseMeta.isBookmarked ?? false;
            const base = ensureNumber(baseMeta.favoriteCount, 0);
            console.log('[ShortVideo] 收藏操作，当前状态：', {
              seq,
              bookmarked,
              index,
              pausedProp: pausedRef.current,
              playingState: vodStatusRef.current.playing,
              currentMeta: current.meta,
              nativeMeta,
              baseMeta,
            });

            const newMeta = withMetaType({
              ...baseMeta,
              isBookmarked: !bookmarked,
              favoriteCount: Math.max(0, base + (bookmarked ? -1 : 1)),
            });
            console.log('[ShortVideo] 收藏操作完成', {
              seq,
              index,
              prevBookmarked: bookmarked,
              nextBookmarked: newMeta.isBookmarked,
              prevCount: base,
              nextCount: newMeta.favoriteCount,
              newMetaType: newMeta.type,
            });
            console.log('[ShortVideo] 收藏 updateMeta 调用', {
              seq,
              index,
              meta: newMeta,
            });
            try {
              playerRef.current?.updateMeta(index, newMeta);
              console.log('[ShortVideo] 收藏 updateMeta 完成', { seq, index });
            } catch (error) {
              console.warn('[ShortVideo] 收藏 updateMeta 失败', {
                seq,
                index,
                error,
              });
            }

            return {
              ...current,
              meta: newMeta,
            };
          }
          case 'icon': {
            console.log('[ShortVideo] 点击图标/封面');
            return current;
          }
          case 'name': {
            console.log('[ShortVideo] 点击名称/标题');
            return current;
          }
          case 'details': {
            console.log('[ShortVideo] 点击详情');
            return current;
          }
          case 'play': {
            console.log('[ShortVideo] 点击播放按钮');
            return current;
          }
          case 'comment': {
            console.log('[ShortVideo] more');
            return current;
          }
          default:
            console.log('action===============', action);
            return current;
        }
      });
      setSources((prev) => {
        if (index < 0 || index >= prev.length) {
          return prev;
        }
        const next = prev;
        console.log('[ShortVideo] overlayAction setSources 触发', {
          seq,
          action,
          index,
          pausedProp: pausedRef.current,
          playingState: vodStatusRef.current.playing,
          prevMeta: prev[index]?.meta,
          overlaySeq: overlaySeqRef.current,
        });
        return next;
      });
    },
    [applyOverlayMeta]
  );

  const handleVodEvent = useCallback(
    ({
      nativeEvent: { type, payload },
    }: {
      nativeEvent: { type: string; payload?: Record<string, unknown> };
    }) => {
      // console.log('[ShortVideo] VodEvent', type, payload);
      if (type === 'overlayAction') {
        handleOverlayAction(payload);
        return;
      }
      setVodStatus((prev) => {
        let next = prev;
        switch (type) {
          case 'onPlayBegin':
            next = { ...prev, playing: true };
            break;
          case 'onPlayPause':
          case 'onPlayStop':
          case 'onPlayEnd':
            next = { ...prev, playing: false };
            break;
          case 'onPlayLoading':
            next = { ...prev, playing: false };
            break;
          case 'onPlayProgress': {
            const data = payload as
              | { current?: number; duration?: number; playable?: number }
              | undefined;
            const currentRaw = data?.current;
            const durationRaw = data?.duration;
            const playableRaw = data?.playable;
            const normalize = (value?: number) =>
              typeof value === 'number'
                ? Number((value > 1000 ? value / 1000 : value).toFixed(2))
                : undefined;
            next = {
              ...prev,
              currentTime: normalize(currentRaw) ?? prev.currentTime,
              duration: normalize(durationRaw) ?? prev.duration,
              playable: normalize(playableRaw) ?? prev.playable,
            };
            break;
          }
          case 'onSeek': {
            const position = (payload as { position?: number } | undefined)
              ?.position;
            next = {
              ...prev,
              currentTime:
                position != null
                  ? Number(position.toFixed(2))
                  : prev.currentTime,
            };
            break;
          }
          case 'onError': {
            const message = (payload as { message?: string } | undefined)
              ?.message;
            next = {
              ...prev,
              playing: false,
              error: message ?? prev.error,
            };
            break;
          }
          case 'onRenderModeChanged': {
            const mode = (payload as { mode?: number } | undefined)?.mode;
            next = {
              ...prev,
              resolutions: prev.resolutions,
            };
            if (mode != null) {
              console.log('[ShortVideo] 渲染模式切换', mode);
            }
            break;
          }
          default:
            next = prev;
            break;
        }
        if (
          type === 'overlayAction' ||
          type === 'onPlayPause' ||
          type === 'onPlayBegin' ||
          type === 'onPlayEnd' ||
          type === 'onPlayStop'
        ) {
          console.log('[ShortVideo] VodEvent 调试', {
            type,
            payload,
            pausedProp: pausedRef.current,
            playingState: vodStatusRef.current.playing,
            nextPlaying: next.playing,
          });
        }
        return next;
      });
    },
    [handleOverlayAction]
  );

  const refreshVodStatus = useCallback(async () => {
    const handle = playerRef.current;
    if (!handle) {
      return;
    }
    try {
      const [
        playing,
        currentTime,
        durationValue,
        playable,
        bitrateIndex,
        resolutions,
        loop,
      ] = await Promise.all([
        handle.vodPlayer.isPlaying().catch(() => undefined),
        handle.vodPlayer.getCurrentPlaybackTime().catch(() => undefined),
        handle.vodPlayer.getDuration().catch(() => undefined),
        handle.vodPlayer.getPlayableDuration().catch(() => undefined),
        handle.vodPlayer.getBitrateIndex().catch(() => undefined),
        handle.vodPlayer.getSupportResolution().catch(() => undefined),
        handle.vodPlayer.isLoop().catch(() => undefined),
      ]);
      setVodStatus((prev) => ({
        ...prev,
        playing: typeof playing === 'boolean' ? playing : prev.playing,
        currentTime:
          typeof currentTime === 'number'
            ? Number(currentTime.toFixed(2))
            : prev.currentTime,
        duration:
          typeof durationValue === 'number'
            ? Number(durationValue.toFixed(2))
            : prev.duration,
        playable:
          typeof playable === 'number'
            ? Number(playable.toFixed(2))
            : prev.playable,
        bitrateIndex:
          typeof bitrateIndex === 'number' ? bitrateIndex : prev.bitrateIndex,
        resolutions: resolutions ?? prev.resolutions,
        loop: typeof loop === 'boolean' ? loop : prev.loop,
      }));
    } catch (error) {
      console.warn('[ShortVideo] 获取播放器状态失败:', error);
    }
  }, []);

  const handlePageChanged = useCallback(
    ({
      nativeEvent: { index, total },
    }: {
      nativeEvent: { index: number; total: number };
    }) => {
      const reason = hasLoggedFirstRef.current ? '页面切换' : '首次加载';
      hasLoggedFirstRef.current = true;
      console.log('[ShortVideo] 页面事件', { reason, index, total });
      if (reason === '首次加载') {
        if (autoPlayRef.current && !pausedRef.current) {
          playerRef.current?.startPlayIndex(0, true);
          setTimeout(() => {
            togglePlayerBottom();
          }, 300);
        }
      } else {
        togglePlayerBottom();
      }
      hasAutoStartedRef.current = true;
      logCurrentSource(reason, index, total);
      refreshVodStatus();
    },
    [logCurrentSource, refreshVodStatus, togglePlayerBottom]
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <View style={styles.toolbar}>
        <View style={styles.buttonRow}>
          <ActionButton
            label="清屏幕"
            onPress={() => {
              playerRef.current?.clearScreen();
            }}
          />
        </View>
        <View style={styles.buttonRow}>
          <ActionButton
            label="恢复屏幕"
            onPress={() => {
              playerRef.current?.restoreScreen();
            }}
          />
        </View>
      </View>

      <TuiplayerShortVideoView
        ref={playerRef}
        sources={sources}
        autoPlay={autoPlay}
        style={[styles.player, { bottom: playerBottom }]}
        paused={paused}
        playMode={playModeValue}
        userInputEnabled={userInputEnabled}
        pageScrollMsPerInch={25}
        onTopReached={handleTopReached}
        onEndReached={handleEndReached}
        onPageChanged={handlePageChanged}
        onVodEvent={handleVodEvent}
      />
    </SafeAreaView>
  );
}

type ActionButtonProps = {
  label: string;
  onPress: () => void;
  disabled?: boolean;
};

function ActionButton({ label, onPress, disabled }: ActionButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      disabled={disabled}
      style={({ pressed }) => [
        styles.button,
        disabled ? styles.buttonDisabled : undefined,
        pressed ? styles.buttonPressed : undefined,
      ]}
    >
      <Text style={styles.buttonText}>{label}</Text>
    </Pressable>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
  },
  toolbar: {
    paddingHorizontal: 16,
    paddingTop: 12,
    paddingBottom: 8,
    backgroundColor: 'rgba(0, 0, 0, 0.6)',
    position: 'absolute',
    top: 100,
    left: 0,
    right: 0,
    zIndex: 10,
    opacity: 0.3,
  },
  title: {
    fontSize: 18,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 6,
  },
  caption: {
    fontSize: 12,
    color: '#B5B5B5',
    marginBottom: 6,
  },
  section: {
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  sectionTitle: {
    fontSize: 14,
    fontWeight: '600',
    color: '#fff',
    marginBottom: 6,
  },
  warning: {
    fontSize: 12,
    color: '#FFB400',
    marginBottom: 6,
  },
  error: {
    fontSize: 12,
    color: '#FF6B6B',
    marginBottom: 6,
  },
  buttonRow: {
    flexDirection: 'row',
    columnGap: 12,
    marginBottom: 8,
  },
  button: {
    flex: 1,
    backgroundColor: '#1F6FEB',
    borderRadius: 6,
    paddingVertical: 10,
    paddingHorizontal: 12,
    alignItems: 'center',
    justifyContent: 'center',
  },
  buttonPressed: {
    opacity: 0.7,
  },
  buttonDisabled: {
    opacity: 0.4,
  },
  buttonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '500',
  },
  player: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
  },
});

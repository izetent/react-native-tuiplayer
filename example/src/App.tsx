import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  Pressable,
  InteractionManager,
  ScrollView,
} from 'react-native';
import {
  initialize,
  TuiplayerShortVideoView,
  TuiplayerListPlayMode,
  TuiplayerResolutionType,
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

const FEATURE_ENDPOINT = 'http://175.178.78.190/dramango/v1/video/feature';
const GET_TOKEN_ENDPOINT = 'http://175.178.78.190/dramango/v1/auth/login';
const tokenParams = {
  user: 'user' + Date.now(),
  password: 'userpassword',
};
let authorizationToken =
  'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiIxMDM0NSIsInVuaW9uSWQiOiIiLCJleHAiOjE3NjA4NzIyMDQuMTIyMzU2LCJpYXQiOjE3NjAyNjc0MDQuMTIyMzU3LCJpc3MiOiJEcmFtYVgifQ.AbjXiT-mgOOJFNMdNM2OhMvm7FSSqPVrVdwHbKApsCU';
const PAGE_SIZE = 10;

const buildApiHeaders = (): Record<
  'Content-Type' | 'Authorization',
  string
> => ({
  'Content-Type': 'application/json',
  'Authorization': authorizationToken,
});

const extractVideoList = (payload: unknown): Video[] => {
  if (Array.isArray(payload)) {
    return payload as Video[];
  }

  if (payload && typeof payload === 'object') {
    const topLevel = payload as Record<string, unknown>;

    for (const key of ['body', 'data', 'result', 'payload']) {
      if (!(key in topLevel)) {
        continue;
      }
      const nested = topLevel[key];
      if (Array.isArray(nested)) {
        return nested as Video[];
      }
      if (nested && typeof nested === 'object') {
        const nestedObj = nested as Record<string, unknown>;
        for (const innerKey of [
          'list',
          'rows',
          'items',
          'videos',
          'records',
          'content',
        ]) {
          const inner = nestedObj[innerKey];
          if (Array.isArray(inner)) {
            return inner as Video[];
          }
        }
      }
    }

    for (const key of [
      'list',
      'rows',
      'items',
      'videos',
      'records',
      'content',
      'result',
    ]) {
      const value = topLevel[key];
      if (Array.isArray(value)) {
        return value as Video[];
      }
    }
  }

  return [];
};

const TOKEN_CONTAINER_KEYS = ['body', 'data', 'result', 'payload'] as const;
const TOKEN_VALUE_KEYS = [
  'token',
  'authToken',
  'authorization',
  'accessToken',
  'access_token',
  'jwt',
  'jwtToken',
] as const;

const extractTokenFromPayload = (payload: unknown): string | null => {
  if (typeof payload === 'string') {
    const trimmed = payload.trim();
    return trimmed.length > 0 ? trimmed : null;
  }
  if (Array.isArray(payload)) {
    for (const item of payload) {
      const candidate = extractTokenFromPayload(item);
      if (candidate) {
        return candidate;
      }
    }
    return null;
  }
  if (!payload || typeof payload !== 'object') {
    return null;
  }
  const container = payload as Record<string, unknown>;
  for (const key of TOKEN_VALUE_KEYS) {
    const value = container[key];
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim();
    }
  }
  for (const key of TOKEN_CONTAINER_KEYS) {
    if (key in container) {
      const nested = extractTokenFromPayload(container[key]);
      if (nested) {
        return nested;
      }
    }
  }
  return null;
};

const buildVideoSource = (video: Video): ShortVideoSource | null => {
  const rawLinks = video.links720p;
  let rawUrl: string | undefined;

  if (typeof rawLinks === 'string') {
    rawUrl = rawLinks;
  } else if (Array.isArray(rawLinks)) {
    rawUrl = rawLinks.find(
      (item): item is string => typeof item === 'string' && item.length > 0
    );
  }

  if (!rawUrl) {
    return null;
  }

  const resolvedUrl =
    typeof video.episode === 'number' &&
    Number.isFinite(video.episode) &&
    /Episodes/i.test(rawUrl)
      ? rawUrl.replace(/Episodes/gi, String(video.episode))
      : rawUrl;

  const coverPictureUrl = [
    video.img,
    video.icon,
    video.coverImg,
    video.cover,
  ].find(
    (value): value is string => typeof value === 'string' && value.length > 0
  );

  const authorName =
    pickString(
      video.authorName,
      video.author,
      video.uploader,
      video.source,
      video.name
    ) ?? '官方账号';
  const authorAvatar = pickString(
    video.authorAvatar,
    video.avatar,
    video.icon,
    video.img
  );
  return {
    type: 'url',
    url: resolvedUrl,
    coverPictureUrl,
    autoPlay: true,
    meta: {
      authorName,
      authorAvatar,
      title: video.details,
      watchMoreText: `观看全集>>第${video.episode ?? 1}集`,
      likeCount: pickNumber(video.likeCount, video.likes) || 11010,
      commentCount: pickNumber(video.commentCount, video.comments) || 999,
      favoriteCount:
        pickNumber(video.favoriteCount, video.collectCount) || 9999,
      isLiked: pickBoolean(video.isLiked, video.liked),
      isBookmarked: pickBoolean(video.isBookmarked, video.favorited),
      isFollowed: pickBoolean(video.isFollowed, video.followed),
    },
  };
};

const pickString = (...values: unknown[]): string | undefined => {
  for (const value of values) {
    if (typeof value === 'string' && value.trim().length > 0) {
      return value.trim();
    }
  }
  return undefined;
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

async function refreshAuthorizationToken(): Promise<string> {
  const payload = {
    ...tokenParams,
    user: 'user' + Date.now(),
  };
  const response = await fetch(GET_TOKEN_ENDPOINT, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    credentials: 'include',
    body: JSON.stringify(payload),
  });

  if (!response.ok) {
    const message = await response.text().catch(() => '');
    throw new Error(`刷新 token 接口返回错误 ${response.status}: ${message}`);
  }

  const tokenPayload = await response.json().catch(() => {
    throw new Error('刷新 token 接口返回了非 JSON 数据');
  });
  const rawToken = extractTokenFromPayload(tokenPayload);
  if (!rawToken) {
    throw new Error('刷新 token 接口未返回 token');
  }
  const formattedToken = rawToken.startsWith('Bearer ')
    ? rawToken
    : `Bearer ${rawToken}`;
  authorizationToken = formattedToken;
  console.info('[ShortVideo] token 已更新');
  return formattedToken;
}

async function requestFeaturedVideos(
  page: number,
  limit: number = PAGE_SIZE
): Promise<Video[]> {
  const performRequest = () =>
    fetch(FEATURE_ENDPOINT, {
      method: 'POST',
      headers: buildApiHeaders(),
      body: JSON.stringify({
        page,
        limit,
      }),
      credentials: 'include',
    });

  let response = await performRequest();

  if (response.status === 401) {
    try {
      await refreshAuthorizationToken();
    } catch (error) {
      const message =
        error instanceof Error ? error.message : String(error ?? '未知错误');
      throw new Error(`刷新 token 失败: ${message}`);
    }
    response = await performRequest();
  }

  if (!response.ok) {
    const message = await response.text().catch(() => '');
    throw new Error(`接口返回错误 ${response.status}: ${message}`);
  }

  const payload = await response.json().catch(() => {
    throw new Error('接口返回了非 JSON 数据');
  });

  if (payload && typeof payload === 'object') {
    const container = payload as Record<string, unknown>;
    const code =
      typeof container.code === 'number' ? container.code : undefined;
    if (code !== undefined && code !== 0) {
      const message =
        (typeof container.msg === 'string' && container.msg.length > 0
          ? container.msg
          : null) ?? `接口返回错误 code=${code}`;
      throw new Error(message);
    }
    console.log('response=============', container);
    const target =
      container.body ??
      container.data ??
      container.result ??
      container.payload ??
      payload;
    return extractVideoList(target);
  }

  return extractVideoList(payload);
}

const LICENSE_URL = '4ddf62fce4de0a8fe505415a45a27823';
const LICENSE_KEY =
  'https://1377187151.trtcube-license.cn/license/v2/1377187151_1/v_cube.license';

export default function App() {
  const [autoPlay, setAutoPlay] = useState(true);
  const [paused, setPaused] = useState(false);
  const [sources, setSources] = useState<ShortVideoSource[]>([]);
  const [userInputEnabled, setUserInputEnabled] = useState(true);
  const [playModeValue, setPlayModeValue] = useState(
    TuiplayerListPlayMode.MODE_LIST_LOOP ?? 0
  );
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
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

  const fetchLockRef = useRef(false);
  const isMountedRef = useRef(true);
  const playerRef = useRef<TuiplayerShortVideoViewHandle>(null);
  const hasAutoStartedRef = useRef(false);
  const autoPlayRef = useRef(autoPlay);
  const pausedRef = useRef(paused);
  const hasLoggedFirstRef = useRef(false);

  useEffect(() => {
    try {
      initialize({
        licenseUrl:
          'https://1377187151.trtcube-license.cn/license/v2/1377187151_1/v_cube.license',
        licenseKey: '4ddf62fce4de0a8fe505415a45a27823',
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

  const licenseConfigured = Boolean(LICENSE_KEY && LICENSE_URL);

  const loadRemotePage = useCallback(
    async (targetPage: number, append: boolean) => {
      if (fetchLockRef.current) {
        return;
      }
      fetchLockRef.current = true;
      setLoading(true);
      if (!append) {
        setErrorMessage(null);
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
          append ? [...prev, ...nextSources] : nextSources
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
        fetchLockRef.current = false;
      }
    },
    []
  );

  const resetSources = useCallback(() => {
    setPaused(false);
    hasAutoStartedRef.current = false;
    fetchLockRef.current = false;
    setPage(1);
    setHasMore(true);
    setLoading(false);
    setErrorMessage(null);
    hasLoggedFirstRef.current = false;

    setSources([]);
    loadRemotePage(1, false);
  }, [loadRemotePage]);

  const loadMoreRemote = useCallback(() => {
    if (loading || !hasMore) {
      return;
    }
    loadRemotePage(page + 1, true);
  }, [hasMore, loading, loadRemotePage, page]);

  const handleAppend = useCallback(() => {
    loadMoreRemote();
  }, [loadMoreRemote]);

  const handleReset = useCallback(() => {
    resetSources();
  }, [resetSources]);

  const handleTogglePlayMode = useCallback(() => {
    const listLoop = TuiplayerListPlayMode.MODE_LIST_LOOP ?? 0;
    const oneLoop = TuiplayerListPlayMode.MODE_ONE_LOOP ?? 1;
    setPlayModeValue((prev) => {
      const next = prev === oneLoop ? listLoop : oneLoop;
      playerRef.current?.setPlayMode(next);
      return next;
    });
  }, []);

  const handleToggleScrollEnabled = useCallback(() => {
    setUserInputEnabled((prev) => {
      const next = !prev;
      playerRef.current?.setUserInputEnabled(next);
      return next;
    });
  }, []);

  const handleJumpToStart = useCallback(() => {
    playerRef.current?.startPlayIndex(0, true);
  }, []);

  const handleSwitchResolution = useCallback(() => {
    const target = TuiplayerResolutionType.CURRENT ?? 1;
    playerRef.current?.switchResolution(1280 * 720, target);
  }, []);

  const handlePausePreload = useCallback(() => {
    playerRef.current?.pausePreload();
  }, []);

  const handleResumePreload = useCallback(() => {
    playerRef.current?.resumePreload();
  }, []);

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
    },
    []
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
      applyOverlayMeta(index, (current) => {
        const nativeMeta = (payload?.source as { meta?: ShortVideoOverlayMeta })
          ?.meta;
        const baseMeta: ShortVideoOverlayMeta = {
          ...(current.meta ?? {}),
          ...(nativeMeta ?? {}),
        };
        switch (action) {
          case 'watchMore': {
            console.log(
              '[ShortVideo] 点击观看更多，当前状态：',
              baseMeta.watchMoreText
            );
            return {
              ...current,
            };
          }
          case 'like': {
            const liked = baseMeta.isLiked ?? false;
            const base = ensureNumber(baseMeta.likeCount, 0);
            console.log('[ShortVideo] 点赞操作，当前状态：', liked);

            return {
              ...current,
              meta: {
                ...baseMeta,
                isLiked: !liked,
                likeCount: Math.max(0, base + (liked ? -1 : 1)),
              },
            };
          }
          case 'favorite': {
            const bookmarked = baseMeta.isBookmarked ?? false;
            const base = ensureNumber(baseMeta.favoriteCount, 0);
            console.log('[ShortVideo] 收藏操作，当前状态：', bookmarked);
            return {
              ...current,
              meta: {
                ...baseMeta,
                isBookmarked: !bookmarked,
                favoriteCount: Math.max(0, base + (bookmarked ? -1 : 1)),
              },
            };
          }
          case 'comment': {
            console.log('[ShortVideo] 点击评论，打开评论面板');
            return {
              ...current,
              meta: {
                ...baseMeta,
                commentCount: ensureNumber(baseMeta.commentCount, 0) + 1,
              },
            };
          }
          case 'avatar': {
            const followed = baseMeta.isFollowed ?? false;
            console.log('[ShortVideo] 关注操作，当前状态：', followed);
            return {
              ...current,
              meta: {
                ...baseMeta,
                isFollowed: !followed,
              },
            };
          }
          case 'author': {
            console.log('[ShortVideo] 点击作者信息栏');
            return current;
          }
          default:
            return current;
        }
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
        return next;
      });
    },
    [handleOverlayAction]
  );

  const infoText = useMemo(() => {
    const parts = [
      '当前模式：接口',
      `自动播放：${autoPlay ? '开启' : '关闭'}`,
      `手动暂停：${paused ? '是' : '否'}`,
      `滑动可用：${userInputEnabled ? '是' : '否'}`,
      `视频数量：${sources.length}`,
    ];
    const listLoop = TuiplayerListPlayMode.MODE_LIST_LOOP ?? 0;
    parts.push(
      `播放模式：${playModeValue === listLoop ? '列表循环' : '单个循环'}`
    );
    parts.push(`页码：${page}`);
    parts.push(`可继续加载：${hasMore ? '是' : '否'}`);
    return parts.join(' / ');
  }, [
    autoPlay,
    hasMore,
    page,
    paused,
    playModeValue,
    sources.length,
    userInputEnabled,
  ]);

  const appendButtonLabel = useMemo(() => {
    if (loading) {
      return '接口数据加载中...';
    }
    if (!hasMore) {
      return '没有更多接口视频';
    }
    return '加载更多接口视频';
  }, [hasMore, loading]);

  const resetButtonLabel = useMemo(() => '重新加载接口数据', []);

  const statusText = useMemo(() => {
    if (loading) {
      return '接口数据加载中...';
    }
    return hasMore ? '上滑可继续加载更多接口数据' : '已到达接口数据最后一页';
  }, [hasMore, loading]);

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
      if (reason === '首次加载' && autoPlayRef.current && !pausedRef.current) {
        playerRef.current?.startPlayIndex(0, true);
      }
      hasAutoStartedRef.current = true;
      logCurrentSource(reason, index, total);
      refreshVodStatus();
    },
    [logCurrentSource, refreshVodStatus]
  );

  const vodStatusText = useMemo(() => {
    const {
      playing,
      currentTime,
      duration,
      playable,
      bitrateIndex,
      resolutions,
      loop,
      rate,
      muted,
    } = vodStatus;
    const parts: string[] = [];
    if (typeof playing === 'boolean') {
      parts.push(`播放中：${playing ? '是' : '否'}`);
    }
    if (typeof loop === 'boolean') {
      parts.push(`循环：${loop ? '开启' : '关闭'}`);
    }
    if (typeof rate === 'number') {
      parts.push(`速率：${rate.toFixed(2)}`);
    }
    if (typeof muted === 'boolean') {
      parts.push(`静音：${muted ? '是' : '否'}`);
    }
    if (typeof currentTime === 'number') {
      parts.push(`定位：${currentTime}s`);
    }
    if (typeof duration === 'number') {
      parts.push(`总时长：${duration}s`);
    }
    if (typeof playable === 'number') {
      parts.push(`缓冲：${playable}s`);
    }
    if (typeof bitrateIndex === 'number') {
      parts.push(`码率档：#${bitrateIndex}`);
    }
    if (resolutions?.length) {
      const desc = resolutions
        .map((item) => `${item.index}:${item.width}×${item.height}`)
        .join(', ');
      parts.push(`可选清晰度：[${desc}]`);
    }
    return parts.join(' / ') || '播放器状态待刷新';
  }, [vodStatus]);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <ScrollView style={styles.toolbar}>
        {/* <Text style={styles.title}>TUIPlayer 短视频示例</Text> */}
        <Text style={styles.caption}>{infoText}</Text>
        {statusText ? <Text style={styles.caption}>{statusText}</Text> : null}
        {errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}
        {!licenseConfigured ? (
          <Text style={styles.warning}>
            ⚠️ 未配置 License，将使用公开示例数据，正式上线请替换为真实
            License。
          </Text>
        ) : null}

        <View style={styles.buttonRow}>
          <ActionButton
            label={autoPlay ? '关闭自动播放' : '开启自动播放'}
            onPress={() => setAutoPlay((prev) => !prev)}
          />
        </View>

        <View style={styles.buttonRow}>
          <ActionButton
            label={paused ? '恢复播放' : '暂停当前视频'}
            onPress={() => setPaused((prev) => !prev)}
          />
          <ActionButton label="切换播放模式" onPress={handleTogglePlayMode} />
          <ActionButton
            label="调用 setPlayMode"
            onPress={() => {
              const listLoop = TuiplayerListPlayMode.MODE_LIST_LOOP ?? 0;
              const oneLoop = TuiplayerListPlayMode.MODE_ONE_LOOP ?? 1;
              const next = playModeValue === oneLoop ? listLoop : oneLoop;
              playerRef.current?.setPlayMode(next);
              setPlayModeValue(next);
            }}
          />
        </View>

        <View style={styles.buttonRow}>
          <ActionButton
            label={userInputEnabled ? '禁止滑动' : '允许滑动'}
            onPress={handleToggleScrollEnabled}
          />
          <ActionButton label="跳到第一个视频" onPress={handleJumpToStart} />
        </View>

        <View style={styles.buttonRow}>
          <ActionButton
            label={appendButtonLabel}
            onPress={handleAppend}
            disabled={loading || !hasMore}
          />
          <ActionButton label={resetButtonLabel} onPress={handleReset} />
        </View>

        <View style={styles.buttonRow}>
          <ActionButton label="切换到 720P" onPress={handleSwitchResolution} />
        </View>

        <View style={styles.buttonRow}>
          <ActionButton label="暂停预加载" onPress={handlePausePreload} />
          <ActionButton label="恢复预加载" onPress={handleResumePreload} />
        </View>

        <View style={styles.section}>
          <Text style={styles.sectionTitle}>播放器调试</Text>
          <Text style={styles.caption}>{vodStatusText}</Text>
          <View style={styles.buttonRow}>
            <ActionButton label="刷新状态" onPress={refreshVodStatus} />
            <ActionButton
              label="播放/暂停"
              onPress={async () => {
                try {
                  const playing =
                    await playerRef.current?.vodPlayer.isPlaying();
                  if (playing) {
                    await playerRef.current?.vodPlayer.pause();
                  } else {
                    await playerRef.current?.vodPlayer.resume();
                  }
                } catch (error) {
                  console.warn('[ShortVideo] 播放切换失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
          </View>
          <View style={styles.buttonRow}>
            <ActionButton
              label="跳至60秒"
              onPress={async () => {
                try {
                  await playerRef.current?.vodPlayer.seekTo(60);
                } catch (error) {
                  console.warn('[ShortVideo] 跳转失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
            <ActionButton
              label="循环切换"
              onPress={async () => {
                try {
                  const loop = await playerRef.current?.vodPlayer.isLoop();
                  await playerRef.current?.vodPlayer.setLoop(!loop);
                  setVodStatus((prev) => ({ ...prev, loop: !loop }));
                } catch (error) {
                  console.warn('[ShortVideo] 设置循环失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
          </View>
          <View style={styles.buttonRow}>
            <ActionButton
              label="1.25x 速度"
              onPress={async () => {
                try {
                  await playerRef.current?.vodPlayer.setRate(1.25);
                  setVodStatus((prev) => ({ ...prev, rate: 1.25 }));
                } catch (error) {
                  console.warn('[ShortVideo] 设置倍速失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
            <ActionButton
              label="恢复1.0x"
              onPress={async () => {
                try {
                  await playerRef.current?.vodPlayer.setRate(1.0);
                  setVodStatus((prev) => ({ ...prev, rate: 1.0 }));
                } catch (error) {
                  console.warn('[ShortVideo] 恢复倍速失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
          </View>
          <View style={styles.buttonRow}>
            <ActionButton
              label="静音切换"
              onPress={async () => {
                try {
                  const muted = vodStatus.muted ?? false;
                  await playerRef.current?.vodPlayer.setMute(!muted);
                  setVodStatus((prev) => ({ ...prev, muted: !muted }));
                } catch (error) {
                  console.warn('[ShortVideo] 静音切换失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
            <ActionButton label="拉取清晰度" onPress={refreshVodStatus} />
          </View>
          <View style={styles.buttonRow}>
            <ActionButton
              label="铺满模式"
              onPress={async () => {
                try {
                  await playerRef.current?.vodPlayer.setRenderMode(0);
                } catch (error) {
                  console.warn('[ShortVideo] 设置铺满模式失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
            <ActionButton
              label="等比适配"
              onPress={async () => {
                try {
                  await playerRef.current?.vodPlayer.setRenderMode(1);
                } catch (error) {
                  console.warn('[ShortVideo] 设置等比模式失败:', error);
                } finally {
                  await refreshVodStatus();
                }
              }}
            />
          </View>
        </View>
      </ScrollView>

      <TuiplayerShortVideoView
        ref={playerRef}
        sources={sources}
        autoPlay={autoPlay}
        style={styles.player}
        paused={paused}
        playMode={playModeValue}
        userInputEnabled={userInputEnabled}
        pageScrollMsPerInch={25}
        vodStrategy={{ renderMode: 0 }}
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
    top: 0,
    left: 0,
    right: 0,
    zIndex: 10,
    opacity: 0.3,
    height: 300,
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
    flex: 1,
  },
});

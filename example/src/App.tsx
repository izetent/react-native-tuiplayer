import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
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
  TuiplayerResolutionType,
  type ShortVideoSource,
  type TuiplayerShortVideoViewHandle,
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
  [key: string]: unknown;
};

const FEATURE_ENDPOINT = 'http://175.178.78.190/dramango/v1/video/feature';
const AUTHORIZATION_TOKEN =
  'Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ1aWQiOiIxMDM0NSIsInVuaW9uSWQiOiIiLCJleHAiOjE3NjA4NzIyMDQuMTIyMzU2LCJpYXQiOjE3NjAyNjc0MDQuMTIyMzU3LCJpc3MiOiJEcmFtYVgifQ.AbjXiT-mgOOJFNMdNM2OhMvm7FSSqPVrVdwHbKApsCU';
const PAGE_SIZE = 10;

const API_HEADERS = {
  'Content-Type': 'application/json',
  'Authorization': AUTHORIZATION_TOKEN,
} as const;

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

  return {
    type: 'url',
    url: resolvedUrl,
    coverPictureUrl,
  };
};

async function requestFeaturedVideos(
  page: number,
  limit: number = PAGE_SIZE
): Promise<Video[]> {
  const response = await fetch(FEATURE_ENDPOINT, {
    method: 'POST',
    headers: API_HEADERS,
    body: JSON.stringify({
      page,
      limit,
    }),
    credentials: 'include',
  });
  console.log('response=============', response);
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

const FILE_ID_SOURCES: ShortVideoSource[] = [];

const FILE_ID_EXTRA: ShortVideoSource[] = [
  {
    type: 'fileId',
    appId: 1500005830,
    fileId: '387702294394228527',
    coverPictureUrl:
      'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/3afb9bd9387702294394228527/coverBySnapshot/coverBySnapshot_10_0.jpg',
  },
  {
    type: 'fileId',
    appId: 1500005830,
    fileId: '387702294168748446',
    coverPictureUrl:
      'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/ccf4265f387702294168748446/coverBySnapshot/coverBySnapshot_10_0.jpg',
  },
];

type SourceMode = 'fileId' | 'url';

export default function App() {
  const [autoPlay, setAutoPlay] = useState(true);
  const [paused, setPaused] = useState(false);
  const [mode, setMode] = useState<SourceMode>('url');
  const [sources, setSources] = useState<ShortVideoSource[]>(FILE_ID_SOURCES);
  const [userInputEnabled, setUserInputEnabled] = useState(true);
  const [playModeValue, setPlayModeValue] = useState(
    TuiplayerListPlayMode.MODE_ONE_LOOP ?? 1
  );
  const [page, setPage] = useState(1);
  const [hasMore, setHasMore] = useState(true);
  const [loading, setLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchLockRef = useRef(false);
  const isMountedRef = useRef(true);
  const modeRef = useRef<SourceMode>('url');
  const playerRef = useRef<TuiplayerShortVideoViewHandle>(null);
  const hasAutoStartedRef = useRef(false);
  const autoPlayRef = useRef(autoPlay);
  const pausedRef = useRef(paused);

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
    modeRef.current = mode;
  }, [mode]);

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

  useEffect(() => {
    if (mode === 'url' && sources.length === 0) {
      resetSources('url');
    }
    return () => {
      isMountedRef.current = false;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (
      mode === 'url' &&
      sources.length > 0 &&
      !hasAutoStartedRef.current &&
      !loading
    ) {
      triggerInitialAutoPlay();
    }
  }, [loading, mode, sources, triggerInitialAutoPlay]);

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

        if (!isMountedRef.current || modeRef.current !== 'url') {
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
        if (!isMountedRef.current || modeRef.current !== 'url') {
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

  const resetSources = useCallback(
    (targetMode: SourceMode) => {
      setPaused(false);
      hasAutoStartedRef.current = false;
      fetchLockRef.current = false;
      setPage(1);
      setHasMore(true);
      setLoading(false);
      setErrorMessage(null);

      if (targetMode === 'fileId') {
        setSources(FILE_ID_SOURCES);
        return;
      }

      setSources([]);
      loadRemotePage(1, false);
    },
    [loadRemotePage]
  );

  const loadMoreRemote = useCallback(() => {
    if (loading || !hasMore) {
      return;
    }
    loadRemotePage(page + 1, true);
  }, [hasMore, loading, loadRemotePage, page]);

  const handleToggleMode = useCallback(() => {
    setMode((prev) => {
      const nextMode: SourceMode = prev === 'fileId' ? 'url' : 'fileId';
      modeRef.current = nextMode;
      resetSources(nextMode);
      return nextMode;
    });
  }, [resetSources]);

  const handleAppend = useCallback(() => {
    if (mode === 'fileId') {
      setSources((prev) => [...prev, ...FILE_ID_EXTRA]);
      return;
    }
    loadMoreRemote();
  }, [mode, loadMoreRemote]);

  const handleReset = useCallback(() => {
    resetSources(mode);
  }, [mode, resetSources]);

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
    if (mode === 'url') {
      loadMoreRemote();
    }
  }, [loadMoreRemote, mode]);

  const infoText = useMemo(() => {
    const parts = [
      `当前模式：${mode === 'fileId' ? 'FileId' : '接口'}`,
      `自动播放：${autoPlay ? '开启' : '关闭'}`,
      `手动暂停：${paused ? '是' : '否'}`,
      `滑动可用：${userInputEnabled ? '是' : '否'}`,
      `视频数量：${sources.length}`,
    ];
    const listLoop = TuiplayerListPlayMode.MODE_LIST_LOOP ?? 0;
    parts.push(
      `播放模式：${playModeValue === listLoop ? '列表循环' : '单个循环'}`
    );
    if (mode === 'url') {
      parts.push(`页码：${page}`);
      parts.push(`可继续加载：${hasMore ? '是' : '否'}`);
    }
    return parts.join(' / ');
  }, [
    autoPlay,
    hasMore,
    mode,
    page,
    paused,
    playModeValue,
    sources.length,
    userInputEnabled,
  ]);

  const appendButtonLabel = useMemo(() => {
    if (mode === 'fileId') {
      return '追加更多视频';
    }
    if (loading) {
      return '接口数据加载中...';
    }
    if (!hasMore) {
      return '没有更多接口视频';
    }
    return '加载更多接口视频';
  }, [hasMore, loading, mode]);

  const resetButtonLabel = useMemo(
    () => (mode === 'fileId' ? '还原初始列表' : '重新加载接口数据'),
    [mode]
  );

  const statusText = useMemo(() => {
    if (mode !== 'url') {
      return null;
    }
    if (loading) {
      return '接口数据加载中...';
    }
    return hasMore ? '上滑可继续加载更多接口数据' : '已到达接口数据最后一页';
  }, [hasMore, loading, mode]);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <View style={styles.toolbar}>
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
            label={
              mode === 'fileId' ? '切换到接口数据源' : '切换到 FileId 数据源'
            }
            onPress={handleToggleMode}
          />
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
            disabled={mode === 'url' && (loading || !hasMore)}
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
      </View>

      <TuiplayerShortVideoView
        ref={playerRef}
        sources={sources}
        autoPlay={autoPlay}
        style={styles.player}
        paused={paused}
        playMode={playModeValue}
        userInputEnabled={userInputEnabled}
        pageScrollMsPerInch={25}
        onEndReached={handleEndReached}
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

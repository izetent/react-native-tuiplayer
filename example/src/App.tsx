import { useCallback, useEffect, useMemo, useState } from 'react';
import {
  SafeAreaView,
  StatusBar,
  StyleSheet,
  Text,
  View,
  Pressable,
} from 'react-native';
import {
  initialize,
  TuiplayerShortVideoView,
  type ShortVideoSource,
} from 'react-native-tuiplayer';

const LICENSE_URL = '';
const LICENSE_KEY = '';

const FILE_ID_SOURCES: ShortVideoSource[] = [
  {
    type: 'fileId',
    appId: 1500005830,
    fileId: '387702294394366256',
    coverPictureUrl:
      'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/3d98015b387702294394366256/coverBySnapshot/coverBySnapshot_10_0.jpg',
  },
  {
    type: 'fileId',
    appId: 1500005830,
    fileId: '387702294394228858',
    coverPictureUrl:
      'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/3afba900387702294394228858/coverBySnapshot/coverBySnapshot_10_0.jpg',
  },
  {
    type: 'fileId',
    appId: 1500005830,
    fileId: '387702294394228636',
    coverPictureUrl:
      'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/3afba03a387702294394228636/coverBySnapshot/coverBySnapshot_10_0.jpg',
  },
  {
    type: 'fileId',
    appId: 1500005830,
    fileId: '387702294167066523',
    coverPictureUrl:
      'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/6fc8e973387702294167066523/coverBySnapshot/coverBySnapshot_10_0.jpg',
  },
];

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

const URL_SOURCES: ShortVideoSource[] = [
  {
    type: 'url',
    url: 'https://media.w3.org/2010/05/sintel/trailer.mp4',
    coverPictureUrl:
      'https://upload.wikimedia.org/wikipedia/commons/thumb/b/b0/Sintel_poster.jpg/640px-Sintel_poster.jpg',
  },
  {
    type: 'url',
    url: 'https://media.w3.org/2010/05/bunny/trailer.mp4',
    coverPictureUrl: 'https://media.w3.org/2010/05/bunny/poster.png',
  },
];

const URL_EXTRA: ShortVideoSource[] = [
  {
    type: 'url',
    url: 'https://media.w3.org/2010/05/video/movie_300.mp4',
    coverPictureUrl: 'https://media.w3.org/2010/05/video/poster.png',
  },
];

type SourceMode = 'fileId' | 'url';

export default function App() {
  const [autoPlay, setAutoPlay] = useState(true);
  const [mode, setMode] = useState<SourceMode>('fileId');
  const [sources, setSources] = useState<ShortVideoSource[]>(FILE_ID_SOURCES);

  useEffect(() => {
    try {
      initialize({
        licenseKey: LICENSE_KEY,
        licenseUrl: LICENSE_URL,
      });
    } catch (error) {
      console.error('Tuiplayer initialize failed:', error);
    }
  }, []);

  const licenseConfigured = Boolean(LICENSE_KEY && LICENSE_URL);

  const resetSources = useCallback((targetMode: SourceMode) => {
    setSources(targetMode === 'fileId' ? FILE_ID_SOURCES : URL_SOURCES);
  }, []);

  const handleToggleMode = useCallback(() => {
    setMode((prev) => {
      const nextMode: SourceMode = prev === 'fileId' ? 'url' : 'fileId';
      resetSources(nextMode);
      return nextMode;
    });
  }, [resetSources]);

  const handleAppend = useCallback(() => {
    setSources((prev) => {
      const extra = mode === 'fileId' ? FILE_ID_EXTRA : URL_EXTRA;
      return [...prev, ...extra];
    });
  }, [mode]);

  const handleReset = useCallback(() => {
    resetSources(mode);
  }, [mode, resetSources]);

  const infoText = useMemo(() => {
    return `当前模式：${mode === 'fileId' ? 'FileId' : 'URL'} / 自动播放：${
      autoPlay ? '开启' : '关闭'
    } / 视频数量：${sources.length}`;
  }, [autoPlay, mode, sources.length]);

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar barStyle="light-content" />
      <View style={styles.toolbar}>
        <Text style={styles.title}>TUIPlayer 短视频示例</Text>
        <Text style={styles.caption}>{infoText}</Text>
        {!licenseConfigured ? (
          <Text style={styles.warning}>
            ⚠️ 未配置 License，将使用公开示例数据，正式上线请替换为真实
            License。
          </Text>
        ) : null}

        <View style={styles.buttonRow}>
          <ActionButton
            label={
              mode === 'fileId' ? '切换到 URL 数据源' : '切换到 FileId 数据源'
            }
            onPress={handleToggleMode}
          />
          <ActionButton
            label={autoPlay ? '关闭自动播放' : '开启自动播放'}
            onPress={() => setAutoPlay((prev) => !prev)}
          />
        </View>

        <View style={styles.buttonRow}>
          <ActionButton label="追加更多视频" onPress={handleAppend} />
          <ActionButton label="还原初始列表" onPress={handleReset} />
        </View>
      </View>

      <TuiplayerShortVideoView
        sources={sources}
        autoPlay={autoPlay}
        style={styles.player}
      />
    </SafeAreaView>
  );
}

type ActionButtonProps = {
  label: string;
  onPress: () => void;
};

function ActionButton({ label, onPress }: ActionButtonProps) {
  return (
    <Pressable
      onPress={onPress}
      style={({ pressed }) => [
        styles.button,
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
  buttonText: {
    color: '#fff',
    fontSize: 14,
    fontWeight: '500',
  },
  player: {
    flex: 1,
  },
});

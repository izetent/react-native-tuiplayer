import { useEffect, useRef, useState } from 'react';
import {
  SafeAreaView,
  StyleSheet,
  Text,
  TouchableOpacity,
  View,
} from 'react-native';
import {
  RNPlayerShortController,
  RNPlayerView,
  setTUIPlayerConfig,
  TUIVodPlayerController,
} from 'react-native-txplayer';
import type { RNVideoSource } from 'react-native-txplayer';

const VIDEO_URL =
  'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/dc455d1d387702306937256938/adp.10.m3u8';
const COVER_URL =
  'http://1500005830.vod2.myqcloud.com/43843ec0vodtranscq1500005830/dc455d1d387702306937256938/coverBySnapshot_10_0.jpg';
const SUBTITLE_URL =
  'https://mediacloud-76607.gzc.vod.tencent-cloud.com/DemoResource/TED-CN.srt';

const SOURCES: RNVideoSource[] = [
  {
    videoURL: VIDEO_URL,
    coverPictureUrl: COVER_URL,
    subtitleSources: [
      {
        name: 'ex-cn-srt',
        url: SUBTITLE_URL,
        mimeType: 'application/x-subrip',
      },
    ],
  },
];

export default function App() {
  const viewRef = useRef(null);
  const controllerRef = useRef<RNPlayerShortController>(null);
  const playerControllerRef = useRef<TUIVodPlayerController | null>(null);
  const [status, setStatus] = useState('Initializing…');

  useEffect(() => {
    async function initPlayer() {
      try {
        await setTUIPlayerConfig({
          licenseUrl:
            'https://1377187151.trtcube-license.cn/license/v2/1377187151_1/v_cube.license',
          licenseKey: '4ddf62fce4de0a8fe505415a45a27823',
          enableLog: true,
        });
        const controller = new RNPlayerShortController();
        controllerRef.current = controller;
        await controller.setModels(SOURCES);
        const vodController = await controller.bindVodPlayer(viewRef, 0);
        playerControllerRef.current = vodController;
        const startCode = await controller.startCurrent();
        console.log('startCurrent', startCode);
        setStatus('Playing sample stream');
      } catch (error) {
        setStatus(`Init failed: ${String(error)}`);
      }
    }
    initPlayer();
    return () => {
      controllerRef.current?.release();
      controllerRef.current = null;
    };
  }, []);

  const togglePlayback = async () => {
    const player = playerControllerRef.current;
    if (!player) {
      return;
    }
    const isPlaying = await player.isPlaying();
    if (isPlaying) {
      await player.pause();
      setStatus('Paused');
    } else {
      await player.resume();
      setStatus('Playing sample stream');
    }
  };

  return (
    <SafeAreaView style={styles.container}>
      <View style={styles.player}>
        <RNPlayerView ref={viewRef} style={styles.player} />
      </View>
      <Text style={styles.status}>{status}</Text>
      <TouchableOpacity style={styles.button} onPress={togglePlayback}>
        <Text style={styles.buttonText}>Toggle Play / Pause</Text>
      </TouchableOpacity>
      <Text style={styles.tip}>
        Configure your Tencent Cloud TUIPlayer license before running the
        example.
      </Text>
      <View style={styles.links}>
        <Text style={styles.linkLabel}>Video URL</Text>
        <Text style={styles.link} selectable>
          {VIDEO_URL}
        </Text>
        <Text style={[styles.linkLabel, styles.subtitleLabel]}>
          Subtitle URL
        </Text>
        <Text style={styles.link} selectable>
          {SUBTITLE_URL}
        </Text>
      </View>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'flex-end',
  },
  player: {
    flex: 1,
    width: '100%',
  },
  status: {
    color: '#fff',
    textAlign: 'center',
    marginVertical: 12,
  },
  button: {
    alignSelf: 'center',
    backgroundColor: '#1b6ef3',
    paddingHorizontal: 24,
    paddingVertical: 12,
    borderRadius: 24,
    marginBottom: 16,
  },
  buttonText: {
    color: '#fff',
    fontWeight: '600',
  },
  tip: {
    color: '#ccc',
    fontSize: 12,
    textAlign: 'center',
    marginBottom: 24,
    paddingHorizontal: 16,
  },
  links: {
    paddingHorizontal: 16,
    paddingBottom: 24,
  },
  linkLabel: {
    color: '#60a5ff',
    fontSize: 12,
    fontWeight: '600',
    marginTop: 4,
  },
  subtitleLabel: {
    marginTop: 12,
  },
  link: {
    color: '#f1f5f9',
    fontSize: 12,
  },
});

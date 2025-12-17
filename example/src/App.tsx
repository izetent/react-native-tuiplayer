import React, { useEffect, useRef, useState } from 'react';
import { SafeAreaView, StyleSheet, Text, TouchableOpacity, View } from 'react-native';
import {
  FTUIPlayerShortController,
  FTUIPlayerView,
  FTUIVideoSource,
  setTUIPlayerConfig,
  TUIVodPlayerController,
} from 'react-native-txplayer';

const LICENSE_URL = '';
const LICENSE_KEY = '';

const SOURCES: FTUIVideoSource[] = [
  {
    videoURL: 'https://liteavapp.qcloud.com/general/vod_demo/vod-demo.mp4',
    coverPictureUrl: 'https://liteavapp.qcloud.com/general/vod_demo/vod-cover.png',
  },
];

export default function App() {
  const viewRef = useRef(null);
  const controllerRef = useRef<FTUIPlayerShortController>();
  const playerControllerRef = useRef<TUIVodPlayerController | null>(null);
  const [status, setStatus] = useState('Initializing…');

  useEffect(() => {
    async function initPlayer() {
      try {
        await setTUIPlayerConfig({
          licenseUrl: LICENSE_URL,
          licenseKey: LICENSE_KEY,
        });
        const controller = new FTUIPlayerShortController();
        controllerRef.current = controller;
        await controller.setModels(SOURCES);
        const vodController = await controller.bindVodPlayer(viewRef, 0);
        playerControllerRef.current = vodController;
        await controller.startCurrent();
        setStatus('Playing sample stream');
      } catch (error) {
        setStatus(`Init failed: ${String(error)}`);
      }
    }
    initPlayer();
    return () => {
      controllerRef.current?.release();
      controllerRef.current = undefined;
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
        <FTUIPlayerView ref={viewRef} style={styles.player} />
      </View>
      <Text style={styles.status}>{status}</Text>
      <TouchableOpacity style={styles.button} onPress={togglePlayback}>
        <Text style={styles.buttonText}>Toggle Play / Pause</Text>
      </TouchableOpacity>
      <Text style={styles.tip}>
        Configure your Tencent Cloud TUIPlayer license before running the example.
      </Text>
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
});

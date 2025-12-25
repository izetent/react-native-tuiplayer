import { useEffect, useRef } from 'react';
import { SafeAreaView, StyleSheet } from 'react-native';
import {
  RNPlayerShortController,
  RNPlayerView,
  setTUIPlayerConfig,
  TUIVodPlayerController,
} from 'react-native-txplayer';
import type { RNVideoSource } from 'react-native-txplayer';

const VIDEO_URL =
  'https://gz001-1377187151.cos.ap-guangzhou.myqcloud.com/short1080/S00379-The%20Double%20Life/1.mp4';
const COVER_URL =
  'https://gz001-1377187151.cos.ap-guangzhou.myqcloud.com/short1080/S00379-The%20Double%20Life/S00379-The%20Double%20Life.jpg';
const SUBTITLE_URL =
  'https://cdn.sparktube.top/SRT/S00269-My%20Sister%20Stole%20My%20Man/1_zh.vtt';

const SOURCES: RNVideoSource[] = [
  {
    videoURL: VIDEO_URL,
    coverPictureUrl: COVER_URL,
    subtitleSources: [
      {
        name: 'ex-cn-srt',
        url: SUBTITLE_URL,
        mimeType: 'text/vtt',
      },
    ],
  },
];

export default function App() {
  const viewRef = useRef(null);
  const controllerRef = useRef<RNPlayerShortController>(null);
  const playerControllerRef = useRef<TUIVodPlayerController | null>(null);

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
        vodController.addListener({
          // onVodPlayerEvent: (event) =>
          //   console.log('vod event', event?.event, event),
          onVodControllerBind: () => console.log('vod controller bind'),
          onVodControllerUnBind: () => console.log('vod controller unbind'),
          onSubtitleTracksUpdate: (tracks) =>
            console.log('subtitle tracks', tracks),
        });
        await new Promise((resolve) => setTimeout(resolve, 100));
        const startCode = await controller.startCurrent();
        console.log('startCurrent', startCode);
        const playing = await vodController.isPlaying();
        const source = SOURCES[0];
        console.log('playing=======', playing, source);
        if (!playing && source) {
          await vodController.startPlay(source);
          console.log('fallback startPlay triggered');
        }
      } catch (error) {
        console.log('error=', error);
      }
    }
    initPlayer();
    return () => {
      controllerRef.current?.release();
      controllerRef.current = null;
    };
  }, []);

  // const togglePlayback = async () => {
  //   const player = playerControllerRef.current;
  //   if (!player) {
  //     return;
  //   }
  //   const isPlaying = await player.isPlaying();
  //   if (isPlaying) {
  //     await player.pause();
  //   } else {
  //     await player.resume();
  //   }
  // };

  return (
    <SafeAreaView style={styles.container}>
      <RNPlayerView ref={viewRef} style={styles.player} collapsable={false} />
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: '#000',
    justifyContent: 'center',
  },
  player: {
    ...StyleSheet.absoluteFill,
    bottom: 1,
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

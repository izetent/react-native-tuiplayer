/**
 * 原生分辨率偏好设置，包含宽高。
 */
export type PreferredResolution = {
  width: number;
  height: number;
};

/**
 * 点播策略配置，控制预加载与渲染行为。
 */
export type VodStrategyOptions = {
  /** 最大并发预加载数量，默认 3。 */
  preloadCount?: number;
  /** 预下载缓存大小，单位 MB，默认 1。 */
  preDownloadSize?: number;
  /** 起播前需缓存的大小，单位 MB，默认 0.5。 */
  preLoadBufferSize?: number;
  /** 播放过程最大缓存大小，单位 MB，默认 10。 */
  maxBufferSize?: number;
  /** 优先起播的分辨率。 */
  preferredResolution?: PreferredResolution;
  /** 播放进度回调间隔，单位毫秒，默认 500。 */
  progressInterval?: number;
  /** 渲染模式常量，0 全屏裁剪，1 按比例适配。 */
  renderMode?: number;
  /** 媒资类型提示，用于加速探测。 */
  mediaType?: number;
  /** 续播模式，可传 NONE / RESUME_LAST / RESUME_PLAYED 或原生常量。 */
  resumeMode?: 'NONE' | 'RESUME_LAST' | 'RESUME_PLAYED' | number;
  /** 是否开启码率自适应。 */
  enableAutoBitrate?: boolean;
  /** 是否开启精准 seek（更准但耗时）。 */
  enableAccurateSeek?: boolean;
  /** 音量均衡目标响度。 */
  audioNormalization?: number;
  /** 切换时是否保留前一个点播信息。 */
  retainPreVod?: boolean;
  /** 超分模式常量或名称。 */
  superResolutionMode?:
    | number
    | 'SUPER_RESOLUTION_NONE'
    | 'SUPER_RESOLUTION_ASR';
  /** 弱网下重试次数，0 表示不重试。 */
  retryCount?: number;
  /** 预播放策略，可传 NONE / NEXT / PREVIOUS / ADJACENT 或原生常量。 */
  prePlayStrategy?:
    | 'TUIPrePlayStrategyNone'
    | 'TUIPrePlayStrategyNext'
    | 'TUIPrePlayStrategyPrevious'
    | 'TUIPrePlayStrategyAdjacent'
    | string;
};

/**
 * 直播播放策略配置。
 */
export type LiveStrategyOptions = {
  /** 直播渲染模式常量。 */
  renderMode?: number;
  /** 切换直播时是否保留之前信息。 */
  retainPreLive?: boolean;
  /** 直播预播放策略。 */
  prePlayStrategy?:
    | 'TUIPrePlayStrategyNone'
    | 'TUIPrePlayStrategyNext'
    | 'TUIPrePlayStrategyPrevious'
    | 'TUIPrePlayStrategyAdjacent'
    | string;
};

/**
 * 单个短视频源的细粒度配置。
 */
export type ShortVideoSourceConfig = {
  /** 覆盖该视频的预播放缓存大小（MB）。 */
  preloadBufferSizeInMB?: number;
  /** 覆盖该视频的预下载缓存大小（MB）。 */
  preDownloadSize?: number;
};

/**
 * 覆盖在视频上的额外展示信息，对应 Android 覆盖图层。
 */
export type ShortVideoOverlayMeta = {
  /** 作者/发布者名称。 */
  authorName?: string;
  /** 作者头像 URL。 */
  authorAvatar?: string;
  /** 视频标题或简介。 */
  title?: string;
  /** 点赞数量。 */
  likeCount?: number;
  /** 评论数量。 */
  commentCount?: number;
  /** 收藏/追剧数量。 */
  favoriteCount?: number;
  /** 当前是否已点赞。 */
  isLiked?: boolean;
  /** 当前是否已收藏。 */
  isBookmarked?: boolean;
};

/**
 * 单条短视频数据模型。
 */
export type ShortVideoSource = {
  /** 资源类型：默认 `fileId`，也可使用 `url`。 */
  type?: 'fileId' | 'url';
  /** 点播 appId，使用 fileId 播放时必填。 */
  appId?: number;
  /** 点播 fileId。 */
  fileId?: string;
  /** 直接播放的 URL。 */
  url?: string;
  /** 播放前显示的封面图，Android 默认附带封面层展示。 */
  coverPictureUrl?: string;
  /** 加密 fileId 使用的 pSign。 */
  pSign?: string;
  /** 自定义原生页面类型，用于区分不同布局。 */
  extViewType?: number;
  /** 是否覆写该视频的自动播放行为。 */
  autoPlay?: boolean;
  /** 该视频独立的缓冲配置。 */
  videoConfig?: ShortVideoSourceConfig;
  /** 覆盖 UI 所需的额外信息（Android 默认展示）。 */
  meta?: ShortVideoOverlayMeta;
};

/**
 * 初始化 TUIPlayer 所需的 License 配置。
 */
export type TuiplayerLicenseConfig = {
  licenseUrl?: string;
  licenseKey?: string;
  enableLog?: boolean;
};

/**
 * 原生图层配置，指定要挂载的层类名。
 */
export type TuiplayerLayerConfig = {
  /** VOD 渲染层的完整类名列表。 */
  vodLayers?: string[];
  /** Live 渲染层的完整类名列表。 */
  liveLayers?: string[];
  /** 自定义渲染层的完整类名列表。 */
  customLayers?: string[];
};

export type TuiplayerVodStrategyOptions = VodStrategyOptions;
export type TuiplayerLiveStrategyOptions = LiveStrategyOptions;

export type {
  ShortVideoSourcePayload as ShortVideoSourceSnapshot,
  CurrentShortVideoInfo,
} from './NativeTuiplayer';

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
  /** 渲染模式常量：0 拉伸铺满、1 等比适配。 */
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
 * 覆盖在视频上的额外展示信息,对应 Android 覆盖图层。
 */
export type ShortVideoOverlayMeta = {
  /** 视频名称/标题。 */
  name?: string;
  /** 视频图标/封面 URL。 */
  icon?: string;
  /** 视频类型/标签列表。 */
  type?: string | string[];
  /** 视频详情描述。 */
  details?: string;
  /** 点赞数量。 */
  likeCount?: number;
  /** 收藏/追剧数量。 */
  favoriteCount?: number;
  /** 评论数量（iOS 旧信息层仍会使用）。 */
  commentCount?: number;
  /** 是否显示播放按钮。 */
  isShowPaly?: boolean;
  /** 当前是否已点赞。 */
  isLiked?: boolean;
  /** 当前是否已收藏。 */
  isBookmarked?: boolean;
  /** 是否已关注；iOS 旧信息层使用。 */
  isFollowed?: boolean;
  /** Android 信息层是否展示封面/图片。 */
  showCover?: boolean;
  /** 播放按钮自定义文案。 */
  playText?: string;
  /** “查看更多”一类的提示文案。 */
  watchMoreText?: string;
  /** “更多”按钮自定义文案；未提供时退回 watchMoreText 或默认值。 */
  moreText?: string;
  /** 兼容旧字段：作者名称。 */
  authorName?: string;
  /** 兼容旧字段：作者头像。 */
  authorAvatar?: string;
  /** 兼容旧字段：信息层标题。 */
  title?: string;
};

/**
 * 外挂字幕源定义。
 */
export type ShortVideoSubtitle = {
  /** 字幕唯一名称，用于区分不同轨道。 */
  name?: string;
  /** 字幕文件地址，支持 VTT/SRT。 */
  url: string;
  /** 字幕类型，接受 text/vtt、text/srt 或 vtt/srt 简写。 */
  mimeType?: 'text/vtt' | 'text/srt' | 'vtt' | 'srt' | string;
};

/**
 * 字幕渲染样式配置。
 */
export type TuiplayerSubtitleStyle = {
  /** 自定义画布宽度，单位像素。 */
  canvasWidth?: number;
  /** 自定义画布高度，单位像素。 */
  canvasHeight?: number;
  /** 自定义字体族名称。 */
  familyName?: string;
  /** 固定字体大小。 */
  fontSize?: number;
  /** 字体缩放比例。 */
  fontScale?: number;
  /** 字体颜色。 */
  fontColor?: number | string;
  /** 是否加粗。 */
  bold?: boolean;
  /** 描边宽度。 */
  outlineWidth?: number;
  /** 描边颜色。 */
  outlineColor?: number | string;
  /** 行距。 */
  lineSpace?: number;
  /** 左侧/起始方向的外边距，0-1 之间的小数。 */
  startMargin?: number;
  /** 右侧/结束方向的外边距，0-1 之间的小数。 */
  endMargin?: number;
  /** 垂直方向外边距，0-1 之间的小数。 */
  verticalMargin?: number;
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
  /** 外挂字幕轨道列表。 */
  subtitles?: ShortVideoSubtitle[];
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

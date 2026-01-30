require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "Txplayer"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/Mahuoooo/react-native-tuiplayer.git", :tag => "#{s.version}" }

  s.platforms    = { :ios => min_ios_version_supported }
  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.private_header_files = "ios/**/*.h"
  s.static_framework = true

  install_modules_dependencies(s)

  s.libraries = "sqlite3", "z"
  s.pod_target_xcconfig = { "OTHER_LDFLAGS" => "-lObjC" }

  podspec_root = File.expand_path(__dir__)
  relative_path = lambda do |path|
    path.start_with?(podspec_root + "/") ? path.sub("#{podspec_root}/", "") : path
  end

  sdk_root = ENV["TUIPLAYERKIT_IOS_SDK_ROOT"] || File.join(__dir__, "ios")
  sdk_root = File.expand_path(sdk_root, __dir__)
  core_xcframework = File.join(sdk_root, "TUIPlayerCoreSDK", "SDKProduct", "TUIPlayerCore.xcframework")
  short_xcframework = File.join(
    sdk_root,
    "TUIPlayerShortVideoSDK",
    "SDKProduct",
    "TUIPlayerShortVideo.xcframework"
  )
  core_privacy = File.join(
    sdk_root,
    "TUIPlayerCoreSDK",
    "SDKProduct",
    "TUIPlayerCore.xcframework",
    "TUIPlayerCore-Privacy.bundle"
  )
  short_privacy = File.join(
    sdk_root,
    "TUIPlayerShortVideoSDK",
    "SDKProduct",
    "TUIPlayerShortVideo.xcframework",
    "TUIPlayerShortVideo-Privacy.bundle"
  )
  short_bundle = File.join(sdk_root, "TUIPlayerShortVideoSDK", "TUIPlayerShortVideo.bundle")

  local_frameworks = []
  local_resources = []
  if File.exist?(core_xcframework) && File.exist?(short_xcframework)
    local_frameworks.concat([
      relative_path.call(core_xcframework),
      relative_path.call(short_xcframework),
    ])
    local_resources << relative_path.call(core_privacy) if File.exist?(core_privacy)
    local_resources << relative_path.call(short_privacy) if File.exist?(short_privacy)
    local_resources << relative_path.call(short_bundle) if File.exist?(short_bundle)
    s.dependency "Masonry"
    s.dependency "SDWebImage"
  else
    s.dependency "TUIPlayerCore/Player_Premium"
    s.dependency "TUIPlayerShortVideo/Player_Premium"
  end

  liteav_root = ENV["TXLITEAVSDK_ROOT"] || File.join(__dir__, "ios", "TXLiteAVSDK_Player_Premium")
  liteav_root = File.expand_path(liteav_root, __dir__)
  liteav_frameworks = Dir.glob(File.join(liteav_root, "**", "*.{xcframework,framework}"))
  liteav_bundles = Dir.glob(File.join(liteav_root, "**", "*.bundle"))
  if liteav_frameworks.empty?
    s.dependency "TXLiteAVSDK_Player_Premium"
  else
    liteav_frameworks.each do |path|
      local_frameworks << relative_path.call(path)
    end
    liteav_bundles.each do |path|
      local_resources << relative_path.call(path)
    end
  end

  s.vendored_frameworks = local_frameworks unless local_frameworks.empty?
  s.resources = local_resources unless local_resources.empty?
  # Super resolution/Monet disabled for now to avoid TSR dependency.
  # s.dependency "TXCMonetPlugin"
  # s.dependency "tsrClient"
end

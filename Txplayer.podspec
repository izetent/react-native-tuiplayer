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
  s.source       = { :git => "https://github.com/Mahuoooo/react-native-txplayer.git", :tag => "#{s.version}" }

  s.platforms    = { :ios => min_ios_version_supported }
  s.source_files = "ios/**/*.{h,m,mm,swift,cpp}"
  s.private_header_files = "ios/**/*.h"
  s.static_framework = true

  install_modules_dependencies(s)

  s.dependency "TXLiteAVSDK_Player_Premium"
  s.dependency "TUIPlayerCore/Player_Premium"
  s.dependency "TUIPlayerShortVideo/Player_Premium"
  s.dependency "TXCMonetPlugin"
  s.dependency "tsrClient"
end

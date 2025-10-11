require "json"

package = JSON.parse(File.read(File.join(__dir__, "package.json")))

Pod::Spec.new do |s|
  s.name         = "Tuiplayer"
  s.version      = package["version"]
  s.summary      = package["description"]
  s.homepage     = package["homepage"]
  s.license      = package["license"]
  s.authors      = package["author"]

  s.platforms    = { :ios => min_ios_version_supported }
  s.source       = { :git => "https://github.com/Mahuoooo/react-native-tuiplayer.git", :tag => "#{s.version}" }

  s.swift_version = "5.0"
  s.source_files = "ios/**/*.{h,m,mm,swift}"
  s.private_header_files = "ios/**/*.h"
  s.vendored_frameworks = [
    "ios/Frameworks/TUIPlayerCore.xcframework",
    "ios/Frameworks/TUIPlayerShortVideo.xcframework"
  ]
  s.resources = [
    "ios/Frameworks/TUIPlayerCore.xcframework/**/TUIPlayerCore-Privacy.bundle",
    "ios/Frameworks/TUIPlayerShortVideo.xcframework/**/TUIPlayerShortVideo-Privacy.bundle"
  ]
  s.frameworks = [
    "AVFoundation",
    "UIKit",
    "Foundation"
  ]
  s.libraries = "c++"


  install_modules_dependencies(s)
end

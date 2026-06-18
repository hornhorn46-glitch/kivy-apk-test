[app]
title = Reaction Tracker
package.name = reactiontracker
package.domain = com.hornhorn46
source.dir = .
source.include_exts = py,png,jpg,kv,atlas,json
version = 0.1.0
requirements = python3,kivy
orientation = portrait
fullscreen = 0

[buildozer]
log_level = 2
warn_on_root = 1

[android]
api = 35
minapi = 23
ndk = 25b
android.accept_sdk_license = True
archs = arm64-v8a, armeabi-v7a
android.permissions = INTERNET

[p4a]

[ios]

[osx]

[linux]

#!/usr/bin/env bash
# Build helpers. Everything except `install` runs inside the Docker image, so no JDK,
# Android SDK or Gradle is needed on the host.
set -euo pipefail
cd "$(dirname "$0")"

GRADLE_VERSION=8.10.2
APK=app/build/outputs/apk/debug/app-debug.apk
RELEASE_APK=app/build/outputs/apk/release/app-release.apk
APKSIGNER=/opt/android-sdk/build-tools/35.0.0/apksigner

in_container() { docker compose run --rm android "$@"; }

# versionName, read from the one place it is declared.
app_version() { sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts; }

case "${1:-help}" in
  setup)
    # One-off: build the image, generate the Gradle wrapper, create the debug key.
    docker compose build
    in_container gradle wrapper --gradle-version "$GRADLE_VERSION"
    "$0" keystore
    echo "Ready. Next: ./dev.sh build"
    ;;

  keystore)
    if [ "${2:-debug}" = "release" ]; then
      # The publishing key. Never committed: .keystore/ and keystore.properties are
      # both gitignored. Back them up — Android identifies an app by its signing key,
      # so losing this means no future build can update an installed copy.
      if [ -f .keystore/release.keystore ]; then
        echo "Release keystore already exists — refusing to overwrite it." >&2
        exit 1
      fi
      mkdir -p .keystore
      pw=$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 32)
      in_container keytool -genkeypair -v \
        -keystore .keystore/release.keystore \
        -storetype PKCS12 \
        -storepass "$pw" -keypass "$pw" \
        -alias smsmirror \
        -keyalg RSA -keysize 4096 -validity 10950 \
        -dname "CN=SMS Mirror, O=SMS Mirror, C=US"
      cat > keystore.properties <<EOF
# Release signing credentials. NOT in git — back this file up together with
# .keystore/release.keystore; losing either means future builds cannot be installed
# as an update over an existing one.
storeFile=.keystore/release.keystore
storePassword=$pw
keyAlias=smsmirror
keyPassword=$pw
EOF
      echo "Created .keystore/release.keystore and keystore.properties — back both up."
      exit 0
    fi
    # A stable debug key checked out of the container's disposable filesystem, so
    # rebuilt APKs stay installable over each other. Same conventional password AGP
    # uses, so Android Studio accepts it too.
    if [ -f .keystore/debug.keystore ]; then
      echo "Debug keystore already exists."
    else
      mkdir -p .keystore
      in_container keytool -genkeypair -v \
        -keystore .keystore/debug.keystore \
        -storepass android -keypass android \
        -alias androiddebugkey \
        -keyalg RSA -keysize 2048 -validity 10950 \
        -dname "CN=Android Debug,O=Android,C=US"
      echo "Created .keystore/debug.keystore"
    fi
    ;;
  build)   in_container ./gradlew assembleDebug ;;
  release) in_container ./gradlew assembleRelease ;;
  test)    in_container ./gradlew testDebugUnitTest ;;
  clean)   in_container ./gradlew clean ;;
  shell)   docker compose run --rm -it android bash ;;

  dist)
    # The published artifact: a signed release APK under dist/, the one path
    # .gitignore lets an APK through, so it can be committed with the release tag.
    "$0" release
    version=$(app_version)
    mkdir -p dist
    cp "$RELEASE_APK" "dist/sms-mirror-${version}.apk"
    # Signature scheme v2, not the old JAR signing: minSdk 26 means AGP skips v1
    # entirely, so there is no META-INF/*.RSA to look for. apksigner is the check.
    if in_container "$APKSIGNER" verify "dist/sms-mirror-${version}.apk" >/dev/null 2>&1; then
      echo "dist/sms-mirror-${version}.apk (signed)"
    else
      echo "dist/sms-mirror-${version}.apk is UNSIGNED — no keystore.properties." >&2
      echo "Run ./dev.sh keystore release, then ./dev.sh dist again." >&2
      exit 1
    fi
    ;;

  install)
    # adb runs on the host: Docker Desktop on macOS can't reach USB devices or the
    # emulator, so the container builds and the host installs.
    [ -f "$APK" ] || { echo "No APK yet — run ./dev.sh build" >&2; exit 1; }
    adb install -r "$APK"
    ;;
  logs)    adb logcat -s SmsReceiver:V ForwardWorker:V WM-WorkerWrapper:I ;;
  sms)     adb emu sms send "${2:-+15551234567}" "${3:-Your code is 123456}" ;;

  *)
    cat <<'USAGE'
./dev.sh <command>

  setup     build the Docker image and generate ./gradlew   (run once)
  build     assemble the debug APK
  release   assemble the release APK
  dist      release + copy to dist/sms-mirror-<version>.apk  (the published build)
  test      run JVM unit tests
  clean     gradle clean
  shell     interactive shell in the build container

  keystore release   create the release signing key         (run once, then back it up)

  install   adb install the debug APK          (host adb, device/emulator)
  sms       adb emu sms send <number> <text>   (emulator only)
  logs      tail the app's logcat tags
USAGE
    ;;
esac

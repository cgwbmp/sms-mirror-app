# Project-scoped Android build toolchain.
# Nothing lands on the host: JDK, Android SDK and Gradle all live in this image.
#
# amd64 is deliberate — Google ships no arm64-Linux build-tools (aapt2, d8), so on
# Apple Silicon this runs under Rosetta. Enable it in Docker Desktop:
#   Settings > General > "Use Rosetta for x86/amd64 emulation on Apple Silicon"
FROM --platform=linux/amd64 eclipse-temurin:17-jdk-jammy

ENV ANDROID_SDK_ROOT=/opt/android-sdk \
    ANDROID_HOME=/opt/android-sdk \
    GRADLE_USER_HOME=/gradle-cache \
    DEBIAN_FRONTEND=noninteractive

RUN apt-get update && apt-get install -y --no-install-recommends \
        curl unzip git ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# --- Android command-line tools -------------------------------------------------
ARG CMDLINE_TOOLS_VERSION=11076708
RUN mkdir -p "$ANDROID_SDK_ROOT/cmdline-tools" \
 && curl -fsSL -o /tmp/tools.zip \
      "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
 && unzip -q /tmp/tools.zip -d "$ANDROID_SDK_ROOT/cmdline-tools" \
 && mv "$ANDROID_SDK_ROOT/cmdline-tools/cmdline-tools" "$ANDROID_SDK_ROOT/cmdline-tools/latest" \
 && rm /tmp/tools.zip

ENV PATH="$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$PATH"

# --- SDK platform + build tools --------------------------------------------------
ARG COMPILE_SDK=35
ARG BUILD_TOOLS=35.0.0
RUN yes | sdkmanager --licenses > /dev/null \
 && sdkmanager --install \
      "platform-tools" \
      "platforms;android-${COMPILE_SDK}" \
      "build-tools;${BUILD_TOOLS}" > /dev/null

# --- Gradle ----------------------------------------------------------------------
# Only needed to bootstrap ./gradlew the first time; after that the wrapper is used.
ARG GRADLE_VERSION=8.10.2
RUN curl -fsSL -o /tmp/gradle.zip \
      "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
 && unzip -q /tmp/gradle.zip -d /opt \
 && rm /tmp/gradle.zip
ENV PATH="/opt/gradle-${GRADLE_VERSION}/bin:$PATH"

WORKDIR /workspace

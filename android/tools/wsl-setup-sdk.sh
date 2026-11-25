#!/bin/bash
# One-time: a minimal Android build toolchain in WSL (no Android Studio, no Gradle):
# OpenJDK 17 (Ubuntu), Google's command-line tools, platform android-28 (Fire OS 7 = Android 9), build-tools 34.
# Run as root in WSL: bash wsl-setup-sdk.sh
set -euo pipefail
export DEBIAN_FRONTEND=noninteractive
command -v javac >/dev/null && command -v zip >/dev/null || { apt-get update -qq; apt-get install -y -qq openjdk-17-jdk-headless unzip zip curl >/dev/null; }
SDK=/root/android-sdk
mkdir -p "$SDK/cmdline-tools"
if [ ! -x "$SDK/cmdline-tools/latest/bin/sdkmanager" ]; then
  cd /tmp
  curl -fsSLo cmdtools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip
  rm -rf cmdline-tools && unzip -q cmdtools.zip && rm -rf "$SDK/cmdline-tools/latest" && mv cmdline-tools "$SDK/cmdline-tools/latest"
fi
yes | "$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" --licenses >/dev/null 2>&1 || true
"$SDK/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$SDK" "platforms;android-28" "build-tools;34.0.0" >/dev/null
javac -version
ls "$SDK/platforms" "$SDK/build-tools"
echo SDK-READY

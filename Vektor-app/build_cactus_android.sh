#!/bin/bash
set -e

# ── Setup env ──────────────────────────────────────────────────────────────
export JAVA_HOME="$HOME/jdk17"
export PATH="$JAVA_HOME/bin:$PATH"
ANDROID_HOME="$HOME/android-sdk"
export ANDROID_HOME
export PATH="$PATH:$ANDROID_HOME/cmdline-tools/latest/bin"

echo "=== Java: $(java -version 2>&1 | head -1) ==="
echo "=== sdkmanager: $(which sdkmanager) ==="

# ── Step 1: Accept licenses and install NDK ────────────────────────────────
echo "=== Accepting licenses ==="
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses 2>&1 | grep -c "accepted" || true

echo "=== Installing NDK 27.2.12479018 ==="
sdkmanager --sdk_root="$ANDROID_HOME" "ndk;27.2.12479018" 2>&1

echo "=== NDK installed: $(ls $ANDROID_HOME/ndk/) ==="

# ── Step 2: Build libcactus.so for Android arm64 ───────────────────────────
echo "=== Building libcactus.so for Android arm64 ==="
export ANDROID_NDK_HOME="$ANDROID_HOME/ndk/27.2.12479018"
cd ~/cactus-work/cactus/android
bash build.sh 2>&1

echo "=== Build complete ==="
ls -lh ~/cactus-work/cactus/android/libcactus.so

# ── Step 3: Copy to Vektor project ─────────────────────────────────────────
echo "=== Copying to Vektor project ==="
cp ~/cactus-work/cactus/android/libcactus.so \
   /mnt/d/Vektor-core/Vektor-app/app/src/main/jniLibs/arm64-v8a/libcactus.so
echo "=== Done! ==="
ls -lh /mnt/d/Vektor-core/Vektor-app/app/src/main/jniLibs/arm64-v8a/

#!/usr/bin/env bash
# Runs Gradle from WSL through the Windows toolchain (Android Studio's JBR + the Windows SDK in
# local.properties). gradlew.bat cannot run on a \\wsl.localhost path, so the checkout is mirrored
# to a Windows directory first; build outputs (APKs, reports) are copied back into ./app/build.
#
#   scripts/gradlew-win.sh assembleDebug testDebugUnitTest lint
set -euo pipefail

repo="$(cd "$(dirname "$0")/.." && pwd)"
win_dir="${TRAINIST_WIN_BUILD_DIR:-/mnt/c/Users/$USER/AppData/Local/Temp/trainist-build}"
java_home="${TRAINIST_WIN_JAVA_HOME:-C:\\Program Files\\Android\\Android Studio\\jbr}"

# .git is mirrored on purpose: versionCode is `git rev-list --count HEAD`.
rsync -a --delete \
    --exclude '/build/' --exclude '/app/build/' --exclude '/.gradle/' \
    --exclude '/design_handoff_*/' \
    "$repo/" "$win_dir/"

status=0
(cd "$win_dir" && cmd.exe /c "set \"JAVA_HOME=$java_home\" && gradlew.bat $* --console=plain") || status=$?

for out in outputs reports; do
    [ -d "$win_dir/app/build/$out" ] || continue
    mkdir -p "$repo/app/build/$out"
    rsync -a "$win_dir/app/build/$out/" "$repo/app/build/$out/"
done
exit "$status"

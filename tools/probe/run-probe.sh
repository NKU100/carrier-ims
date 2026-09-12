#!/usr/bin/env bash
# Builds the probe, pushes it, and runs it as the shell uid via app_process.
#
# Set ANDROID_HOME. Set DEVICE to a serial when more than one device is attached.
#
# Under Git Bash the SDK tools are Windows executables, so every host path handed
# to them goes through cygpath; MSYS_NO_PATHCONV keeps adb's device-side paths intact.
set -euo pipefail
export MSYS_NO_PATHCONV=1

SDK=${ANDROID_HOME:-${ANDROID_SDK_ROOT:-}}
if [ -z "$SDK" ]; then
    echo "set ANDROID_HOME (or ANDROID_SDK_ROOT) to your Android SDK" >&2
    exit 2
fi

if command -v cygpath > /dev/null; then
    win() { cygpath -w "$1"; }
    d8_name=d8.bat
else
    win() { printf '%s' "$1"; }
    d8_name=d8
fi

# Default to the newest installed platform and build-tools; override PLATFORM or D8 to pin one.
PLATFORM=${PLATFORM:-$(ls -d "$SDK"/platforms/android-* | sort -V | tail -1)/android.jar}
D8=${D8:-$(ls "$SDK"/build-tools/*/"$d8_name" | sort -V | tail -1)}

adb=(adb)
if [ -n "${DEVICE:-}" ]; then
    adb=(adb -s "$DEVICE")
fi

HERE=$(cd "$(dirname "$0")" && pwd)
OUT="$HERE/../../build/probe"

rm -rf "$OUT" && mkdir -p "$OUT"
javac -nowarn -source 8 -target 8 -bootclasspath "$(win "$PLATFORM")" -d "$(win "$OUT")" \
    "$(win "$HERE/CarrierConfigProbe.java")"
"$D8" --release --min-api 26 --lib "$(win "$PLATFORM")" --output "$(win "$OUT")" \
    "$(win "$OUT/CarrierConfigProbe.class")"

"${adb[@]}" push "$(win "$OUT/classes.dex")" /data/local/tmp/carrierims-probe.dex > /dev/null

SUBID=${SUBID:-$("${adb[@]}" shell 'dumpsys telephony.registry | grep -oE "mSubId=[0-9]+" | head -1' | tr -d '\r' | cut -d= -f2)}
SUBID=${SUBID:-1}

"${adb[@]}" shell \
    "CLASSPATH=/data/local/tmp/carrierims-probe.dex app_process / CarrierConfigProbe $SUBID"

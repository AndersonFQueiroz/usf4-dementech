#!/usr/bin/env bash
# Dementech USF4 — build do APK a partir do APK base (ReXGlue, build 0.3.0).
#
# Uso:
#   BASE_APK=/caminho/USF4-0.3.0.apk tools/build.sh
#
# Variáveis:
#   BASE_APK    APK base de entrada (obrigatório)
#   OUT         APK de saída (default: dist/dementech-usf4.apk)
#   KS          keystore de assinatura (default: ~/keys/carbon.jks)
#   KS_ALIAS    alias da chave (default: t)
#   KS_PASS     senha do keystore/chave (default: android)
#
# Requisitos: apktool 2.7+ (com --use-aapt2), build-tools (aapt2, apksigner),
# java 17 no PATH.
set -euo pipefail

cd "$(dirname "$0")/.."
ROOT=$PWD

BASE_APK=${BASE_APK:?defina BASE_APK com o caminho do APK base}
OUT=${OUT:-$ROOT/dist/dementech-usf4.apk}
KS=${KS:-$HOME/keys/carbon.jks}
KS_ALIAS=${KS_ALIAS:-t}
KS_PASS=${KS_PASS:-android}
BT=${BUILD_TOOLS:-/root/android-sdk/build-tools/35.0.0}

export PATH="$BT:$PATH"

rm -rf "$ROOT/build/dec"
mkdir -p "$ROOT/build" "$(dirname "$OUT")"

echo "== decode"
apktool d "$BASE_APK" -o "$ROOT/build/dec" -f -q

echo "== patches"
cd "$ROOT/build/dec"
for p in "$ROOT"/patches/*.patch; do
  echo "   $(basename "$p")"
  patch -p1 -s < "$p"
done

echo "== assets de marca"
rm -f assets/links/belmante.jpg assets/links/bruno.jpg
cp "$ROOT/assets/brand/launcher_logo.png" assets/launcher/logo.png
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  cp "$ROOT/assets/brand/ic_launcher_$d.png" "res/mipmap-$d/ic_launcher.png"
done
cd "$ROOT"

echo "== build"
apktool b build/dec --use-aapt2 -o "$OUT"

echo "== assinatura"
apksigner sign \
  --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  "$OUT"

apksigner verify --print-certs "$OUT" | head -3
ls -la "$OUT"

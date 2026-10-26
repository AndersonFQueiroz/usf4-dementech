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
ANDROID_JAR=${ANDROID_JAR:-/root/android-sdk/platforms/android-35/android.jar}

export PATH="$BT:$PATH"

rm -rf "$ROOT/build/dec"
mkdir -p "$ROOT/build" "$(dirname "$OUT")"

echo "== decode"
apktool d "$BASE_APK" -o "$ROOT/build/dec" -f -q

echo "== patches"
cd "$ROOT/build/dec"
if grep -rq "dementech.invalid" smali*/ 2>/dev/null; then
  echo "   (base ja e Dementech - patches ignorados)"
else
  for p in "$ROOT"/patches/*.patch; do
    echo "   $(basename "$p")"
    patch -p1 -s < "$p"
  done
fi

# versao Dementech (idempotente)
python3 - <<'PY'
import re
p = 'apktool.yml'
s = open(p).read()
s = re.sub(r"versionName: .*", "versionName: 1.5", s, count=1)
s = re.sub(r"versionCode: .*", "versionCode: 6", s, count=1)
open(p, 'w').write(s)
PY

echo "== limpeza de resíduos do autor"
rm -f assets/links/belmante.jpg assets/links/bruno.jpg
rm -f res/drawable-nodpi/channel_belmante.png res/drawable-nodpi/channel_bruno.png

# remove as entradas correspondentes da tabela publica de recursos
python3 - <<'PY'
p = 'res/values/public.xml'
lines = [l for l in open(p, encoding='utf-8')
         if 'channel_belmante' not in l and 'channel_bruno' not in l]
open(p, 'w', encoding='utf-8').writelines(lines)
PY

# caminhos de build embutidos nos .so (mesmo comprimento, offsets intactos)
python3 - <<'PY'
import glob
for f in glob.glob('lib/arm64-v8a/*.so'):
    d = open(f,'rb').read()
    if b'C:/Users/Belmante' not in d: continue
    n = d.count(b'C:/Users/Belmante')
    d2 = d.replace(b'C:/Users/Belmante', b'C:/Users/Anderson')
    assert len(d2) == len(d), f
    open(f,'wb').write(d2)
    print('   %s: %d caminhos' % (f, n))
PY

echo "== assets de marca"
cp "$ROOT/assets/brand/launcher_logo.png" assets/launcher/logo.png
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  cp "$ROOT/assets/brand/ic_launcher_$d.png" "res/mipmap-$d/ic_launcher.png"
done
cd "$ROOT"

echo "== updater (Bootstrap + KeepAlive)"
rm -rf "$ROOT/build/dexjava" "$ROOT/build/dex"
mkdir -p "$ROOT/build/dexjava" "$ROOT/build/dex"
javac --release 8 -nowarn -classpath "$ANDROID_JAR" -d "$ROOT/build/dexjava" \
  $(find "$ROOT/java" -name "*.java")
"$BT/d8" --release --min-api 29 --lib "$ANDROID_JAR" --output "$ROOT/build/dex" \
  $(find "$ROOT/build/dexjava" -name "*.class")
cp "$ROOT/build/dex/classes.dex" "$ROOT/build/dex/classes6.dex"

python3 - <<'PY'
import glob, re

# hook no LauncherActivity.onCreate
for p in glob.glob('build/dec/smali*/io/github/belmantegu/usfivrecomp/LauncherActivity.smali'):
    s = open(p).read()
    if 'com/dementech/update/Bootstrap' in s:
        print('   hook ja presente')
        break
    m = re.search(r'(\.method protected onCreate\(Landroid/os/Bundle;\)V\n(?:.*\n)*?'
                  r'\s*invoke-super \{p0, p1\}, Landroid/app/Activity;->onCreate\(Landroid/os/Bundle;\)V\n)', s)
    if m:
        s = s.replace(m.group(1), m.group(1) +
                      '\n    invoke-static {p0}, Lcom/dementech/update/Bootstrap;->check(Landroid/app/Activity;)V\n', 1)
        open(p, 'w').write(s)
        print('   hook LauncherActivity ok')
    break

# hook da barra (depois do setContentView)
for p in glob.glob('build/dec/smali*/io/github/belmantegu/usfivrecomp/LauncherActivity.smali'):
    s = open(p).read()
    if 'com/dementech/update/Bar' in s:
        print('   bar ja presente')
        break
    m = re.search(r'(invoke-virtual \{p0, v0\}, Lio/github/belmantegu/usfivrecomp/LauncherActivity;->setContentView\(Landroid/view/View;\)V\n)', s)
    if m:
        s = s.replace(m.group(1), m.group(1) +
                      '\n    invoke-static {p0}, Lcom/dementech/update/Bar;->attach(Landroid/app/Activity;)V\n', 1)
        open(p, 'w').write(s)
        print('   bar LauncherActivity ok')
    break

# nao mostrar o card "Jogo nao encontrado" enquanto a barra cuida do download
for p in glob.glob('build/dec/smali*/io/github/belmantegu/usfivrecomp/LauncherActivity.smali'):
    s = open(p).read()
    if 'Downloader;->pending' in s:
        print('   card guard ja presente')
        break
    needle = '    if-ne v0, v4, :cond_5\n'
    if needle in s:
        s = s.replace(needle, needle +
            '\n    invoke-static {}, Lcom/dementech/update/Downloader;->pending()Z\n'
            '    move-result v7\n'
            '    if-nez v7, :cond_5\n'
            '    goto :goto_5\n', 1)
        open(p, 'w').write(s)
        print('   card guard ok')
    break

# permissoes + servico
p = 'build/dec/AndroidManifest.xml'
s = open(p).read()
if 'FOREGROUND_SERVICE_DATA_SYNC' not in s:
    s = s.replace('<uses-permission android:name="android.permission.INTERNET"/>',
        '<uses-permission android:name="android.permission.INTERNET"/>'
        '<uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>'
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>'
        '<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC"/>'
        '<uses-permission android:name="android.permission.WAKE_LOCK"/>'
        '<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS"/>', 1)
if 'Bootstrap$KeepAlive' not in s:
    s = s.replace('</application>',
        '<service android:name="com.dementech.update.Bootstrap$KeepAlive" '
        'android:exported="false" android:stopWithTask="false" android:foregroundServiceType="dataSync"/>'
        '<activity android:name="com.dementech.update.DownloadActivity" '
        'android:exported="false" android:theme="@android:style/Theme.Material.NoActionBar"/>'
        '</application>')
open(p, 'w').write(s)
print('   manifest updater ok')
PY

echo "== build"
apktool b build/dec --use-aapt2 -o "$OUT"
(cd "$(dirname "$OUT")" && zip -qj "$(basename "$OUT")" "$ROOT/build/dex/classes6.dex")

echo "== assinatura"
apksigner sign \
  --ks "$KS" --ks-key-alias "$KS_ALIAS" \
  --ks-pass "pass:$KS_PASS" --key-pass "pass:$KS_PASS" \
  --v1-signing-enabled false --v2-signing-enabled true --v3-signing-enabled true \
  "$OUT"

apksigner verify --print-certs "$OUT" | head -3
ls -la "$OUT"

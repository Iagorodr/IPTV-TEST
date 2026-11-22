#!/usr/bin/env bash
# Roda dentro do emulador de TV: instala o APK de teste, abre o app com uma lista de demonstração e tira as capturas.
set -x
mkdir -p shots seed
python3 tools/shots/seed.py seed
(python3 -m http.server 8099 --directory testlists >/dev/null 2>&1 &)
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop app.iptvtest
for f in iptv_account iptv_user; do
  adb push seed/$f.xml /data/local/tmp/$f.xml
  adb shell "run-as app.iptvtest sh -c 'mkdir -p shared_prefs && cp /data/local/tmp/$f.xml shared_prefs/$f.xml'"
done
adb shell pm grant app.iptvtest android.permission.POST_NOTIFICATIONS 2>/dev/null
adb shell monkey -p app.iptvtest -c android.intent.category.LEANBACK_LAUNCHER 1 || adb shell monkey -p app.iptvtest 1
sleep 40
shot() { adb exec-out screencap -p > "shots/$1.png"; }
key() { adb shell input keyevent "$1"; sleep "${2:-2}"; }
# fecha o aviso de "nova versão" (o build de teste tem número baixo): Direita = "Depois", Enter
key 22 1; key 23 3
shot 01_inicio
# sobe até o menu do topo e percorre as abas (Direita + Enter)
for i in 1 2 3 4 5; do key 19 1; done
for i in 2 3 4 5 6 7; do key 22 1; key 23 6; shot "0${i}_aba"; for j in 1 2 3 4 5; do key 19 1; done; done
# ajustes (engrenagem) e busca
key 22 1; key 22 1; key 23 5; shot 08_ajustes
adb logcat -d -t 400 *:E > shots/logcat_erros.txt || true

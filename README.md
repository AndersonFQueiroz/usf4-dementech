# Dementech USF4

Rebrand pessoal do launcher **Ultra Street Fighter IV** (port ReXGlue para Android)
na identidade visual Dementech, com uma melhoria de controle: o touch virtual
some sozinho quando um controle/joystick físico é usado e volta com um toque na tela.

Este repositório **não** contém o APK base nem o motor do jogo: só o patch de
transformação, os assets de marca e o script de build. Uso pessoal — não distribuir.

## O que muda no APK base

| # | Patch | Efeito |
|---|---|---|
| 00 | `00-version` | `versionName` → `1.0` |
| 01 | `01-appname` | nome do app → **Dementech USF4** |
| 02 | `02-header` | cabeçalho "PROJETO BELMANTE" → "PROJETO DEMENTECH" |
| 03 | `03-theme` | paleta laranja → preto/prata/vermelho Dementech (+ assinatura do tema) |
| 04 | `04-channels` | remove o bloco "Siga os canais" e os avatares de redes sociais |
| 05 | `05-options-model` | remove as linhas de canais/créditos das opções |
| 06 | `06-options-text` | textos de créditos/descrições e links neutralizados |
| 07 | `07-env` | URL de ambiente/telemetria → placeholder |
| 08 | `08-settings` | URL de telemetria → placeholder |
| 09 | `09-buildconfig` | URL de telemetria → placeholder |
| 10 | `10-gamepad-touch` | touch virtual oculto ao usar controle físico; toque na tela restaura |

Assets:
- `assets/brand/launcher_logo.png` → `assets/launcher/logo.png` (emblema Dementech × SF)
- `assets/brand/ic_launcher_{mdpi..xxxhdpi}.png` → ícones do app

## Build

```sh
export JAVA_HOME=/data/data/com.termux/files/usr/lib/jvm/java-17-openjdk
BASE_APK=/sdcard/Download/USF4-0.3.0.apk tools/build.sh
```

Saída em `dist/dementech-usf4.apk`, assinado com `~/keys/carbon.jks`
(mesma chave dos outros apps Dementech → `adb install -r` preserva os dados).

Requisitos: `apktool` 2.7+ com `--use-aapt2`, Android build-tools (aapt2,
apksigner), `patch`, Java 17. O `zipalign` não é usado (binário ausente no
ambiente de build; a assinatura v2/v3 dispensa o alinhamento para instalar).

## Instalação

```sh
adb install -r dist/dementech-usf4.apk
```

O app copia os dados do jogo para o armazenamento interno dele na primeira
execução (`Importar` → pasta com `Default.xex`). **Não desinstale** o app depois
disso: o Android apaga essa cópia no uninstall.

## Feature do controle

`GameActivity` ganhou:

- `touchHiddenByGamepad` — estado do touch virtual;
- `isGamepadDevice(InputDevice)` — verdadeiro para controle físico (não virtual,
  fontes `SOURCE_GAMEPAD`/`SOURCE_JOYSTICK`);
- em `dispatchKeyEvent`/`onGenericMotionEvent`: controle físico → esconde o touch;
- em `dispatchTouchEvent`: `ACTION_DOWN` na tela → mostra o touch de novo.

Os overrides chamam `super` (`org.libsdl.app.SDLActivity`), então o input do jogo
não é afetado.

## Estrutura

```
assets/brand/    logos e ícones da marca
patches/         diffs unificados contra a árvore decodificada do APK base
tools/build.sh   decode → patch → build → sign
doc/             notas do projeto
```

## Licença

Scripts e assets de marca deste repositório: MIT (`LICENSE`).
O APK base e o motor do jogo pertencem a seus respectivos donos; os dados do
jogo são © Capcom/Electronic Arts. Este repositório não os redistribui.

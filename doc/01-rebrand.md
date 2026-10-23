# Rebrand — notas técnicas

## Base

APK base: ReXGlue port de **Ultra Street Fighter IV** (Xbox 360), build `0.3.0`,
pacote `io.github.belmantegu.usfivrecomp`, 46 MB. O pacote **não** pode ser
renomeado: `libusfiv.so` exporta símbolos JNI `Java_io_github_belmantegu_*`.

## Pipeline

```
APK base ──apktool d──> árvore decodificada ──patch -p1 (patches/)──>
assets de marca ──apktool b --use-aapt2──> APK ──apksigner v2/v3──> dist/
```

Os patches são diffs unificados com prefixo `a/`/`b/` relativos à raiz da árvore
decodificada, aplicados em ordem alfabética.

## Detalhes que quebraram antes

- **apktool 2.7.0** exige `--use-aapt2` e o `aapt2` do build-tools no `PATH`.
- **`zipalign`** não executa neste ambiente (binário x86_64 em proot); assinar com
  v2/v3 instala normalmente sem alinhar.
- **smali**: `const/16` aceita apenas `-32768..32767`. Cores como `0xFFC8102E`
  exigem `const`. O apktool insere linha em branco entre instruções — os patches
  são gerados do próprio decode, então já vêm nesse formato.

## Paleta (UsfivTheme)

| Papel | Cor |
|---|---|
| accent | `0xFFFF3B4E` |
| accent dim | `0xFFC8102E` (via `-0x37efd2`) |
| accent claro | `0xFFFF8A94` |
| texto | `0xFFE9EDF4` |
| secundário | `0xFF9BA6B8` |
| highlight | `0xFFF2F5FA` |
| glow | `0x73FF3B4E` |

## Proveniência

O APK base é trabalho de terceiros, obtido publicamente; o repositório do autor
original não está mais disponível. Este projeto é **uso pessoal** (não
distribuição): aplica a marca Dementech e remove canais/telemetria do autor.
O motor (`libusfiv.so`, runtime ReXGlue, GPU Xenos) não é modificado.

## Estado validado

- Build + instalação por cima da versão anterior (mesma chave): OK.
- Launcher: "PROJETO DEMENTECH", "Versão 1.0", JOGAR vermelho, emblema SF — OK
  (screenshot).
- Feature do controle: compila e roda; teste com controle físico pendente.

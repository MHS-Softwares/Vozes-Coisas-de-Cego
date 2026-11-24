# Vozes Coisas de Cego

Motor de **texto para fala** para Android com vozes neurais, **100% offline** para falar, que roda direto no celular
e funciona como a voz do sistema (TalkBack e outros leitores de tela). Feito para usuários cegos
e com baixa visão, e pensado para responder rápido ao leitor de telas.

O APK traz três vozes clonadas no Gradium Studio, falando português do Brasil:
**Meriellen** (dona do projeto Coisas de Cego), **Michel** e **Daiane**. O motor também fala inglês, espanhol,
francês e alemão com a voz escolhida. A voz se troca na tela do app e **a troca vale na hora**, sem
reiniciar o leitor de telas.

> **Uso não comercial.** O modelo de voz (Phonon 40M, da Gradium) é CC BY-NC 4.0.
> Veja [THIRD_PARTY.md](THIRD_PARTY.md).

Este projeto é um irmão do Michel Henrique TTS: mesmo motor, com a escolha entre as três vozes.
Os dois apps podem ficar instalados juntos (pacotes diferentes).

## Baixar

Na página de **Releases**: o APK (`Vozes-Coisas-de-Cego-1.1.apk`, cerca de 103 MB, só arm64)
e o **manual** em texto (`Manual das Vozes Coisas de Cego.txt`).

**Atualizações pelo próprio app (a partir da 1.1).** Na tela do app, em **Atualizações**, o botão
**Verificar atualizações** procura a versão mais nova nesta página de Releases. Se houver, o app mostra o
que mudou, baixa o APK (continuando de onde parou se a conexão cair), confere que o arquivo é mesmo do app
(mesmo pacote, versão mais nova e o mesmo certificado) e abre a instalação do Android, onde você confirma.
Na primeira vez, o Android pede para permitir que o app instale outros apps. Uma vez por dia o app também
procura sozinho e avisa por notificação (dá para desligar). **Só essa verificação usa a internet**: a voz
continua falando 100% offline. Quem está na 1.0 precisa instalar a 1.1 uma vez à mão, porque a 1.0 não tem
atualização.

Para ativar: Ajustes do Android, Texto para fala, Mecanismo preferido, **Vozes Coisas de Cego**.

## O que ele faz

- **Três vozes** (Meriellen, Michel e Daiane), com troca **em tempo real**: o app lê a voz escolhida a cada frase
  e o motor troca de voz em cerca de 1 ms, sem recarregar o modelo.
- **Velocidade, tom e volume** ajustáveis, junto com os do Android e do TalkBack (dá para
  ignorar os do Android, se você preferir).
- **Cache de frases**, separado por voz: guarda o áudio de cada frase já falada (memória e disco) e pré-gera,
  com o celular parado, cerca de 200 frases comuns de leitor de telas. Frases repetidas tocam na hora. Um botão prepara todas de uma vez.
- **Emojis lidos pelo nome em português** (4.328 emojis, do CLDR), com repetidos como "nome, 3 vezes".
- **Correção de pronúncia**: regras do app (por exemplo, "imagens" que o motor falava "imaguens"),
  letras soltas pelo nome e um **dicionário de pronúncias** seu, numa tela própria
  (adicionar, editar e remover palavras).
- **Corta o silêncio do começo** de cada fala, para responder mais rápido.
- **Modo de fala** (início rápido, automático ou sem engasgos) e número de núcleos do processador.
- Tela de ajustes feita para leitor de tela, com **"Última fala"**: mostra a voz usada, o que o Android
  pediu, o tempo até o primeiro som e quantas frases vieram do cache.

## Como funciona

Um `TextToSpeechService` do Android chama o motor nativo do Phonon (`libptts_ffi.so`) por JNA.
O áudio passa por um processador próprio (velocidade e tom sem robotizar, por sobreposição de trechos
parecidos; volume com limitador) e é entregue ao sistema em 24 kHz. Código principal em
`app/src/main/java/com/mhs/vozescoisasdecego/`.

A voz de cada pedido é lida das preferências no começo da fala. Se for diferente da que está no
motor, `PhononEngine` chama `ptts_set_voice` na própria thread do motor (única que o toca), e o cache
usado é o daquela voz (`Vozes.kt`).

## Como compilar

O repositório tem só o **código**. O modelo, as bibliotecas nativas e as vozes **não** estão no Git
(veja `.gitignore` e [THIRD_PARTY.md](THIRD_PARTY.md)); você precisa colocá-los antes de compilar:

| Arquivo | Onde colocar |
|---|---|
| `config.json`, `tokenizer.json` e `model.q8.gguf` do Phonon 40M multilingual | `app/src/main/assets/model/` |
| As vozes exportadas do Gradium Studio (`.safetensors`): `Meriellen.safetensors`, `Michel.safetensors`, `Daiane.safetensors` (e uma cópia `default.safetensors`) | `app/src/main/assets/model/voices/` |
| `libptts_ffi.so` e `libptts_ffi_compat.so` (arm64) | `app/src/main/jniLibs/arm64-v8a/` |

Para acrescentar outra voz: coloque o arquivo em `voices/`, inclua o nome em `Vozes.LISTA`
(e, se quiser, na frase de apresentação) e troque `ModelStore.PACOTE`.

Depois, com JDK 17 e o SDK do Android 34 (crie `local.properties` com `sdk.dir=...`):

```
gradlew.bat assembleRelease
```

O APK sai em `app/build/outputs/apk/release/`. Os testes de JVM (áudio, cache, dicionário, emojis,
texto, vozes) rodam com `gradlew.bat testDebugUnitTest`.

**Atualização:** o app procura a versão nova em `Atualizacao.REPOSITORIO` (`MHS-Softwares/Vozes-Coisas-de-Cego`).
Para publicar uma versão, suba `versionCode` e `versionName` no `app/build.gradle.kts`, compile, e crie uma
Release no GitHub com a etiqueta `v` + o número (por exemplo `v1.2`) e o APK como arquivo. O app só oferece
um APK com o **mesmo certificado** do que está instalado: a chave de assinatura não pode mudar entre versões.

O `build.gradle.kts` assina o APK com a chave de depuração do Android (`~/.android/debug.keystore`).
Para distribuir como seu, troque por a sua chave.

## Créditos

Gradium (Phonon), Kyutai (Pocket TTS e Mimi), Unicode (CLDR), autores do JNA e do AndroidX.
Detalhes e licenças em [THIRD_PARTY.md](THIRD_PARTY.md).

Código por Michel Henrique da Silva, feito com a ajuda do Claude (Anthropic). Licença MIT para o código.

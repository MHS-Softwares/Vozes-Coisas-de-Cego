# Componentes de terceiros e licenças

O **código** deste repositório é MIT (arquivo `LICENSE`). O APK da Release leva
dentro outras coisas, que **não** são do autor do código e têm licença própria.

## Modelo de voz: uso não comercial

- **Phonon 40M multilingual**, da Gradium (`gradium/phonon-40m-multilingual`),
  licença **CC BY-NC 4.0** (Creative Commons, atribuição, **uso não comercial**).
  Por isso o APK também é só para uso não comercial.
- O APK leva a versão quantizada em 8 bits (`model.q8.gguf`), junto com
  `config.json` e `tokenizer.json`, que vieram do pacote de modelo do app
  "Vozes Neurais TTS" v0.13 (veja abaixo). O modelo não foi treinado de novo.
- O Phonon da Gradium se apoia no Pocket TTS e no Mimi, da Kyutai.

## Motor nativo

- `libptts_ffi.so` e `libptts_ffi_compat.so` (arm64): o motor do Phonon,
  que a Gradium indica como **MIT ou Apache-2.0**. As cópias do APK vieram,
  sem alteração, do app "Vozes Neurais TTS" v0.13 (autor: laionil), que
  distribui builds do motor com algumas funções a mais. O autor deste
  repositório não compilou essas bibliotecas e não conhece uma licença
  própria publicada nesse app. Quem quiser evitar a dúvida pode usar as
  bibliotecas do app oficial da Gradium (ver README, "Como compilar").

## Vozes

Todas são **vozes clonadas** criadas no Gradium Studio a partir de gravações das
próprias pessoas. Usar uma dessas vozes para se passar por quem ela é, ou para
enganar alguém, não é permitido.

- `voices/Michel.safetensors` (e a cópia `voices/default.safetensors`): voz clonada
  de Michel Henrique da Silva, autor do código.
- `voices/Meriellen.safetensors`: voz clonada de **Meriellen**, dona do projeto
  Coisas de Cego. A distribuição desta voz depende da autorização dela.
- `voices/Daiane.safetensors`: voz clonada de **Daiane**. A distribuição desta voz depende da
  autorização dela.

## Nomes dos emojis

- `app/src/main/assets/emojis_pt.tsv`: gerado a partir das anotações em
  português do **CLDR** (Unicode Common Locale Data Repository, versão 49.0.0),
  licença **Unicode License v3**. O texto da licença está em
  `app/src/main/assets/emojis_NOTICE.txt`. O gerador é
  `ferramentas/gerar_emojis.py`.

## Bibliotecas de código

- **JNA** (`net.java.dev.jna:jna`), Apache-2.0 ou LGPL-2.1: chama o motor nativo.
- **AndroidX Core KTX**, Apache-2.0.

# Favilla APK

Favilla è una chat che gira **dentro il browser del telefono**: nessuna chiave API, nessun
account, il modello (SmolLM2 / Qwen2.5 quantizzati) viene scaricato una volta e poi eseguito
in WebAssembly sulla CPU del dispositivo. Questo repository costruisce l'APK Android che la
apre a tutto schermo.

- App web: <https://site-3c4b410fa82a4937a3a270ac92de0e41.freebuff.page>
- APK: **Actions → APK → ultima run → Artifacts → `Favilla-apk`**

## Installare l'APK

1. Apri la scheda **Actions** e scegli l'ultima esecuzione verde di «APK».
2. Scarica l'artefatto `Favilla-apk` (contiene `Favilla.apk`).
3. Sul telefono: Impostazioni → Sicurezza → «Installa app sconosciute» per il browser o il
   file manager che usi, poi apri l'APK.

L'APK è firmato con la chiave di debug generata da GitHub Actions: si installa e funziona,
ma non è pubblicabile sul Play Store (servirebbe una chiave di release vera).

## Come è fatto

```
android/                        progetto Gradle (AGP 8.5, compileSdk 34, minSdk 24)
  app/src/main/java/app/favilla/MainActivity.java
  app/src/main/res/…            icona e nome
.github/workflows/apk.yml       build su ogni push in main + artifact
docs/BENCHMARK.md              misure di velocità dei modelli
```

`MainActivity` è un `WebView` a schermo intero sull'app pubblicata:

- JavaScript, DOM storage e database abilitati (la chat usa `sessionStorage`).
- Il navigator resta dentro l'app per `freebuff.page`, `huggingface.co`, `hf.co` e
  `jsdelivr.net` (pagina, pesi del modello, runtime di transformers.js); ogni altro link
  esce nel browser di sistema.
- Tasto indietro = cronologia della pagina.
- In background la pagina viene messa in pausa (`pauseTimers`): niente CPU spesa mentre il
  telefono è in tasca.
- Se manca la rete al primo avvio compare una schermata «Riprova» invece di una pagina
  bianca.

## Compilare in locale

Serve JDK 17 e Android SDK 34.

```bash
cd android
gradle assembleRelease
# android/app/build/outputs/apk/release/app-release.apk
```

## Perché è un guscio e non un pacchetto di file locali

Il modello gira in un `Worker` con `SharedArrayBuffer`, che esiste solo in una pagina
*cross-origin isolated*: servono gli header `Cross-Origin-Opener-Policy` e
`Cross-Origin-Embedder-Policy`, e li manda il Worker del sito. Con gli asset dentro l'APK
(`file://`) quei due header non ci sono e la chat resterebbe a un solo thread. Il guscio
tiene quindi la pagina sul suo origin, con l'isolamento e la cache HTTP che le servono.

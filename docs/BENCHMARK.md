# Misure di velocità

Stesso prompt, 16–24 token nuovi, `do_sample: false`, token contati dal worker, quindi
`tok/s = token / secondi di sola generazione` (caricamento escluso). Macchina delle prove:
VM a 2 vCPU, cross-origin isolated, WebGPU inutilizzabile, WebNN assente.

| Modello | Thread | Token | Tempo | Tok/s |
|---|---|---|---|---|
| Lampo (SmolLM2 135M, q4f16) | 1 | 13 | 6,97 s | 1,9 |
| Lampo | 2 | 13 | 4,25 s | 3,1 |
| Lampo | 4 | 13 | 13,36 s | 1,0 |
| Lampo | 8 | 13 | 17,64 s | 0,7 |
| Brezza (SmolLM2 360M, q4f16) | 1 | 22 | 17,73 s | 1,2 |
| Brezza | 2 | 15 | 6,95 s | 2,2 |
| Brezza | 4 | — | — | bloccato |
| Vela (Qwen2.5 0.5B, q4) | 2 | — | — | bloccato (>115 s) |
| Fucina (Coder 0.5B) | 2 | — | — | non tentato |
| Quercia (1.7B) / Faro (1.5B) | — | — | — | non tentati (1,1–1,2 GB) |
| Lampo su GPU (WebGPU) | 2 | — | — | bloccato al caricamento (3 volte) |
| qualsiasi NPU | — | — | — | `navigator.ml` assente, nessun backend WebNN |

2 thread sono il massimo per i modelli piccoli su questa VM; 4 e 8 peggiorano perché i
thread wasm superano i core reali. Sul telefono il numero giusto va misurato: Impostazioni →
«Misura tutti i modelli» salva il risultato con motore e thread usati.

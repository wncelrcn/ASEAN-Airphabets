# Tech Stack

## SDKs

- Min SDK: 24 (app), 30 (wear)
- Compile/Target SDK: 36
- JVM Target: Java 11
- Gradle: 8.13.2, Kotlin: 2.0.21

## Dependencies

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Kotlin | 2.0.21 |
| UI | Jetpack Compose + Material 3 | — |
| Architecture | MVVM + Repository | — |
| Database | Room | 2.6.1 |
| Navigation | Navigation Compose | — |
| Networking | Retrofit + OkHttp + Gson | 2.9.0 / 4.12.0 |
| AI | Google Generative AI SDK | 0.9.0 |
| TTS | Deepgram API (primary), Android TTS (fallback) | — |
| ML | TensorFlow Lite (wear module) | 2.17.0 |
| Watch Comms | Play Services Wearable | 19.0.0 |
| Images | Coil Compose | 2.5.0 |

## API Keys

Stored in `local.properties` (never committed), injected via `BuildConfig`:

```
DEEPGRAM_API_KEY=...   # Text-to-speech via Deepgram
GEMINI_API_KEY=...     # AI activity generation via OpenRouter
```

Copy `local.properties.example` if it exists, or ask a teammate for the keys.

## Permissions

`AndroidManifest.xml` declares: Bluetooth, Internet, Notifications (Android 13+), Vibrate.

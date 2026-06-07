# Feature Map

High-level map of what exists and where to find it.

## Auth
Login and signup with password hashing + salt.
- `ui/feature/auth/` — screens
- `data/repository/UserRepository.kt` — logic

## Dashboard
Activity progress cards, student summaries, Kuu AI recommendations, watch connection status.
- `ui/feature/dashboard/`

## Classroom Management
Create, edit, and archive classes; manage student enrollment.
- `ui/feature/classroom/`

## Learn Mode
Two modes: **Tutorial** (guided, step-by-step) and **Learn** (independent).
Question types: fill-in-blank, write-word, name-picture.
- `ui/feature/learn/`

## Word Bank
Per-teacher word list with optional images. Supports batch delete.
- `ui/feature/learn/` (word bank screens embedded in learn feature)

## Activities & Sets
Teachers create Activities, build word Sets, and link them together.
- `ui/feature/learn/set/`

## AI Activity Generation
Natural-language prompts generate ready-to-use activities. See [ai-pipeline.md](ai-pipeline.md) for full detail.

## Watch Integration
Air-writing gesture recognition via TFLite on the Wear OS module. Tutorial and learn modes mirror phone experience. Phone-watch sync via Wearable Data Layer.
- `wear/` module
- `ui/feature/watch/` (pairing screen on phone)

## TTS (Text-to-Speech)
Deepgram voices with audio caching and playback speed control. Android TTS as fallback. Encouraging phrases injected at key moments.
- `speech/` package

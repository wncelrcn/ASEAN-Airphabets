# Airphabets — Agent & Contributor Guide

Airphabets is an educational Android app for letter and word learning, with Wear OS smartwatch integration, AI-powered activity generation, and air-writing gesture recognition.

## First-Run Commands

```bash
./gradlew assembleDebug          # Build debug APK
./gradlew test                   # Run unit tests
./gradlew installDebug           # Build and install on connected device
./gradlew connectedAndroidTest   # Run instrumentation tests
```

API keys go in `local.properties` (never committed). Ask a teammate or see [docs/tech-stack.md](docs/tech-stack.md).

---

## Hard Constraints

These are non-negotiable. Do not work around them.

1. **Kotlin only** — no Java, anywhere.
2. **Compose only** — no XML layouts, no View system.
3. **No Hilt/Dagger** — use manual singletons with double-check locking.
4. **Room migrations** — the project uses `fallbackToDestructiveMigration()`. Write no manual migration files.
5. **One public class per file.**
6. **Screens** must end with the `Screen` suffix; ViewModels with `ViewModel`.
7. **Never expose `MutableStateFlow`** from a ViewModel — expose `StateFlow` only.
8. **Repositories own all data access** — screens and ViewModels never touch DAOs directly.
9. **`local.properties` must not be committed** — it contains secrets.
10. **Don't touch unrelated code.** If you notice dead code nearby, mention it; don't delete it.

---

## Behavioral Guidelines

### Think before coding
- State your assumptions explicitly before implementing. If something is ambiguous, ask — don't guess silently.
- If multiple interpretations exist, name them. If a simpler approach exists, say so.

### Simplicity first
- Minimum code that solves the problem. Nothing speculative.
- No abstractions for single-use code. No "flexibility" that wasn't requested.
- If you write 200 lines and it could be 50, rewrite it.

### Surgical changes
- Touch only what the task requires. Don't "improve" adjacent formatting or structure.
- Match existing style, even if you'd do it differently.
- Remove imports/variables made unused by *your* changes. Leave pre-existing dead code alone.

### Verify before claiming done
- Run `./gradlew test` before marking anything complete.
- For multi-step tasks, write a brief plan with a verification step per stage before starting.

---

## Topic Docs

Read these when working in the relevant area:

| Doc | When to read |
|-----|-------------|
| [docs/architecture.md](docs/architecture.md) | Module layout, package structure, navigation, DI pattern |
| [docs/conventions.md](docs/conventions.md) | Naming, file structure, ViewModel/Repository/coroutine patterns |
| [docs/tech-stack.md](docs/tech-stack.md) | SDK versions, dependencies, API keys, permissions |
| [docs/features.md](docs/features.md) | What exists and where to find it |
| [docs/ai-pipeline.md](docs/ai-pipeline.md) | AI activity generation, PatternRouter, word suggestion flow |

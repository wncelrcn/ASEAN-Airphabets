# Architecture

Airphabets is a single-activity Android app using Jetpack Compose for all UI. Navigation is handled by Navigation Compose with a sealed class route system.

## Modules

| Module | Purpose |
|--------|---------|
| `:app` | Main phone application |
| `:wear` | Wear OS smartwatch app (TFLite gesture recognition) |
| `:common` | Shared utilities for phone-watch communication |

## Package Structure

```
app/src/main/java/com/example/app/
├── data/
│   ├── AppDatabase.kt          # Room database (16 entities, 14 DAOs)
│   ├── SessionManager.kt       # User session singleton
│   ├── dao/                    # Room DAOs
│   ├── entity/                 # Room entities
│   ├── model/                  # API response models
│   └── repository/             # Business logic (9 repositories)
├── ui/
│   ├── feature/                # Screen-level composables + ViewModels
│   │   ├── auth/               # Login, SignUp
│   │   ├── classroom/          # Class management
│   │   ├── dashboard/          # Main dashboard
│   │   ├── home/               # MainNavigationContainer
│   │   ├── learn/              # Learning flows, AI generation, sets
│   │   ├── onboarding/         # Onboarding screens
│   │   └── watch/              # Watch pairing
│   ├── components/             # Reusable composable components
│   ├── theme/                  # Color, Typography, Theme
│   └── navigation/             # AppNavigation, Screen sealed class
├── service/                    # Background services
├── speech/                     # TTS integration
└── util/                       # Utilities
```

## Entry Points

- `MainActivity.kt` — Single activity, bootstraps Compose
- `AppNavigation.kt` — Top-level navigation graph
- `MainNavigationContainer.kt` — Feature routing (50+ screen indices)

## Navigation Routes

Defined in the `Screen` sealed class. Key routes:

- `Login`, `SignUp`, `PostSignUpOnboarding`
- `Home` (gateway to all features)
- `ClassDetails/{classId}`
- `StudentDetails/{studentId}/{studentName}/{className}`

## State Management Pattern

ViewModels expose `StateFlow<UiState>` and mutate via `_uiState.update { }`. Screens collect state with `collectAsState()`. Side effects run inside `LaunchedEffect`.

## Dependency Injection

Manual singletons using `@Volatile` + `synchronized` double-check locking with a `getInstance()` companion object. No Hilt or Dagger.

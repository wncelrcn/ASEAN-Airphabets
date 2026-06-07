# Code Conventions

## Language & UI

- **Kotlin only** — no Java anywhere
- **Compose only** — no XML layouts, no View system
- All dependencies managed via version catalog at `gradle/libs.versions.toml`

## Naming

| Thing | Convention | Example |
|-------|-----------|---------|
| Classes | PascalCase | `LearnModeScreen` |
| Functions | camelCase | `loadWordBank()` |
| Constants | UPPER_SNAKE_CASE | `MAX_WORD_COUNT` |
| Screens | `*Screen` suffix | `DashboardScreen` |
| ViewModels | `*ViewModel` suffix | `LessonViewModel` |

## File Structure

- One public class per file
- ViewModels live alongside their feature screen in the same package
- Reusable composables go in `ui/components/`

## ViewModels

- Extend `AndroidViewModel`
- Expose `StateFlow<UiState>` (never expose `MutableStateFlow` directly)
- Mutate state via `_uiState.update { it.copy(...) }`
- Launch coroutines with `viewModelScope`

## Repositories

- Abstract all data access — screens never touch DAOs directly
- Return sealed class results for operations (e.g. `Result.Success`, `Result.Error`)
- Reside in `data/repository/`

## Database

- Room, version 18, database name `airphabets_database`
- Uses `fallbackToDestructiveMigration()` — write no manual migrations
- 16 entities, CASCADE DELETE on user deletion
- When bumping schema: increment `AppDatabase` version number

## Coroutines

- `viewModelScope` in ViewModels
- `LaunchedEffect` for side effects in Compose
- No `GlobalScope`

## Singletons

Use double-check locking pattern:

```kotlin
@Volatile private var INSTANCE: MyClass? = null

fun getInstance(): MyClass {
    return INSTANCE ?: synchronized(this) {
        INSTANCE ?: MyClass().also { INSTANCE = it }
    }
}
```

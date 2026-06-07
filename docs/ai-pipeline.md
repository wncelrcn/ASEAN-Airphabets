# AI Activity Generation Pipeline

## Overview

Teachers type a natural-language prompt (e.g. "short a words", "animal words"). The system selects matching words from the teacher's Word Bank and builds an activity.

Entry point: `GeminiRepository.kt` → `generateActivity()`

## 3-Step Gemini Chain

| Step | What it does |
|------|-------------|
| 1 — Filter Words | Selects words from the Word Bank that match the prompt |
| 2 — Select Subset | Picks a coherent group of 3–10 words |
| 3 — Configure | Assigns question types: "write the word", "fill in the blanks", "name the picture" |

Steps 2 and 3 always use Gemini. Step 1 is bypassed for structural CVC patterns (see PatternRouter below).

## PatternRouter — Deterministic Filtering

`classifyPrompt()` in `GeminiRepository.kt` detects structural CVC patterns and skips AI for Step 1:

| Pattern | Example Prompt | Enum |
|---------|---------------|------|
| Rime | "-un words", "ending in -at" | `DetectedPattern.Rime` |
| Onset | "starting with b", "letters with g" | `DetectedPattern.Onset` |
| Vowel | "short a words", "vowel 'e'" | `DetectedPattern.Vowel` |
| Coda | "ending in t" | `DetectedPattern.Coda` |

**Regex order matters:** vowel patterns are checked before onset patterns. This prevents "words with the short 'a'" from matching the 's' in "short" as an onset. The `(?:the\s+)?(?:letter\s+)?` optional groups handle phrasings like "start with the letter g".

Thematic/semantic prompts ("animal words", "food") fall through to AI Step 1.

## Insufficient Words Flow

When fewer than 3 words in the Word Bank match a structural pattern:

1. `generateActivity()` returns `AiGenerationResult.InsufficientWords` (carries the detected pattern + original prompt)
2. `LessonViewModel.handleInsufficientWords()` calls `generateCVCWords()` to suggest new words
3. `WordSuggestionDialog` shows selectable chips — teacher picks which to add
4. After adding, generation auto-resumes

## Word Generation Filter Chain

`generateCVCWords()` post-filters AI output before presenting suggestions:

1. `isValidCVC()` — validates consonant-vowel-consonant structure
2. `BLOCKED_WORDS` — removes inappropriate content
3. Pattern adherence — strips words that don't match the detected pattern
4. Child-friendly enforcement via prompt (kindergarten/grade-1 level)

## Key Files

| File | Role |
|------|------|
| `data/repository/GeminiRepository.kt` | Pipeline, PatternRouter, CVC analysis |
| `data/model/AiGeneratedData.kt` | Result sealed class including `InsufficientWords` |
| `ui/feature/learn/LessonViewModel.kt` | Suggestion flow orchestration |
| `ui/components/wordbank/WordSuggestionDialog.kt` | Word suggestion UI |
| `ui/feature/learn/set/YourSetsScreen.kt` | Activity creation screen |
| `util/WordValidator.kt` | CVC pattern validation |
| `util/DictionaryValidator.kt` | Android spell checker integration |

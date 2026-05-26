# 🚀 Gemini CLI Guidelines — VCode IDE

This project is **VCode**, a mobile IDE for web development (HTML, CSS, JS, JSON) built for Android.

## 🏗️ Core Architecture & Tech Stack

- **Platform:** Android (Java Only).
- - **Architecture:** MVVM (ViewModel + LiveData + Repository).
- - **Min SDK:** 23 (Android 6.0).
- - **Build System:** Gradle (Groovy DSL) with Version Catalogs (`libs.versions.toml`).
- - **Core Library:** JGit for all Git operations.
- - **Key Features:** Custom code editor, syntax highlighting, autocomplete, git/github integration, in-app web preview.

## 📏 Mandatory Conventions

### 1. Resource Naming (CRITICAL)
Every resource (color, string, dimen, attr, style, drawable) **MUST** use the `vcode_` prefix.
- ✅ `@color/vcode_accent_primary`
- ✅ `@string/vcode_app_name`
- ❌ `@color/accent_primary`

### 2. Language & Style
- **Java Only:** No Kotlin.
- **No forbidden libraries:** Avoid Retrofit, OkHttp, Room, Dagger/Hilt, RxJava unless explicitly requested. Use `HttpURLConnection` for APIs and `org.json` for parsing.
- **Typography:** JetBrains Mono for code, Sora for UI (loaded via `FontManager`).
- **Icons:** Use SVG Vector Drawables (`res/drawable/ic_*.xml`). No icon fonts.

### 3. Editor Logic
- Syntax highlighting and autocomplete must run off the main thread (use `ExecutorProvider`).
- Highlights are applied as spans to `Editable`, not by re-setting text.
- Debounce: 350ms for syntax, 200ms for autocomplete.

## 🛠️ Development Workflow

### 1. Research & Strategy
- Reproduce bugs before fixing.
- Map dependencies before refactoring.
- Check `VCode_APP_FLOW.md` for UX expectations.
- Check `VCode_IMPLEMENTATION_PLAN.md` for architectural blueprints.

### 2. Implementation Rules
- **Surgical Edits:** Use `replace` for precise changes.
- **Complete Implementation:** Never use `// TODO` or `// ...`.
- **Validation:** Always verify with builds or tests.

### 3. Testing
- Add unit tests for core logic (parsers, formatters, syntax engines).
- UI tests for critical flows (file creation, tab switching).

## 📂 Key File Locations
- **Activities:** `ui/`
- **ViewModels:** `ui/` (alongside activities/fragments)
- **Custom Views:** `views/`
- **Git Logic:** `git/`
- **Core Engines:** `core/` (syntax, autocomplete, parser)
- **Data/Repos:** `data/`

## 🤖 Agent Instructions
- **Strict Adherence:** Follow the `vcode_` prefix and Java-only rules without exception.
- **Verification:** Run `./gradlew assembleDebug` (or equivalent) after major changes to ensure compilation.
- **Documentation:** Keep `VCode_APP_FLOW.md` and `VCode_IMPLEMENTATION_PLAN.md` updated if architectural changes occur.

## 📦 Recent Changes

- Added unsaved changes detection before navigating away from the editor.
- Replaced deprecated `onBackPressed()` with AndroidX `OnBackPressedDispatcher` callback to ensure consistent back navigation handling.
- Integrated `navigateWithUnsavedCheck` utility in overflow menu actions (Git, Settings) and back navigation.

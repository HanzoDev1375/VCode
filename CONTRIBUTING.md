# Contributing to VCode

Thank you for your interest in contributing to VCode! We welcome bug reports, feature requests, documentation improvements, and code contributions. Please take a moment to read these guidelines before getting started.

---

## 📋 Table of Contents

- [Code of Conduct](#code-of-conduct)
- [How to Contribute](#how-to-contribute)
- [Development Setup](#development-setup)
- [Coding Standards](#coding-standards)
- [Pull Request Process](#pull-request-process)
- [Reporting Bugs](#reporting-bugs)
- [Requesting Features](#requesting-features)

---

## 📜 Code of Conduct

By participating in this project, you agree to maintain a respectful and collaborative environment. Be kind, constructive, and considerate in all interactions.

---

## 🤝 How to Contribute

1. **Fork** the repository on GitHub.
2. **Clone** your fork locally:
   ```bash
   git clone https://github.com/YOUR_USERNAME/VCode.git
   cd VCode
   ```
3. **Create a branch** for your work:
   ```bash
   git checkout -b feature/your-feature-name
   # or
   git checkout -b fix/your-bug-fix
   ```
4. **Implement** your changes following the [Coding Standards](#coding-standards) below.
5. **Verify** the build compiles cleanly:
   ```bash
   ./gradlew compileDebugJavaWithJavac
   ```
6. **Commit** your changes with a clear, descriptive message:
   ```bash
   git commit -m "feat: add file search in file tree"
   ```
7. **Push** your branch and open a Pull Request.

---

## 🛠️ Development Setup

### Prerequisites
- Android Studio Hedgehog or later
- JDK 11 or higher
- Android SDK with API level 36

### Build

```bash
./gradlew assembleDebug
```

### Install directly to a connected device

```bash
./gradlew installDebug
```

---

## 📐 Coding Standards

VCode has **strict technical constraints** that must be followed to maintain consistency and performance. Please read these carefully before writing any code.

### 1. Java Only — No Kotlin
All source files must be written in **pure Java**. Kotlin is not used anywhere in this project and must not be introduced.

### 2. No Heavy Third-Party Libraries
We intentionally keep the dependency footprint minimal:

| ❌ Do NOT use | ✅ Use instead |
|---|---|
| Retrofit | `HttpURLConnection` |
| Gson / Moshi | `org.json` |
| Room | Direct SQLite or file-based I/O |
| RxJava / Coroutines | `ExecutorProvider` + `Handler` / LiveData |
| Hilt / Dagger | Manual dependency injection |

### 3. Resource Naming Convention
All Android resources (colors, strings, drawables) **must** use the `vcode_` prefix:
```xml
<!-- ✅ Correct -->
<color name="vcode_accent_primary">#FF6B6B</color>
<string name="vcode_commit_btn">Commit</string>

<!-- ❌ Wrong -->
<color name="accent_primary">#FF6B6B</color>
```
> **Note:** Layout file names are excluded from this rule.

### 4. Threading Rules
VCode is an IDE that processes large files. **Never** perform I/O, parsing, or Git operations on the main thread:

```java
// ✅ Correct — run I/O on background thread
ExecutorProvider.getInstance().runOnIo(() -> {
    String content = FileUtils.readFile(file);
    ExecutorProvider.getInstance().runOnMain(() -> {
        // Update UI here
    });
});

// ❌ Wrong — blocks the main thread
String content = FileUtils.readFile(file); // on main thread
```

Always capture a `String` snapshot of editor text before dispatching to a background thread to prevent `IndexOutOfBoundsException` from concurrent UI modifications.

### 5. TextWatcher / Editor Lifecycle
When modifying `CodeEditText` or related viewers, guard against recursive `TextWatcher` loops using state flags like `isApplyingHighlight`, `isAutoClosing`, or `isSettingText`.

### 6. Commit Message Format
Follow [Conventional Commits](https://www.conventionalcommits.org/):
```
feat: add branch rename functionality
fix: cursor not visible after tap during blink off-phase
style: remove gear button from file tree header
refactor: extract file read logic into FileUtils
docs: update README with screenshots
```

---

## 🔄 Pull Request Process

1. Ensure your branch is **up to date** with `master` before opening a PR.
2. **Verify the build** compiles without errors:
   ```bash
   ./gradlew compileDebugJavaWithJavac
   ```
3. Make sure your PR description clearly explains:
   - **What** the change does
   - **Why** it is needed
   - **How** you tested it
4. Keep PRs **focused** — one feature or fix per PR.
5. A maintainer will review your PR and may request changes. Please be responsive to feedback.

---

## 🐛 Reporting Bugs

Before opening a bug report, please search [existing issues](https://github.com/cocodestudio/VCode/issues) to avoid duplicates.

When reporting a bug, please include:
- **Device** model and Android version
- **Steps to reproduce** the issue
- **Expected behavior** vs **actual behavior**
- **Logcat output** if available (filter by `VCode`)
- A **screenshot or screen recording** if helpful

[**→ Open a Bug Report**](https://github.com/cocodestudio/VCode/issues/new?labels=bug)

---

## 💡 Requesting Features

Feature requests are welcome! Please describe:
- The **use case** — what problem does this solve?
- Your **proposed solution** (optional)
- Any **alternatives** you have considered

[**→ Open a Feature Request**](https://github.com/cocodestudio/VCode/issues/new?labels=enhancement)

---

## 📬 Questions?

If you have questions about contributing, feel free to open a [GitHub Discussion](https://github.com/cocodestudio/VCode/discussions) or file an issue with the `question` label.

---

Thank you for helping make VCode better! 🚀

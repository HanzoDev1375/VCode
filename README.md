# VCode — The IDE That Fits in Your Pocket

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE) [![Platform](https://img.shields.io/badge/Platform-Android-brightgreen.svg)](https://android.com) [![Min SDK](https://img.shields.io/badge/Min_SDK-23-orange.svg)]()

> **Code anywhere. Preview instantly. Ship confidently.** VCode is a premium mobile IDE for web developers — built from the ground up to feel fast, beautiful, and powerful on any Android device.

---

## Why VCode?

Most mobile code editors feel like toys. VCode doesn't.

We've obsessed over every detail — from the smoothness of tab switching to the way autocomplete understands your HTML structure — so you get a **genuine coding experience** wherever you are. Whether you're polishing a personal project on your commute or making a critical hotfix from your phone, VCode has your back.

---

## 🌟 Features

### ⚡ Blazing‑Fast Code Editor

Write code at the speed of thought. VCode's editor never stutters, never freezes, and never loses your place.

- **Instant tab switching** — open tabs stay live in memory, so jumping between files is always instant with zero flicker.
- **Smart syntax highlighting** for HTML, CSS, JavaScript, and JSON — rendered beautifully in JetBrains Mono.
- **Context‑aware autocomplete** that actually understands what you're writing:
  - CSS suggestions inside `<style>` tags and `style="..."` attributes.
  - JavaScript suggestions inside `<script>` blocks.
  - Project‑wide variable and function discovery.
  - File and folder name suggestions with icons.
- **Built‑in Emmet Expansion** — type shorthand abbreviations like `ul>li.item*3` in HTML or `m10`, `df`, `jcc` in CSS and instantly expand them into full code. The `!` abbreviation injects your custom HTML boilerplate with your cursor already inside `<body>`.
- **One‑tap code formatting** for JS, CSS, HTML, and JSON — your cursor stays exactly where it was.
- **Smart overflow menu** — text‑editing tools (Find/Replace, Go to Line, Format Code) automatically appear or hide based on the active file type and view mode.
- Auto‑closing brackets, smart indentation, Find & Replace with regex, and real‑time JSON validation.
- **Intelligent diagnostics** — real‑time linting for HTML, CSS, JavaScript, and TypeScript with color‑coded squiggly underlines (red errors, yellow warnings, blue hints) drawn directly in the editor at the exact token position.

---

### 👁️ Beautiful File Previews

Stop switching apps just to see what something looks like.

- **Live Web Preview** — run your *currently active* HTML file instantly in a powerful WebView, or pop it open in your browser. The Run button appears automatically only when relevant — animated and context‑aware.
- **Markdown rendering** — your `.md` files render as rich, formatted documents right inside the editor.
- **SVG, CSV, and Image previews** — files open directly in their visual representation, no extra steps.
- **JSON Viewer** — formatted, syntax‑highlighted JSON at a glance.

---

### 🌿 Full Git & GitHub Integration

VCode treats Git as a first‑class citizen, not an afterthought.

- **Complete Git workflow** — initialize, stage, commit, branch, and push, all without leaving the app.
- **GitHub connected** — link your account securely and sync your work with a tap.
- **Visual Diff Viewer** — review every change before you commit.
- **Commit History** — a clean, scrollable timeline of your project's story.
- **Background cloning** — clone large repositories without a single UI freeze, with live progress updates.

---

### 📁 Desktop‑Grade File Management

Your workspace, your way.

- **Premium file tree** — long‑press any file or folder to reveal a beautiful animated context menu with instant actions.
- **Full clipboard support** — Copy, Cut, and Paste files and entire folders across your project.
- **Visual Cut feedback** — cut files dim to 70% opacity, just like macOS and Windows.
- **Path copying** — grab the Absolute or Relative path of any file in one tap.
- **Background file operations** — importing or pasting large folders runs silently in the background with a progress notification and a Cancel button.
- **Project templates** — start new projects instantly with Blank, HTML, or HTML+CSS+JS layouts.
- **Snippet Manager** — save your most‑used code blocks and insert them anywhere with a single tap.

---

### 🛡️ Your Work Is Always Safe

- **Unsaved‑Changes Guard** — VCode detects unsaved edits and asks before you navigate away. No more accidental losses.
- **Auto‑sync project list** — newly cloned or created projects appear in your list automatically.

---

### 🎨 A UI You'll Actually Enjoy

- **Material Design 3** with a fully polished dark theme.
- **Sora typography** for crisp, readable UI text everywhere.
- **Smooth micro‑animations** and transitions that make the app feel alive.
- Circular progress indicators, animated menus, and carefully tuned visual feedback throughout.

---

## 🚀 Getting Started

1. Install the latest stable version from the releases section.
2. Tap **New Project** and choose a template, or clone a repo.
3. Open files from the file tree, write code, and see your changes live.
4. Commit and push with Git — right from within the app.

---

## 🤝 Contributing

VCode is open‑source and we love contributions! Here's how to get involved:

1. **Fork** the repository.
2. **Create** a feature branch: `git checkout -b feature/YourFeature`
3. **Implement** your changes in **Java only** (no Kotlin). All Android resources must use the `vcode_` prefix (e.g. `@color/vcode_accent_primary`). Avoid Retrofit, Room, Hilt, and RxJava — use `HttpURLConnection` and `org.json` instead.
4. **Build** and verify: `./gradlew assembleDebug`
5. **Open** a Pull Request with a clear description of what you've changed and why.

---

## 📜 License

Distributed under the **Apache License 2.0**. See [`LICENSE`](LICENSE) for more information.

---

*VCode — Stop settling for less. Code like a pro, from your pocket.*

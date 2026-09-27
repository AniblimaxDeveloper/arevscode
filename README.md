# arevscode

arevscode is an Android-first mobile development workspace inspired by the workflow of Android Studio, VS Code, Acode, GitHub and a coding copilot.

## Included in Foundation v0.1.0

- Modern dark IDE shell
- Home dashboard
- File/project workspace powered by Android Storage Access Framework
- In-app text editor with line numbers and unsaved state
- Built-in command terminal using the device shell
- Git workspace panel foundation
- GitHub panel foundation
- AI Copilot panel with Gemini REST integration
- Settings for API key and model
- GitHub Actions APK build

## Important architecture notes

The terminal in this foundation uses the Android device shell and app-local workspace. Android does not expose a general-purpose Linux distribution to third-party apps by default. A future terminal engine can be plugged into this UI; libtermux-android is one possible route, but its current upstream project is explicitly marked experimental.

The AI provider is isolated in `GeminiCopilot` so an OpenAI-compatible or local provider can be added later without rewriting the UI. The foundation currently uses Gemini `generateContent` for a small, reliable client implementation; Google currently recommends the newer Interactions API for new Gemini projects, while `generateContent` remains supported.

## Gemini setup

Open **Settings** inside the app and paste your Gemini API key. The default model is `gemini-3.8-flash`. The Gemini REST API remains available for `generateContent`; the newer Interactions API can replace this provider later.

## Build

Recommended for GitHub Actions: push this folder to a repository and run the `Build arevscode APK` workflow. The workflow uses JDK 17 + Gradle 9.6 and builds `app-debug.apk`.

Local:

```bash
gradle :app:assembleDebug
```

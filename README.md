# PDF Master Pro

**Everything PDF. One Powerful App.**

A complete, offline-first native Android PDF creation, editing, organizing, scanning, OCR, compression, and security suite built with Kotlin, Jetpack Compose, and Material 3.

## Architecture & Tech Stack

- **UI & Design**: Jetpack Compose, Material 3 (`#7047EB` Primary Purple, `#3478F6` Secondary Blue), Light/Dark/System themes, responsive `NavigationBar` / `NavigationRail` layouts, and full English & Bengali (`বাংলা`) UI localization.
- **Typography**: Local Google Fonts (`Outfit`, `Plus Jakarta Sans`, `JetBrains Mono`, and `Noto Sans Bengali` for Unicode PDF generation & UI).
- **Architecture**: MVVM + Repository Pattern (`AppContainer` DI, `ViewModel`, `StateFlow`, `collectAsStateWithLifecycle`).
- **Persistence**:
  - **Room Database** (`PdfMasterDatabase`): `pdf_documents`, `recent_documents`, `favorite_documents`, `processing_history`, `user_settings`.
  - **DataStore Preferences** (`SettingsDataStore`): theme, language, default page size/orientation/compression/font size, SAF export folder, onboarding status, favorite tools.
- **PDF Engine**:
  - `PdfBox-Android` (`com.tom-roush:pdfbox-android:2.0.27.0`) for creation, merging, splitting, rotation, reordering, compression, encryption, metadata, bookmarks, watermarks, signatures, and text extraction.
  - Android `PdfRenderer` for fast, thread-safe page preview & thumbnail rendering.
  - Android `PrintManager` + `FileProvider` for system printing and sharing.
  - Android Storage Access Framework (`OpenDocument`, `CreateDocument`, `OpenDocumentTree`) & zero-permission Photo Picker (`PickVisualMedia`).
- **Scanner & OCR**:
  - `CameraX` (`PreviewView`, `ImageCapture`) + interactive 4-corner perspective correction, rotation, brightness/contrast, and Original / Color Boost / Grayscale / B&W filters.
  - `ML Kit Text Recognition` (`com.google.mlkit:text-recognition`) for offline Latin OCR with transparent Bengali model availability notice and embedded Unicode PDF text extraction.
- **Background Work**: `WorkManager` (`PdfBackgroundWorker`) for long-running compression tasks.

## Building & Testing

- **Minimum SDK**: Android 8.0 (API 26)
- **Target / Compile SDK**: 36
- **Build Debug APK**: `gradle assembleDebug`
- **Build Release APK**: `gradle assembleRelease` (configured with signing configs in `app/build.gradle.kts` and ProGuard rules in `app/proguard-rules.pro`)
- **Run Unit & Robolectric Tests**: `gradle :app:testDebugUnitTest`

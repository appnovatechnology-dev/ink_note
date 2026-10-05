# Inkstead

**Inkstead** is an offline-first, precision digital notebook application for Android built with Kotlin and Jetpack Compose. Designed specifically for active stylus writing, technical drawing, synchronized audio lecture recording, and vector PDF annotation.

## Core Features

- **Precision Stylus Canvas**: Low-latency handwriting engine with bezier curve smoothing, pressure sensitivity, and robust palm rejection (Stylus-only and Auto-detect modes).
- **Geometric Drafting Tools**: Built-in interactive digital ruler, protractor, compass, and shape detection (lines, circles, ellipses, rectangles, polygons).
- **Synchronized Audio & Playback**: Record lectures, meetings, and discussions with real-time waveform visualization, time-synchronized note timestamps, and offline speech-to-text transcriptions.
- **Multi-Format Note Management**: Organize notes into subjects and notebooks, drag-and-drop categorization, rich text boxes, image insertion, and page management.
- **Smart Study & AI Tools**: Handwriting-to-Text OCR, Math Solver, Flashcards, Quiz Generator, and Note Summarization powered by Google Gemini.
- **Vector PDF Annotation & Flexible Export**: Import multi-page PDF documents for direct vector markup, and export full notebooks or specific page ranges (e.g. `1, 3-5, 8`) to high-resolution vector PDF.
- **100% Local & Private**: No mandatory user accounts, cloud lock-in, or telemetry. All user notebooks, audio recordings, and vector strokes remain strictly on device with Room database persistence.

## Architecture

- **UI Framework**: Jetpack Compose with Material Design 3 (M3)
- **Local Persistence**: Android Room Database with Flow observables
- **Stylus Processing**: Custom touch event disambiguation with `MotionEvent` tool type classification
- **Vector PDF Engine**: Android `PdfRenderer` and vector `PdfDocument` rendering pipeline
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) with AndroidX libraries

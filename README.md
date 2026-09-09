# CartBridge

A modern, open-source Android utility that extracts grocery and cart items from screenshots using a hybrid on-device OCR + cloud generative AI approach. It provides a persistent local history and a floating bubble to capture long-scrolling screenshots from other apps.

> **Note**: CartBridge has recently been open-sourced and is currently undergoing a major architectural rewrite to adopt modern Android development standards. See the [Recent Changes](#recent-changes) section for details!

## Table of Contents
- [Project Summary](#project-summary)
- [Key Features](#key-features)
- [Tech Stack & Architecture](#tech-stack--architecture)
- [Recent Changes](#recent-changes)
- [How to Build and Run](#how-to-build-and-run)
- [Firebase Configuration](#firebase-configuration)
- [Contributing](#contributing)
- [License](#license)

## Project Summary
CartBridge lets users take (or share) a screenshot of a shopping cart / grocery UI and converts it into a structured list of items (name + quantity). It utilizes a floating overlay bubble for quick long-scroll screenshots, stitches frames into a tall bitmap, runs OCR on the image locally (ML Kit), and extracts structured items using a cloud generative AI model (via Firebase AI). Extracted items are stored locally in Room and displayed in a Jetpack Compose UI.

## Key Features
- **Floating Bubble Overlay**: Quick trigger for long screenshot captures from any app.
- **Long-Screenshot Capture Service**: Programmatically scrolls the foreground app and stitches frames using an Accessibility Service.
- **Hybrid Extraction**: On-device OCR (ML Kit Text Recognition) followed by a generative model (Firebase AI) to extract structured data.
- **Local Persistence**: Stores history locally using Room Database.
- **Modern UI**: Built entirely with Jetpack Compose.
- **Remote Configuration**: Configurable extraction prompts via Firebase Remote Config.

## Tech Stack & Architecture

The app follows **Clean Architecture** principles and is structured into distinct layers (`app`, `di`, `presentation`, `domain`, `data`, `utils`). The UI layer follows the MVI (Model-View-Intent) pattern.

- **UI**: Jetpack Compose, Material 3
- **Language**: Kotlin 2.4.10
- **Dependency Injection**: Koin
- **Navigation**: Jetpack Navigation Compose
- **Image Loading**: Coil & Glide
- **Local Storage**: Room (via KSP 2.3.10)
- **AI & ML**: Firebase AI (Gemini Flash), Google ML Kit (Text Recognition)
- **Backend Infrastructure**: Firebase (App Check, Remote Config, Crashlytics, Analytics)

## Recent Changes
The project was recently open-sourced and heavily refactored to align with modern Android best practices:
- **Major Tech Upgrade**: Upgraded to Kotlin 2.4.10, KSP 2.3.10, and Target SDK 37.
- **Clean Architecture Refactor**: Separated concerns into `presentation`, `domain`, and `data` packages.
- **Dependency Injection**: Replaced manual factories with **Koin** for dependency injection.
- **Navigation**: Integrated **Jetpack Navigation** for Compose.
- **UI Modernization**: Revamped the UI with Material 3 Scaffold, updating `MainActivity` and extracting `HomeScreen` into a dedicated presentation module.

## How to Build and Run

### Prerequisites
1. Open the project in Android Studio.
2. Create a Firebase project and add an Android app with the package name `com.anantmittal.cartbridge`.
3. Download the `google-services.json` file and place it in the `app/` directory. **Do not commit this file.**
4. Ensure your Firebase project has Firebase AI, Remote Config, and App Check enabled.

### Building
From Android Studio, click **Run** or use the Gradle wrapper from the command line:
```bash
# Debug build
./gradlew assembleDebug

# Install on connected device
./gradlew installDebug
```

## Firebase Configuration
- **Remote Config**: The app relies on the `extraction_prompt` key. A default is provided in `CartBridgeApplication`, but you can tweak the prompt in the Firebase Console to improve extraction accuracy.
- **App Check**: The app uses `DebugAppCheckProviderFactory` for debug builds and `PlayIntegrityAppCheckProviderFactory` for production releases.
- **Generative AI**: Ensure the Firebase AI API is active in your Google Cloud project.

## Contributing
CartBridge is open source and we welcome contributions! As the app is currently in the middle of a UI/architecture rewrite, here are great areas to contribute:
- Migrating the legacy `FloatingBubbleService` and `LongScreenshotCaptureService` to the new architecture.
- Re-implementing the `HybridExtractionRepository` within the `domain` and `data` layers.
- Adding Unit and UI tests.
- UI improvements for the Compose screens.

Please feel free to open issues or submit Pull Requests. Make sure to keep secrets (like `google-services.json`) out of your commits!

## License
This project is open-source. (Please include an explicit LICENSE file in your repository, e.g., MIT or Apache 2.0).

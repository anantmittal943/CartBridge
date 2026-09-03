CartBridge
===========

A mobile utility that extracts grocery/cart items from screenshots using a hybrid on-device OCR + cloud generative AI approach, provides a persistent local history, and offers a
floating bubble to capture long-scrolling screenshots from other apps.

This README documents the repository "as of now": architecture, how the app works end-to-end, important source files, build & run instructions, required permissions and platform
configuration (Firebase, App Check), debugging tips, and extension points.

Table of contents
-----------------

- Project summary
- Key features
- High-level architecture and data flow
- Important modules & source map (what each file does)
- Required permissions & Android settings (overlay, accessibility, media projection)
- Firebase configuration & notes (Remote Config, Firebase AI usage, App Check)
- How to build and run (Windows/Android Studio/CLI)
- Typical usage (how a user interacts and how the app processes screenshots)
- Troubleshooting and common pitfalls
- Extension points and ideas for future work
- Tests and development notes

Project summary
---------------

CartBridge lets a user take (or share) a screenshot of a shopping cart / grocery UI and converts it to a structured list of items (name + qty). It supports a floating overlay
bubble for quick long-scroll screenshots, stitches frames into a tall bitmap, runs OCR on the image locally (ML Kit on-device), and then refines/extracts structured items using a
cloud generative AI model (integrated through Firebase.ai in this code). Extracted items are stored locally in Room and displayed in a Compose UI.

Key features
------------

- Floating bubble overlay to trigger long screenshot capture from any app.
- Transparent activity to request MediaProjection permission (native consent dialog).
- Long-screenshot capture service that programmatically scrolls the foreground app and stitches frames.
- AccessibilityService-based fling gestures to perform scrolling reliably in the target app.
- On-device OCR (ML Kit Text Recognition) followed by generative model (Firebase AI) prompt-based extraction.
- Local persistence using Room (CartDatabase) with a simple DAO.
- Modern Kotlin + Jetpack Compose UI showing history + quick store links.
- Remote-configurable extraction prompt via Firebase Remote Config.

High-level architecture & dataflow
----------------------------------

1. Floating bubble (FloatingBubbleService)
    - Runs as a foreground service and shows a movable overlay bubble.
    - When user requests "Long Screenshot" it launches ScreenCaptureRequestActivity.

2. ScreenCaptureRequestActivity
    - Transparent activity which invokes MediaProjectionManager.createScreenCaptureIntent() — the system dialog appears.
    - On user approval it starts LongScreenshotCaptureService with the returned projection token.

3. LongScreenshotCaptureService
    - Creates a MediaProjection virtual display + ImageReader to capture frames.
    - Uses CartBridgeAccessibilityService.performScrollGesture() to fling/scroll the foreground app between captures.
    - Collects frames, detects when content stops changing (simple hash heuristic), stitches frames into a long bitmap, saves to cache, and shares the bitmap via an ACTION_SEND
      intent.

4. MainActivity
    - Declared to receive ACTION_SEND image/* intents and also acts as the app entry point.
    - When receiving an image URI, it down-samples the image (ImageDownsampler) and forwards the Bitmap to MainViewModel.processImage().

5. HybridExtractionRepository
    - Step 1: runs ML Kit on-device OCR to get raw text.
    - Step 2: fetches a Remote Config prompt (fallback default available in CartBridgeApplication). The prompt asks the generative AI to return JSON list of items in a strict
      format.
    - Step 3: calls Firebase.ai generative model (currently configured to use gemini-3.7-flash in code) to convert OCR text => JSON array.
    - Step 4: parses JSON into CartItem objects.

6. Persistence & UI
    - Parsed items are inserted into the Room database via CartDao.
    - MainViewModel exposes items as a StateFlow consumed by CartScreen (Compose) to display history and quick-store search links.

Important modules & source map
-----------------------------

Core application

- app/src/main/java/com/anantmittal/cartbridge/CartBridgeApplication.kt
    - Firebase initialization, Remote Config defaults, App Check setup (debug vs production).

Entry & UI

- app/src/main/java/com/anantmittal/cartbridge/MainActivity.kt
    - Receives shared images, constructs ViewModel (MainViewModelFactory) wired with HybridExtractionRepository and Room DAO.
- app/src/main/java/com/anantmittal/cartbridge/ui/CartScreen.kt
    - Jetpack Compose UI that displays item history, start/stop floating bubble control, and per-item store buttons.
- app/src/main/java/com/anantmittal/cartbridge/ui/MainViewModel.kt
    - Orchestrates calling repository to process images and updates Room via CartDao.
- app/src/main/java/com/anantmittal/cartbridge/ui/MainViewModelFactory.kt
    - Simple factory wiring dependencies for the ViewModel.

Data Layer

- app/src/main/java/com/anantmittal/cartbridge/data/CartItem.kt
    - Serializable Room entity: id, name, qty, timestamp.
- app/src/main/java/com/anantmittal/cartbridge/data/CartDao.kt
    - DAO exposing Flow<List<CartItem>> and insert/clear methods.
- app/src/main/java/com/anantmittal/cartbridge/data/CartDatabase.kt
    - Room database singleton with builder.

Screenshot & capture pipeline

- app/src/main/java/com/anantmittal/cartbridge/bubble/FloatingBubbleService.kt
    - Foreground overlay bubble UI, menu, and flow to request long screenshot.
- app/src/main/java/com/anantmittal/cartbridge/screenshot/ScreenCaptureRequestActivity.kt
    - Transparent activity for MediaProjection request.
- app/src/main/java/com/anantmittal/cartbridge/screenshot/LongScreenshotCaptureService.kt
    - Captures frames via MediaProjection → ImageReader, instructs accessibility service to scroll, stitches frames, saves & shares result.
- app/src/main/java/com/anantmittal/cartbridge/screenshot/CartBridgeAccessibilityService.kt
    - AccessibilityService used to dispatch fling-up gestures to the foreground app.

OCR / AI extraction

- app/src/main/java/com/anantmittal/cartbridge/domain/HybridExtractionRepository.kt
    - Runs ML Kit on-device OCR, fetches Remote Config prompt, tries to use a local/gemini-nano path (placeholder), falls back to Firebase.ai generative model, parses JSON into
      CartItem list.

Utilities

- app/src/main/java/com/anantmittal/cartbridge/utils/ImageDownsampler.kt
    - Downsamples large images safely to avoid OOM before sending to OCR.

Build and dependency scripts

- build.gradle.kts (root)
- app/build.gradle.kts (module) — uses Kotlin DSL, Compose, Room (ksp), ML Kit text-recognition, Firebase modules, and Firebase.ai
- settings.gradle.kts

AndroidManifest highlights

- Declares CartBridgeApplication, MainActivity with SEND intent-filter for images, ScreenCaptureRequestActivity (transparent), FloatingBubbleService, LongScreenshotCaptureService (
  mediaProjection), AccessibilityService, and FileProvider for sharing saved screenshots.

Required permissions & Android settings
-------------------------------------

- INTERNET: network access for Firebase and generative model usage.
- SYSTEM_ALERT_WINDOW: allow drawing overlays (floating bubble). The app prompts the user to open Settings to grant this via CartScreen.
- FOREGROUND_SERVICE and related special-use permissions: persistent foreground services for overlay and media projection.
- Accessibility service: user must enable Cart Bridge under Settings → Accessibility to let the app perform programmatic fling/scroll gestures used during long-screenshot capture.
- MediaProjection consent: every long-screenshot capture shows the system consent dialog (no extra permissions required, but user must tap allow).

Firebase configuration & notes
------------------------------

This project expects Firebase to be set up in the Android app. The code references Firebase.ai (the Firebase Generative APIs) and Remote Config. To run end-to-end locally, do the
following:

1. Create a Firebase project in the Firebase Console.
2. Add an Android app with package name com.anantmittal.cartbridge.
3. Download google-services.json and place it under app/ (app/google-services.json). Do NOT commit secrets to source control.
4. Enable Remote Config in the console and optionally set the "extraction_prompt" key to tune the model prompt.
5. Enable App Check (Play Integrity or other) if you plan production deployment. In debug builds the app installs DebugAppCheckProviderFactory to ease development.
6. Configure Firebase Generative AI access if required by Google/Firebase (APIs/quotas, early access, SDK availability). The repository uses Firebase.ai(backend =
   GenerativeBackend.googleAI()) — this requires the Firebase AI SDK to be available and the appropriate access/credentials set in your Firebase project.

Notes / caveats:

- The code includes a placeholder block that attempts to use a local "Gemini Nano" path; this is intentionally wrapped to fall back to Firebase.ai. Integrating an on-device LLM or
  alternative cloud LLM requires adding the correct SDK/initialization.
- Do not commit google-services.json to a public repo. Use environment-specific configuration or CI secrets for automated builds.

How to build & run
------------------

From Android Studio (recommended):

- Open the project in Android Studio.
- Gradle sync should run (ensure you have the Android SDK and Kotlin configured).
- Add app/google-services.json and install required SDKs.
- Run the app on a device (emulator may not support overlay or the exact media projection behaviour as a real device).

From the command line (Windows):

- Open a Developer PowerShell in the repo root.
- To assemble a debug APK: .\gradlew.bat assembleDebug
- To install on a connected device: .\gradlew.bat installDebug

Important run-time steps on a device

- Grant Overlay permission: Settings → Apps → Special access → Display over other apps → enable for Cart Bridge. The UI also shows an in-app prompt linking to the overlay settings.
- Enable Accessibility service: Settings → Accessibility → Cart Bridge Long Screenshot → enable.
- When taking a long screenshot via the bubble, accept the system MediaProjection prompt.

Typical usage
-------------

1. Start the app. From the main screen, tap "Start Bubble" to create the floating bubble (first-time overlay permission may be needed).
2. In any shopping/cart app, tap the floating bubble → menu → Long Screenshot. The bubble will request screen capture permission and then start a long-capture.
3. The app will programmatically fling/scroll the foreground app while capturing frames, stitch a tall bitmap, then share it back to CartBridge via an ACTION_SEND intent.
4. CartBridge receives the image, down-samples it, runs OCR, sends OCR text + prompt to generative model, receives JSON list, parses it into CartItem objects, and persists them to
   Room.
5. Items show up in the app history. Use the quick-store buttons to open store search results.

Troubleshooting & common pitfalls
--------------------------------

- Overlay not appearing: ensure the SYSTEM_ALERT_WINDOW (draw over other apps) permission is enabled for the app in system settings.
- Accessibility gestures not working: make sure the accessibility service is enabled and the target app allows programmatic gestures. Some apps may block or behave differently.
- MediaProjection denied/cancelled: user must accept the system dialog every capture. If denied the capture service stops.
- OOM on very large images: ImageDownsampler caps max dimension to avoid OOM, but very large unbounded images or extremely long stitched bitmaps may still cause memory pressure.
  Consider offloading stitching to disk-based approach if needed.
- Firebase Generative AI errors: ensure Firebase project has the generative AI feature enabled and that the app's google-services.json corresponds to the project. Check logcat for
  errors from Firebase.ai.

Configuration & tuning
----------------------

- Change extraction prompt: Use Firebase Remote Config key extraction_prompt (default set in CartBridgeApplication). Tune prompt text in Remote Config to improve parsed results.
- Room DB: file name "cart_database". To inspect DB while debugging, use Android Studio Database Inspector.
- Overlap percentage for stitching: LongScreenshotCaptureService uses a fixed 20% overlap. Adjust if content cropping occurs.

Testing notes
-------------

- Unit tests: example unit tests are present (androidTest/ and test/ placeholders). Add targeted unit tests for ImageDownsampler and HybridExtractionRepository parsing logic.
- Instrumentation: long-screenshot flows require device features and permissions — emulate manually for integration testing.

Extending the project (suggestions)
----------------------------------

- Add more robust stitching with visual overlap detection (e.g., cross-correlation) rather than fixed 20%.
- Add a server-side parsing fallback or multiple LLM backends (OpenAI, Vertex) for resilience and cost control.
- Improve UI: allow editing parsed items, merging duplicates, and exporting lists (CSV/JSON).
- Add unit/integration tests around OCR→LLM→parsing chain by mocking Firebase.ai and ML Kit responses.
- Add analytics/telemetry (careful with PII) to measure extraction success rates and prompt improvements.

Security & privacy
------------------

- The app uploads raw OCR text to a generative AI backend for extraction — this may include sensitive information. Clearly disclose in privacy policy and consider on-device-only
  models if privacy is required.
- Do not store google-services.json or other secrets in a public repository.
- Use Firebase App Check and production App Check provider (Play Integrity) to protect backend access.

Files worth reading first
------------------------

- app/src/main/java/com/anantmittal/cartbridge/domain/HybridExtractionRepository.kt — core OCR + AI extraction logic.
- app/src/main/java/com/anantmittal/cartbridge/screenshot/LongScreenshotCaptureService.kt — long capture pipeline, stitching and sharing.
- app/src/main/java/com/anantmittal/cartbridge/bubble/FloatingBubbleService.kt — overlay UI and menu interactions.
- app/src/main/java/com/anantmittal/cartbridge/ui/CartScreen.kt — Compose UI and how users control the bubble.

License
-------

(Repository does not include an explicit license file as of this README snapshot. Add a LICENSE file if you want to make the project open-source under a specific license.)

Contact / contribution
----------------------

If you (the reader) are the maintainer, use branches and create PRs, write unit tests for newly added logic, and keep secrets out of source control. If you want me to generate a
CONTRIBUTING.md, a MIT/Apache license, or open an initial set of unit tests for the parsing logic, ask and I will create them.

-- End of README --

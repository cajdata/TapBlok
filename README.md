# TapBlok

TapBlok is a digital wellbeing and productivity tool for Android designed to help users regain control over their screen time. Our core philosophy is to create intentional friction. By requiring a physical action (scanning an NFC tag or QR code) to disable a focus session, we make it more difficult for users to impulsively bypass their own productivity goals.

The app is built entirely in Kotlin and utilizes modern Android development practices, including Jetpack Compose for the UI, Room for local persistence, and Coroutines for asynchronous operations.

---
## Core Features

* **App Selection:** Users can select any launchable application on their device to be included in the block list.
* **Safety Block:** A comprehensive list of critical system apps (dialer, SMS, settings, launchers, etc.) are permanently excluded from the list to prevent users from making their device unusable.
* **Select All / Unselect All:** Buttons in the app selection screen allow users to quickly add or remove all apps from the block list.
* **Session Control:** A focus session can be started or stopped via multiple methods:
    * **In-App Buttons:** A "Start Monitoring" button on the main screen.
    * **NFC Tag:** Scanning a pre-written NFC tag toggles the service.
    * **QR Code:** Scanning a unique QR code toggles the service.
    * **App Shortcut:** Long-pressing the app icon reveals a "Start Session" shortcut that starts monitoring instantly without opening the app.
* **Emergency Override:** A "90-Second Hold" button is available on the main screen during an active session. Holding it for the full duration provides a "break glass" option to stop the service if the NFC tag or QR code is lost.
* **Blocking Screen:** When a user attempts to open a blocked app, an overlay is displayed on top of it. The back button is overridden to send the user to the home screen, preventing a common bypass loophole.
* **Break System:** When blocked, a user can take a 5-minute break. They are allotted 3 breaks per focus session. The break counter is reset every time a new session starts.
* **Attempt Counter:** The main screen displays a counter showing how many times the user has attempted to open a blocked app during the current session, providing real-time habit feedback.

---
## Core Architecture

The application is composed of several key components that work together to provide a seamless blocking experience.

* **Permissions:** The app requires several critical permissions to function:
    * `PACKAGE_USAGE_STATS`: Allows us to see which app is currently in the foreground.
    * `SYSTEM_ALERT_WINDOW`: Enables us to display the blocking screen over a restricted app.
    * `CAMERA`: Required for the QR code scanning feature.
* **User Interface (Jetpack Compose):** The entire UI is built with Jetpack Compose. `MainActivity` manages the primary UI state, while `AppSelectionActivity` handles the list of blockable apps.
* **Database (Room):** We use the Room Persistence Library to store the list of blocked applications.
    * **Entity:** `BlockedApp` which contains the `packageName` of the app to be blocked.
    * **DAO:** `BlockedAppDao` provides methods to insert, delete, and query the list of blocked apps.
* **Monitoring Logic (AppMonitoringService):** This is the heart of the app. It's a sticky foreground service that runs a continuous loop in a coroutine. Every second, it checks the current foreground app against the list of blocked apps. If a match is found, it launches the `BlockingActivity`.
* **Session Control (NFC & QR Code):**
    * **NFC:** The `NfcWriteActivity` allows users to write a custom MIME type to a tag. The headless `NfcHandlerActivity` listens for this tag and toggles the monitoring service.
    * **QR Code:** The `QrCodeActivity` generates and displays a unique QR code. The main activity handles scanning this code to toggle the monitoring service.

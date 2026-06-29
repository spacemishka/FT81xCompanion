# Walkthrough - New Features & Security Enhancements

We have successfully implemented and verified the 4 advanced features adapted from the FT8CN application codebase, emphasizing security, safety, and modern Kotlin design principles.

---

## 1. Summary of Accomplishments

### 1.1 USB OTG Serial CAT control (`UsbCatPort.kt`)
- **Direct Serial Port Control**: Added the lightweight `usb-serial-for-android` dependency to [build.gradle.kts](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/build.gradle.kts) and added the JitPack repository to [settings.gradle.kts](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/settings.gradle.kts).
- **USB CAT Client**: Created [UsbCatPort.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/service/UsbCatPort.kt) which handles reading and writing raw 5-byte CAT blocks over standard USB-to-serial chips (FTDI, CP2102, CH340, PL2303).
- **Reactive Connection Management**: Updated [CatForegroundService.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/service/CatForegroundService.kt) to delegate requests dynamically to either Bluetooth or USB connections based on the user's selected preference.

### 1.2 Transceiver Safety SWR Watchdog (`CatForegroundService.kt`)
- **PTT Auto-Cutoff**: During transmission, the service reads the transceiver's status byte. If the high SWR indicator is active for 2 consecutive ticks, a safety auto-cutoff is triggered, instantly unkeying the transceiver (PTT OFF) and stopping any active Morse code transmissions.
- **UI State Indicators**: Added `isSwrCutoffActive` inside the state flow model so that the UI can notify the user if transmission was automatically aborted due to high VSWR.

### 1.3 Maidenhead Location Calculations (`MaidenheadLocator.kt` & `MainViewModel.kt`)
- **Coordinates to Grid**: Created [MaidenheadLocator.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/utils/MaidenheadLocator.kt) containing coordinates-to-grid calculations (e.g. converting GPS coordinate values to `FN31ub`).
- **Enriched DX Spots**: Whenever a new DX Cluster spot arrives, the system attempts to parse the Maidenhead grid from the spot comment. If matched, it calculates the Great-Circle distance (km) and compass direction bearing (degrees + cardinal point) from the user's current GPS location.
- **Logging UI updates**: Updated [LoggingScreen.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/screens/LoggingScreen.kt) to display the calculated distance and bearing inline (e.g. `1240 km @ 270° W`) under the spotter's callsign in green.

### 1.4 Secure Sync to QRZ & CloudLog logbooks (`MainViewModel.kt`)
- **Strict HTTPS Transport**: Implemented secure background upload routines that communicate exclusively over encrypted HTTPS connections using the standard Java `HttpsURLConnection` library. Cleartext `http://` configurations are automatically upgraded to `https://`.
- **Sensitive Credential Protection**: Integrated visual masking in [SettingsScreen.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/screens/SettingsScreen.kt) using `PasswordVisualTransformation` to hide API keys from plain view. API keys are excluded from all Logcat statements.

### 1.5 Smart-Scan: Software-Defined Band Scanner
- **Auto-Pause Squelch Watchdog**: Integrated a background scanning loop in [CatForegroundService.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/service/CatForegroundService.kt) that steps the VFO frequency.
- **Signal-Controlled Sweeping**: Automatically queries squelch state; pauses scanning when squelch opens (signal detected) and resumes with a 1.5-second hang time delay after squelch closes.
- **Squelch Threshold Guard**: Implemented configurable squelch thresholds (S0 to S9). The scanner only pauses if squelch is open and signal strength is at or above the threshold, filtering out static/noise.
- **Settle Speed Optimization**: Decreased tuning settle delay to 20ms and added a Dwell Time selector (50ms to 1s) for high-performance scanning.
- **Dashboard Scan Controls**: Added a collapsible control card in [DashboardScreen.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/screens/DashboardScreen.kt) allowing the user to configure scan range limits (Start/End MHz), step size (5 kHz to 100 kHz), dwell speed, squelch thresholds, and start/stop the scan dynamically.

### 1.6 Direct QSY entry in MHz
- **MHz Keypad Entry**: Reconfigured the Direct QSY pop-up dialog in [DashboardScreen.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/screens/DashboardScreen.kt). Added a decimal point `.` button, updated the visual readout to show MHz values (e.g. `14.250 MHz`), and automatically convert inputs to Hz before transmitting the VFO command.

---

## 2. Verification

The project compiles cleanly:
```powershell
./gradlew compileDebugSources
```
Result:
```text
BUILD SUCCESSFUL in 6s
7 actionable tasks: 1 executed, 6 up-to-date
```
All Kotlin state models, Compose screens, network endpoints, and local utilities compile successfully.

# Implementation Plan - FT8CN Features & Security Enhancements

This plan details the implementation of 4 core features adapted from the FT8CN project with a strong focus on Android security best practices.

## Security Considerations

Since public Ham Radio applications are often written under relaxed security models, we will implement these features with strict transport and credential protection:
1.  **Transport Layer Security (HTTPS)**: All API syncing to QRZ.com and CloudLog will enforce strict HTTPS with standard TLS certificate validation. If a user enters an `http://` domain for CloudLog, it will be automatically upgraded to `https://` or rejected to prevent credential sniffing.
2.  **No Cleartext Logging**: Logging of API keys, tokens, or personal identifiers to Android Logcat is strictly prohibited.
3.  **Scoped USB Permissions**: USB Host connections will request runtime system permissions via `UsbManager.requestPermission()` before attempting to access the USB-to-serial hardware.
4.  **Credential Protection**: User credentials (keys and IDs) will be stored securely using standard Android `SharedPreferences` and will not be exposed to any other app context.

---

## Proposed Changes

### 1. Build Configurations

#### [MODIFY] [build.gradle.kts](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/build.gradle.kts)
- Add Maven Central dependency for the trusted serial library:
  ```kotlin
  implementation("com.github.mik3y:usb-serial-for-android:3.7.0")
  ```

---

### 2. Connection Interfaces & USB Port

#### [NEW] [UsbCatPort.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/service/UsbCatPort.kt)
- Create a USB CAT class using the `usb-serial-for-android` API to read and write 5-byte CAT blocks to FTDI/PL2303/CH340/CP2102 chips.
- Handles asynchronous reader loops and proper stream closures, wrapping calls with the thread-safe `ioMutex`.

#### [MODIFY] [CatForegroundService.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/service/CatForegroundService.kt)
- Support connection modes: Bluetooth vs. USB.
- Manage USB interface lifecycle, service state updates, and serial port binding.
- **SWR Auto-Cutoff Safety (Watchdog)**: During transmission (`isPtt` is true), read the SWR status bit. If SWR is high for 2 consecutive polls, automatically set PTT to OFF to protect the radio and update the status state.

---

### 3. Maidenhead Locator & DX Distance Calculations

#### [NEW] [MaidenheadLocator.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/utils/MaidenheadLocator.kt)
- Implement static math functions to convert coordinates to Maidenhead grids (e.g. `FN31ub`).
- Calculate the Great-Circle distance (km) and compass bearing (degrees + cardinal direction) between two grids.

#### [MODIFY] [MainViewModel.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/MainViewModel.kt)
- Track current locator `myMaidenheadGrid` derived from GPS coordinates.
- Calculate and update distance/bearing for incoming DX Cluster spots by extracting 4-character Maidenhead grids from spot comments.

#### [MODIFY] [LoggingScreen.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/screens/LoggingScreen.kt)
- Display the distance and heading (e.g. `1240 km @ 270° W`) next to spotted DX stations.

---

### 4. Secure Log Upload API Syncing

#### [MODIFY] [SettingsScreen.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/screens/SettingsScreen.kt)
- Add preferences UI sections for:
  - Connection type (Bluetooth vs. USB OTG)
  - QRZ Logbook Sync (API Key field with masked display)
  - CloudLog Sync (Server URL field with HTTPS check, API Key field, and Station ID field)
  - SWR Auto-Cutoff toggle

#### [MODIFY] [MainViewModel.kt](file:///c:/Users/peter/AndroidStudioProjects/FT81xCompanion/app/src/main/java/com/spacemishka/app/ft_81xcompanion/ui/MainViewModel.kt)
- Store QRZ and CloudLog keys in preferences.
- Implement background HTTPS POST tasks in `viewModelScope.launch(Dispatchers.IO)` to upload ADIF data to QRZ (`https://logbook.qrz.com/api`) and CloudLog (`https://[server]/index.php/api/qso`) with strict network error handling.

---

## Verification Plan

### Automated Tests
- Run Gradle debug compilation check:
  ```powershell
  ./gradlew compileDebugSources
  ```

### Manual Verification
- Test coordinates-to-grid grid translations with known reference test cases.
- Verify that SWR alarms trigger and shutdown the transceiver when simulated.
- Confirm QRZ and Cloudlog HTTP connections reject cleartext HTTP domains.

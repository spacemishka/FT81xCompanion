# Code Review – Round 2 (Post-Fix)

Reviewed files: `CatPort.kt`, `CatProtocol.kt`, `CatForegroundService.kt`, `MorseSidetonePlayer.kt`, `MorseTranslator.kt`, `MainViewModel.kt`, `MorseScreen.kt`

---

## 🔴 Critical / High Priority

### Issue 1 — `onDestroy()` PTT-off is fire-and-forget (CatForegroundService.kt, L115–119)

**Problem**: On `onDestroy()`, the PTT-off CAT command is launched in a coroutine (`serviceScope.launch`) whose `serviceScope` is cancelled immediately after (`serviceScope.cancel()` on L122). The PTT-off command will virtually never actually be sent, leaving the transmitter stuck on.

**Fix**: Use a blocking approach in `onDestroy()` — either use a `runBlocking { }` block (safe in service lifecycle), or pre-cancel the scope only after awaiting the PTT-off job:
```kotlin
override fun onDestroy() {
    if (_radioState.value.isPttActive) {
        runBlocking(Dispatchers.IO) {
            catPort.sendCommand(CatProtocol.buildPttOff(), 1)
        }
    }
    sidetonePlayer.release()
    manualKeyChannel.close()
    serviceScope.cancel()
    catPort.closeSocket()
    super.onDestroy()
}
```

---

### Issue 2 — `closeSocket()` uses `synchronized(this)` while also using `ioMutex` (CatPort.kt, L110–125)

**Problem**: `closeSocket()` uses `synchronized(this)` for thread safety, but the rest of the class uses `ioMutex: Mutex()` (a coroutines-based lock). These two locking mechanisms protect the same data (`inputStream`, `outputStream`, `bluetoothSocket`) independently — they are not mutually exclusive. A coroutine holding `ioMutex` can race with a thread acquiring the `synchronized` lock, causing the streams to be closed mid-read/write.

**Fix**: Remove `synchronized(this)` from `closeSocket()` and make it only callable from within an `ioMutex` block (make it `private`). Callers go through `suspend fun disconnect()` which holds the mutex.

---

### Issue 3 — Farnsworth spacing formula is incorrect (CatForegroundService.kt, L259–263)

**Problem**: The timing calculations do not produce valid Paris-standard Morse timing:
- `charSpaceMs` subtracts `charUnitMs` from a Farnsworth-speed derived value inconsistently
- `wordSpaceMs` then further subtracts both `charSpaceMs` and `charUnitMs`, producing negative or near-zero values at high WPM

**Fix (standard Paris timing)**:
```kotlin
val dotMs = 1200L / wpm               // element unit at character speed
val farDotMs = 1200L / farnsworthWpm  // element unit at Farnsworth speed
val dashMs = dotMs * 3L
val interElementMs = dotMs            // 1 unit gap between elements
val interCharMs = farDotMs * 3L       // 3 Farnsworth units between characters
val interWordMs = farDotMs * 7L       // 7 Farnsworth units between words
```

---

## 🟡 Medium Priority

### Issue 4 — `dxSpots` is a public `MutableStateFlow` (MainViewModel.kt, L82)

**Problem**: `val dxSpots = MutableStateFlow<List<DxSpot>>(emptyList())` is exposed as-is. Any consumer can mutate it. Only the ViewModel should modify it.

**Fix**:
```kotlin
private val _dxSpots = MutableStateFlow<List<DxSpot>>(emptyList())
val dxSpots: StateFlow<List<DxSpot>> = _dxSpots.asStateFlow()
// Update all _dxSpots.value references to use _dxSpots
```

---

### Issue 5 — Satellite tracking mutates Compose state from `Dispatchers.Default` (MainViewModel.kt, L417)

**Problem**: `satelliteState = state` is a write to a Compose `mutableStateOf()` property performed on `Dispatchers.Default`. Compose state mutations from non-Main threads are unsafe and may cause crashes on some Android versions.

**Fix**: Wrap the state assignment in `withContext(Dispatchers.Main)`:
```kotlin
withContext(Dispatchers.Main) {
    satelliteState = state
}
```

---

### Issue 6 — Database state updated from `Dispatchers.IO` into Compose `mutableStateOf` (MainViewModel.kt, L234–236)

**Problem**: `qsoList = dbHelper.getAllQsos()` and `repeaterList = dbHelper.getAllRepeaters()` assign to Compose state properties while running on `Dispatchers.IO`. Same thread-safety concern as Issue 5.

**Fix**:
```kotlin
viewModelScope.launch(Dispatchers.IO) {
    val qsos = dbHelper.getAllQsos()
    val repeaters = dbHelper.getAllRepeaters()
    withContext(Dispatchers.Main) {
        qsoList = qsos
        repeaterList = repeaters
    }
}
```

---

### Issue 7 — Audio focus abandoned on every Morse element stop (MorseSidetonePlayer.kt, L117)

**Problem**: `stop()` calls `abandonAudioFocus()` internally. During auto-keyer playback, `stop()` is called between every dot and dash — potentially 10+ times per second. This creates a burst of audio focus acquire/release cycles which is wasteful and may cause momentary audio ducking from the OS between elements.

**Fix**: Add separate `pausePlayback()` / `resumePlayback()` methods that manipulate the `AudioTrack` only, without touching audio focus. Reserve `start()` / `stop()` for focus acquisition at the start/end of a full transmission.

---

### Issue 8 — `closeSocket()` is `public` but leaves `connectionState` stale (CatPort.kt, L110)

**Problem**: When external code (e.g., `onDestroy()`) calls `catPort.closeSocket()` directly, the `_connectionState` is not updated. It will still show `CONNECTED` after the socket is torn down. This causes polling loops to keep trying to read from a closed stream.

**Fix**: Make `closeSocket()` `private`. Replace `catPort.closeSocket()` in `onDestroy()` with a `runBlocking { catPort.disconnect() }` call which correctly updates state.

---

### Issue 9 — Unused import `BluetoothAdapter` (MainViewModel.kt, L5)

**Problem**: `import android.bluetooth.BluetoothAdapter` is dead code since the refactor. IDE warnings, and generally unclean.

**Fix**: Remove the import.

---

## 🟢 Low Priority / Code Quality

### Issue 10 — `Math.max()` instead of Kotlin `maxOf()` (CatForegroundService.kt, L262–263)

**Problem**: Java-style `Math.max(a, b)` is used. The idiomatic Kotlin equivalent is `maxOf(a, b)`.

**Fix**: Replace all `Math.max(...)` usages with `maxOf(...)`.

---

### Issue 11 — Sidetone frequency precision lost via `Int` cast (MorseScreen.kt, L299, L550)

**Problem**: `morseSidetoneFreq` is stored as `Float` but converted to `Int` before being passed to `transmitMorse()` and `setManualKey()`, then cast back to `Double` inside the player. This lossy conversion means 523.5 Hz becomes 523 Hz, degrading pitch accuracy.

**Fix**: Change the `sidetoneFreqHz: Int` parameters in `transmitMorse()` and `setManualKey()` (and their service equivalents) to `Float`. Pass directly without `.toInt()`.

---

### Issue 12 — Missing apostrophe `'` in Morse table (MorseTranslator.kt)

**Problem**: The apostrophe character `'` is missing from the Morse code map. Its ITU-R code is `.----.` and it is commonly needed for contraction words and some callsigns.

**Fix**: Add to `morseMap`:
```kotlin
'\'' to ".----."
```

---

## ✨ Suggested Enhancements

| # | Enhancement | File(s) | Impact |
|---|-------------|---------|--------|
| E1 | **Repeat Transmission**: Add a "Repeat N times" counter to the auto-keyer with a configurable inter-message gap in seconds. | `MorseScreen.kt`, `CatForegroundService.kt` | Medium |
| E2 | **Morse Decoder (Receive)**: A goertzel-filter audio decoder tab that listens via mic and decodes incoming CW in real-time. | New `MorseDecoder.kt` | High |
| E3 | **Auto-Reconnect on Error**: When `ConnectionState.ERROR` is set, automatically retry connection to `lastConnectedMacAddress` with exponential backoff. | `CatForegroundService.kt` | High |
| E4 | **Live TLE Auto-Update**: Fetch fresh TLE data from `celestrak.org` at launch (or cached daily). Current hard-coded TLEs will be stale within weeks. | `MainViewModel.kt` | High |
| E5 | **Sidetone Waveform Selection**: Let user choose Sine, Square, or Triangle wave for the sidetone tone. | `MorseSidetonePlayer.kt`, `MorseScreen.kt` | Low |
| E6 | **QSO Export to File**: Write ADIF string to the device Downloads folder using `MediaStore`, not just return a String from `exportAdif()`. | `MainViewModel.kt` | Medium |
| E7 | **PTT Timeout Setting**: Expose the 3-minute PTT timeout as a user-adjustable setting stored in `SharedPreferences`. | `CatForegroundService.kt` | Low |
| E8 | **CW Prosign Quick Presets**: Add `AR` (end of msg), `SK` (end of contact), `BT` (paragraph break), and `KN` (go-ahead specific) as quick-preset chips. | `MorseScreen.kt` | Low |

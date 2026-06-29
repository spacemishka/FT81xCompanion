# Requirements: Android CAT Controller for Yaesu FT-818ND via Bluetooth Adapter

**Version:** 1.0  
**Date:** 2026-06-24  
**Target Hardware:** Yaesu FT-818ND + CAT-to-Bluetooth Adapter (CT-62 compatible, e.g. Hilitand / EKP-H150206 8×7 or equivalent HC-05/HC-06 based dongle)  
**Target Platform:** Android 12 (API 31) or later

---

## 1. Background & Scope

The Yaesu FT-818ND exposes a **CAT (Computer Aided Transceiver)** serial interface on its rear-panel 6-pin Mini-DIN DATA connector. The CAT-to-Bluetooth adapter replaces the CT-62 USB/RS-232 cable and makes this interface wirelessly available as an **RFCOMM serial port (BT SPP — Serial Port Profile)**. The Android app shall connect to the adapter and provide full-featured transceiver control and logging from a smartphone or tablet, with no physical cable to the radio.

---

## 2. Protocol Specification

### 2.1 Physical / Link Layer

| Parameter | Value |
|---|---|
| Interface on radio | 6-pin Mini-DIN (ACC/DATA jack, pins TXD, RXD, GND) |
| Bluetooth profile | SPP (RFCOMM over BT Classic, BT 2.0+EDR minimum) |
| Default adapter baud rate | **9600 bps** (some adapters 4800 bps; must be configurable) |
| Radio CAT baud rates supported | 4800 / 9600 / 38400 bps (Menu Item #14 on radio) |
| Data format | **8N2** — 8 data bits, No parity, **2 stop bits** |
| Default BT pairing PIN | `1234` (typical for HC-05/HC-06 modules) |

> **Critical:** The FT-818ND uses 2 stop bits. Any UART configuration with 1 stop bit will cause framing errors and silent command failures.

### 2.2 CAT Command Frame Structure

Every command sent **to** the radio is exactly **5 bytes**:

```
[Data 1] [Data 2] [Data 3] [Data 4] [Opcode]
```

- All values are **hexadecimal**.
- All 5 bytes must be transmitted within **200 ms** of each other (ideally in a single write).
- Unused data bytes for a given command may contain any value (convention: `0x00`).
- The radio replies with either 1 byte (ACK/status) or 5 bytes (data), depending on the command.

**ACK byte semantics:**
- `0x00` — Command accepted / was not already in that state
- `0xF0` — Command rejected / was already in that state

### 2.3 Documented CAT Command Set (FT-817/818 identical)

| Opcode (HEX) | Command | Data Bytes | Response |
|---|---|---|---|
| `00` | Lock ON | ignored | 1 byte (ACK) |
| `01` | Set Frequency | BCD: 100MHz/10MHz \| 1MHz/100kHz \| 10kHz/1kHz \| 100Hz/10Hz | 1 byte (ACK) |
| `02` | Split ON | ignored | 1 byte (ACK) |
| `03` | Read Frequency & Mode | ignored | **5 bytes** |
| `05` | Clarifier (RIT) ON | ignored | 1 byte (ACK) |
| `07` | Set Operating Mode | mode byte (D1), rest ignored | 1 byte (ACK) |
| `08` | PTT ON | ignored | 1 byte (ACK) |
| `09` | Set Repeater Offset Direction | `09`=minus / `49`=plus / `89`=simplex | 1 byte (ACK) |
| `0A` | Set CTCSS/DCS Mode | `0A`=DCS / `2A`=CTCSS / `4A`=Enc only / `8A`=Off | 1 byte (ACK) |
| `0B` | Set CTCSS Tone Frequency | BCD: 100Hz/10Hz \| 1Hz/0.1Hz | 1 byte (ACK) |
| `0C` | Set DCS Code | DCS MSB, DCS LSB | 1 byte (ACK) |
| `0F` | Power ON (rear DC only) | ignored | 1 byte (ACK) |
| `80` | Lock OFF | ignored | 1 byte (ACK) |
| `81` | Toggle VFO (A↔B) | ignored | 1 byte (ACK) |
| `82` | Split OFF | ignored | 1 byte (ACK) |
| `85` | Clarifier (RIT) OFF | ignored | 1 byte (ACK) |
| `88` | PTT OFF | ignored | 1 byte (ACK) |
| `8F` | Power OFF (rear DC only) | ignored | 1 byte (ACK) |
| `E7` | Read RX Status | ignored | **1 byte** (S-meter + flags) |
| `F5` | Read TX Status | ignored | **1 byte** (power + flags) |

#### Read Frequency Response (opcode `03`) — 5 bytes:
```
Byte 1: BCD 100MHz / 10MHz
Byte 2: BCD 1MHz / 100kHz
Byte 3: BCD 10kHz / 1kHz
Byte 4: BCD 100Hz / 10Hz
Byte 5: Mode byte (see table below)
```

#### Mode Byte Values:
| Value | Mode |
|---|---|
| `00` | LSB |
| `01` | USB |
| `02` | CW |
| `03` | CW-R |
| `04` | AM |
| `06` | WFM |
| `08` | FM |
| `0A` | DIG (PKT/RTTY/PSK) |
| `0C` | PKT |

#### RX Status Byte (opcode `E7`):
- Bits 3–0: S-meter reading (`0x00`=S0, `0x09`=S9, `0x0A`=S9+10, up to `0x0F`)
- Bit 7: `1` = squelch open, `0` = squelched
- Bit 6: `1` = CTCSS/DCS matched
- Bit 5: `1` = discriminator centred (FM)

#### TX Status Byte (opcode `F5`):
- Bits 3–0: TX power output (0–15, linear scale)
- Bit 7: `1` = PTT keyed
- Bit 6: `1` = SWR high (reflected power alert)
- Bit 5: `1` = split active

### 2.4 Undocumented / Advanced Commands

The following are research-confirmed undocumented commands (use with **extreme caution**; incorrect EEPROM writes may require factory recalibration by Yaesu):

| Opcode (HEX) | Description |
|---|---|
| `10` | Read PTT keyed state (returns `00`=RX, `F0`=TX) |
| `A7` | Dump radio configuration string |
| `BB` | Read EEPROM (2 addr bytes → 2 data bytes returned) |
| `BC` | Write EEPROM (addr + data bytes) — **dangerous** |

> The app **must** gate EEPROM write commands behind a prominent safety warning and require explicit user confirmation.

---

## 3. Functional Requirements

### 3.1 Bluetooth Connection Management

- **REQ-BT-01:** Discover and pair with CAT-to-Bluetooth adapters advertising the SPP UUID (`00001101-0000-1000-8000-00805F9B34FB`).
- **REQ-BT-02:** Support manual entry of a Bluetooth device MAC address or selection from a paired-device list.
- **REQ-BT-03:** Persist the last connected device; auto-reconnect on app launch (with user setting to disable).
- **REQ-BT-04:** Display connection status (disconnected / connecting / connected / error) prominently in the UI.
- **REQ-BT-05:** Handle reconnection gracefully after Bluetooth drops without crashing or corrupting radio state.
- **REQ-BT-06:** Support configurable baud rate selection: **4800 / 9600 / 38400 bps** to match radio Menu Item #14.
- **REQ-BT-07:** Use Android `BluetoothAdapter` APIs; target `BLUETOOTH_CONNECT` and `BLUETOOTH_SCAN` permissions (Android 12+ permission model).

### 3.2 Frequency & VFO Control

- **REQ-VFO-01:** Display and allow editing of the current VFO frequency (1 Hz resolution).
- **REQ-VFO-02:** Poll frequency and mode via opcode `03` at a configurable interval (default: 500 ms).
- **REQ-VFO-03:** Allow direct frequency entry via a numeric keypad.
- **REQ-VFO-04:** Provide tuning knob / up-down buttons with configurable step sizes: 1 Hz, 10 Hz, 100 Hz, 1 kHz, 5 kHz, 9 kHz, 10 kHz, 12.5 kHz, 25 kHz, 100 kHz, 1 MHz.
- **REQ-VFO-05:** Toggle between VFO-A and VFO-B (opcode `81`).
- **REQ-VFO-06:** Support Split operation: enable/disable (opcodes `02`/`82`); indicate split state (read from TX status byte).
- **REQ-VFO-07:** Support Clarifier / RIT: enable (opcode `05`) and disable (opcode `85`).

### 3.3 Mode Control

- **REQ-MODE-01:** Allow selection of all supported modes: LSB, USB, CW, CW-R, AM, FM, WFM, DIG/PKT.
- **REQ-MODE-02:** Reflect the current mode read from opcode `03` response byte 5.
- **REQ-MODE-03:** Warn the user when setting a mode not appropriate for the current band.

### 3.4 PTT Control

- **REQ-PTT-01:** Provide a prominent PTT button that sends opcode `08` (PTT ON) on press and `88` (PTT OFF) on release.
- **REQ-PTT-02:** Implement a PTT safety timeout: automatically send PTT OFF if no PTT OFF command has been sent within a configurable period (default: 3 minutes).
- **REQ-PTT-03:** Display TX/RX state derived from TX status byte (opcode `F5`).
- **REQ-PTT-04:** Confirm via undocumented opcode `10` that the radio is actually keyed after PTT ON (optional diagnostic).
- **REQ-PTT-05:** Prevent PTT ON if Bluetooth is not confirmed connected.

### 3.5 S-Meter & Signal Display

- **REQ-SMETER-01:** Poll RX status via opcode `E7` and display an S-meter (S0–S9, +10, +20 dBm over S9).
- **REQ-SMETER-02:** Display squelch open/closed state.
- **REQ-SMETER-03:** Display TX power level from TX status byte.
- **REQ-SMETER-04:** Display SWR alert flag from TX status byte.
- **REQ-SMETER-05:** Show discriminator centering indicator for FM modes.

### 3.6 Tone & Subtone Control

- **REQ-TONE-01:** Select CTCSS/DCS mode (opcode `0A`).
- **REQ-TONE-02:** Set CTCSS tone frequency from standard tone table (67.0–254.1 Hz) via opcode `0B`.
- **REQ-TONE-03:** Set DCS code (opcode `0C`) from standard 104-code list.

### 3.7 Repeater Control

- **REQ-RPT-01:** Set repeater offset direction: plus / minus / simplex (opcode `09`).
- **REQ-RPT-02:** Provide a repeater directory browser (data sourced from a bundled or downloadable database, e.g. Repeaterbook API) with one-tap frequency/mode/tone programming.
- **REQ-RPT-03:** Support storage of user-defined repeater presets locally.

### 3.8 Memory Channels

- **REQ-MEM-01:** Read memory channel data from the radio EEPROM via opcode `BB` (200 regular channels + M-PL, M-PU).
- **REQ-MEM-02:** Display channel list with frequency, mode, tone, and label.
- **REQ-MEM-03:** Allow write-back of edited memory channel via opcode `BC`, gated by a safety confirmation dialog.
- **REQ-MEM-04:** Export/import memory channel list as CSV and CHIRP-compatible format.
- **REQ-MEM-05:** Display current QMB (Quick Memory Bank) and home memory frequencies if readable.

> **Note:** There is no documented CAT command to _select_ a memory channel; direct selection requires EEPROM write. The app must document this limitation clearly.

### 3.9 Panel Lock

- **REQ-LOCK-01:** Engage panel lock (opcode `00`) and release (opcode `80`) from the app.
- **REQ-LOCK-02:** Display current lock state.

### 3.10 Band Stack & Frequency Presets

- **REQ-PRESET-01:** Provide a band selector covering all FT-818ND HF/VHF/UHF amateur bands: 160 m, 80 m, 40 m, 30 m, 20 m, 17 m, 15 m, 12 m, 10 m, 6 m, 2 m, 70 cm, plus AM/FM broadcast, Air band, WFM.
- **REQ-PRESET-02:** Store and recall user-defined frequency/mode/tone presets per band.
- **REQ-PRESET-03:** Restore last-used frequency per band when switching (mirror the radio's internal band-stack VFO behavior).

### 3.11 Satellite Operation Support

- **REQ-SAT-01:** Dual-VFO display showing uplink (VFO-B) and downlink (VFO-A) frequencies simultaneously.
- **REQ-SAT-02:** Real-time Doppler shift compensation: integrate with a satellite tracking library (e.g. predict4java / JSOW) using GPS location, automatically adjusting TX and RX frequencies as the satellite passes.
- **REQ-SAT-03:** Load satellite TLE data from Celestrak or similar source; support offline-cached TLEs.
- **REQ-SAT-04:** Display AOS/LOS times, elevation, azimuth for tracked passes.
- **REQ-SAT-05:** Support linear transponder satellites (SSB/CW uplink/downlink) and FM birds.

### 3.12 Digital Modes Assistance

The Bluetooth adapter carries only CAT (control) data. Audio for digital modes must be routed separately (phone headset jack or Bluetooth audio to radio ACC port). The app shall:

- **REQ-DIG-01:** Set the radio to DIG/PKT mode and configure correct USB/LSB sub-mode for FT8, PSK31, RTTY, etc.
- **REQ-DIG-02:** Provide pre-configured frequency presets for common digital mode watering holes (FT8: 14.074 MHz, 7.074 MHz, etc.).
- **REQ-DIG-03:** Handle PTT assertion over CAT when external audio is being used by a companion digital mode app (expose an inter-app Intent interface for PTT control).
- **REQ-DIG-04:** Document clearly that audio path is not handled by this app and link to compatible companion apps (e.g. FT8CN for Android).

### 3.13 Logging & Operating Assistance

- **REQ-LOG-01:** Automatically log QSO data (timestamp UTC, frequency, mode, callsign, signal report, notes) with on-tap entry.
- **REQ-LOG-02:** Export log in ADIF format.
- **REQ-LOG-03:** Optionally upload log to Logbook of the World (LoTW) or QRZ.com via their APIs.
- **REQ-LOG-04:** Display a DX cluster spot list (Telnet to a user-configured DX cluster server); tap a spot to QSY directly.
- **REQ-LOG-05:** Show grey-line map and band condition indicators (solar flux, A/K indices from NOAA Space Weather) to assist band selection.

### 3.14 Power Management (Rear-DC Power Only)

- **REQ-PWR-01:** Support radio power-on (opcode `0F`) and power-off (opcode `8F`) when the radio is powered via the rear DC connector and was switched off via CAT.
- **REQ-PWR-02:** Warn the user that these commands only function under rear-DC power conditions.

---

## 4. Non-Functional Requirements

### 4.1 Platform

- **REQ-SYS-01:** Minimum Android version: **Android 12 (API level 31)**.
- **REQ-SYS-02:** Target SDK: Android 15 (API 35) or latest stable at build time.
- **REQ-SYS-03:** Support ARM64 and x86_64 ABIs.
- **REQ-SYS-04:** Bluetooth Classic (BR/EDR) required. BLE alone is insufficient.

### 4.2 Performance

- **REQ-PERF-01:** Frequency/mode polling round-trip (send command → receive 5 bytes → update UI) must complete in under **300 ms** under normal BT conditions.
- **REQ-PERF-02:** PTT ON command must be sent within **50 ms** of user button press.
- **REQ-PERF-03:** All 5 CAT bytes must be written to the serial stream in a single call to avoid inter-byte gaps exceeding the 200 ms radio timeout.
- **REQ-PERF-04:** App must not cause Android ANR; all CAT I/O on a dedicated background thread/coroutine.

### 4.3 Reliability

- **REQ-REL-01:** Implement a command queue with configurable retry on timeout (default: 2 retries, 500 ms timeout).
- **REQ-REL-02:** On 3 consecutive command failures, auto-close the BT socket and alert the user.
- **REQ-REL-03:** PTT state must always be restored to OFF on BT disconnect or app background, regardless of app state.
- **REQ-REL-04:** The app must survive Android lifecycle events (screen off, app backgrounded) without dropping PTT unexpectedly.

### 4.4 Safety

- **REQ-SAFE-01:** EEPROM write commands (opcode `BC`) must be hidden behind a developer/expert mode toggle, disabled by default.
- **REQ-SAFE-02:** Before any EEPROM write, the app must prompt the user to confirm they have backed up EEPROM contents and accept responsibility for potential miscalibration.
- **REQ-SAFE-03:** PTT safety timeout (REQ-PTT-02) is always active and non-disableable.

### 4.5 UX

- **REQ-UX-01:** Support portrait and landscape layouts; landscape optimised for tablet use in the field.
- **REQ-UX-02:** Dark theme by default (night/field use); light theme option.
- **REQ-UX-03:** Frequency display legible at arm's length; minimum digit height 24 sp.
- **REQ-UX-04:** All primary controls (PTT, frequency, mode) reachable with one hand on a 6" phone.
- **REQ-UX-05:** Haptic feedback on PTT press/release.

### 4.6 Permissions (Android 12+)

```xml
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
<uses-permission android:name="android.permission.BLUETOOTH_SCAN" />
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" /> <!-- satellite tracking GPS -->
<uses-permission android:name="android.permission.INTERNET" />              <!-- DX cluster, LoTW, TLEs -->
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />    <!-- keep BT alive in background -->
<uses-permission android:name="android.permission.VIBRATE" />
```

---

## 5. Architecture Notes

### 5.1 Bluetooth Serial Layer

Use Android `BluetoothSocket` with UUID `00001101-0000-1000-8000-00805F9B34FB`. The socket's `OutputStream` and `InputStream` provide the raw byte channel. Wrap in a `CatPort` service with:

- **Write queue**: serialises all outgoing 5-byte frames; never write partial frames.
- **Response dispatcher**: reads the expected 1 or 5 bytes per command and dispatches to awaiting coroutine/callback.
- **Timeout watchdog**: cancels awaiting command and signals error if no response within 500 ms.

### 5.2 CAT Protocol State Machine

Maintain in-memory radio state (frequency, mode, PTT, split, squelch, etc.) updated by polling responses. The UI reads from this state object; individual commands update it optimistically and reconcile on next poll.

### 5.3 Satellite Tracking

Use **predict4java** (Java port of PREDICT) for Doppler calculations. GPS fixes via `FusedLocationProviderClient`. Doppler correction loop should run at 1 Hz minimum during a pass.

### 5.4 Foreground Service

Run the BT connection in a `ForegroundService` with a persistent notification showing frequency and connection status. This prevents Android from killing the connection when the app is backgrounded and is required for PTT reliability.

---

## 6. Known Protocol Limitations

| Limitation | Impact |
|---|---|
| Cannot read which VFO (A or B) is currently active via documented commands | App must track VFO state internally; may desync after radio power cycle |
| Cannot read IF Shift (PBT) setting | IF Shift not controllable via app |
| Cannot read AGC, attenuator, IPO, or pre-amp state via documented commands | These controls require undocumented EEPROM read (`BB`) |
| Cannot select a specific memory channel via CAT | Memory recall limited to sequential channel stepping; app cannot jump directly to channel N |
| Clone mode baud rate is fixed at 9600 regardless of Menu Item #14 | Not relevant for CAT operation; informational only |
| No readback of clarifier (RIT) offset value | Display will show on/off state only, not offset frequency |
| EEPROM writes take effect immediately on many registers | No "pending" state; writes are live — caution required |

---

## 7. Out of Scope

- Audio routing, VOX control, or digital mode decoding (audio path not available over CAT Bluetooth adapter)
- WSJT-X / JTDX direct integration (these run on PC; app may expose CAT-over-network forwarding as a future feature)
- Firmware update / radio clone (CHIRP-style full memory clone) — feasible via EEPROM commands but out of scope for v1.0
- FT-817ND-specific hardware differences (v1.0 targets FT-818ND only)

---

## 8. References

- Yaesu FT-818ND Operating Manual, Section "CAT System" (pp. 66–69)
- KA7OEI FT-817 CAT Interface Documentation: http://www.ka7oei.com/ft817_meow.html
- hamlib FT-817 backend (`ft817.c`) — open source CAT implementation
- `ft817_cat_python` by 4X1MD — Python reference implementation
- Android Bluetooth SPP guide: https://developer.android.com/guide/topics/connectivity/bluetooth/transfer-data
- predict4java satellite tracking library: https://github.com/g4dpz/predict4java
- FT8CN Android app — reference for FT8 + CAT integration on Android

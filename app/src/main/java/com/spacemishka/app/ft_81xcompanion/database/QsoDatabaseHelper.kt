package com.spacemishka.app.ft_81xcompanion.database

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class Qso(
    val id: Long = 0,
    val timestamp: String, // ISO-8601 UTC format
    val frequencyHz: Long,
    val mode: String,
    val callsign: String,
    val rstSent: String,
    val rstRcvd: String,
    val powerWatts: Int,
    val notes: String
)

data class RepeaterPreset(
    val id: Long = 0,
    val name: String,
    val frequencyHz: Long,
    val mode: String,
    val toneMode: String,
    val ctcssFreq: Double,
    val dcsCode: Int,
    val offsetDir: String
)

class QsoDatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "ft818_companion.db"
        private const val DATABASE_VERSION = 1

        // QSO Table
        private const val TABLE_QSO = "qsos"
        private const val COL_QSO_ID = "id"
        private const val COL_QSO_TIMESTAMP = "timestamp"
        private const val COL_QSO_FREQ = "frequency_hz"
        private const val COL_QSO_MODE = "mode"
        private const val COL_QSO_CALLSIGN = "callsign"
        private const val COL_QSO_RST_SENT = "rst_sent"
        private const val COL_QSO_RST_RCVD = "rst_rcvd"
        private const val COL_QSO_POWER = "power"
        private const val COL_QSO_NOTES = "notes"

        // Repeater Table
        private const val TABLE_REPEATER = "repeaters"
        private const val COL_RPT_ID = "id"
        private const val COL_RPT_NAME = "name"
        private const val COL_RPT_FREQ = "frequency_hz"
        private const val COL_RPT_MODE = "mode"
        private const val COL_RPT_TONE_MODE = "tone_mode"
        private const val COL_RPT_CTCSS = "ctcss_freq"
        private const val COL_RPT_DCS = "dcs_code"
        private const val COL_RPT_OFFSET_DIR = "offset_dir"
    }

    override fun onCreate(db: SQLiteDatabase) {
        val createQsoTable = """
            CREATE TABLE $TABLE_QSO (
                $COL_QSO_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_QSO_TIMESTAMP TEXT NOT NULL,
                $COL_QSO_FREQ INTEGER NOT NULL,
                $COL_QSO_MODE TEXT NOT NULL,
                $COL_QSO_CALLSIGN TEXT NOT NULL,
                $COL_QSO_RST_SENT TEXT,
                $COL_QSO_RST_RCVD TEXT,
                $COL_QSO_POWER INTEGER,
                $COL_QSO_NOTES TEXT
            )
        """.trimIndent()

        val createRepeaterTable = """
            CREATE TABLE $TABLE_REPEATER (
                $COL_RPT_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COL_RPT_NAME TEXT NOT NULL,
                $COL_RPT_FREQ INTEGER NOT NULL,
                $COL_RPT_MODE TEXT NOT NULL,
                $COL_RPT_TONE_MODE TEXT NOT NULL,
                $COL_RPT_CTCSS REAL,
                $COL_RPT_DCS INTEGER,
                $COL_RPT_OFFSET_DIR TEXT NOT NULL
            )
        """.trimIndent()

        db.execSQL(createQsoTable)
        db.execSQL(createRepeaterTable)
        
        // Seed default repeater presets for offline use
        seedDefaultRepeaters(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS $TABLE_QSO")
        db.execSQL("DROP TABLE IF EXISTS $TABLE_REPEATER")
        onCreate(db)
    }

    // Seed Data
    
    private fun seedDefaultRepeaters(db: SQLiteDatabase) {
        val defaults = listOf(
            ContentValues().apply {
                put(COL_RPT_NAME, "2m National Calling")
                put(COL_RPT_FREQ, 146520000L)
                put(COL_RPT_MODE, "FM")
                put(COL_RPT_TONE_MODE, "OFF")
                put(COL_RPT_CTCSS, 88.5)
                put(COL_RPT_DCS, 23)
                put(COL_RPT_OFFSET_DIR, "SIMPLEX")
            },
            ContentValues().apply {
                put(COL_RPT_NAME, "70cm National Calling")
                put(COL_RPT_FREQ, 446000000L)
                put(COL_RPT_MODE, "FM")
                put(COL_RPT_TONE_MODE, "OFF")
                put(COL_RPT_CTCSS, 88.5)
                put(COL_RPT_DCS, 23)
                put(COL_RPT_OFFSET_DIR, "SIMPLEX")
            },
            ContentValues().apply {
                put(COL_RPT_NAME, "Mt. Diablo Repeater")
                put(COL_RPT_FREQ, 147060000L)
                put(COL_RPT_MODE, "FM")
                put(COL_RPT_TONE_MODE, "CTCSS")
                put(COL_RPT_CTCSS, 100.0)
                put(COL_RPT_DCS, 23)
                put(COL_RPT_OFFSET_DIR, "PLUS")
            }
        )
        for (cv in defaults) {
            db.insert(TABLE_REPEATER, null, cv)
        }
    }

    // QSO CRUD

    fun insertQso(qso: Qso): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_QSO_TIMESTAMP, qso.timestamp)
            put(COL_QSO_FREQ, qso.frequencyHz)
            put(COL_QSO_MODE, qso.mode)
            put(COL_QSO_CALLSIGN, qso.callsign.uppercase(Locale.US).trim())
            put(COL_QSO_RST_SENT, qso.rstSent)
            put(COL_QSO_RST_RCVD, qso.rstRcvd)
            put(COL_QSO_POWER, qso.powerWatts)
            put(COL_QSO_NOTES, qso.notes)
        }
        return db.insert(TABLE_QSO, null, cv)
    }

    fun getAllQsos(): List<Qso> {
        val list = mutableListOf<Qso>()
        val db = readableDatabase
        val cursor = db.query(
            TABLE_QSO, null, null, null, null, null,
            "$COL_QSO_TIMESTAMP DESC"
        )
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_QSO_ID)
            val timeIdx = c.getColumnIndexOrThrow(COL_QSO_TIMESTAMP)
            val freqIdx = c.getColumnIndexOrThrow(COL_QSO_FREQ)
            val modeIdx = c.getColumnIndexOrThrow(COL_QSO_MODE)
            val callIdx = c.getColumnIndexOrThrow(COL_QSO_CALLSIGN)
            val rstSIdx = c.getColumnIndexOrThrow(COL_QSO_RST_SENT)
            val rstRIdx = c.getColumnIndexOrThrow(COL_QSO_RST_RCVD)
            val pwrIdx = c.getColumnIndexOrThrow(COL_QSO_POWER)
            val notesIdx = c.getColumnIndexOrThrow(COL_QSO_NOTES)

            while (c.moveToNext()) {
                list.add(
                    Qso(
                        id = c.getLong(idIdx),
                        timestamp = c.getString(timeIdx),
                        frequencyHz = c.getLong(freqIdx),
                        mode = c.getString(modeIdx),
                        callsign = c.getString(callIdx),
                        rstSent = c.getString(rstSIdx),
                        rstRcvd = c.getString(rstRIdx),
                        powerWatts = c.getInt(pwrIdx),
                        notes = c.getString(notesIdx)
                    )
                )
            }
        }
        return list
    }

    fun deleteQso(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_QSO, "$COL_QSO_ID = ?", arrayOf(id.toString()))
    }

    // Repeater CRUD

    fun insertRepeater(rpt: RepeaterPreset): Long {
        val db = writableDatabase
        val cv = ContentValues().apply {
            put(COL_RPT_NAME, rpt.name)
            put(COL_RPT_FREQ, rpt.frequencyHz)
            put(COL_RPT_MODE, rpt.mode)
            put(COL_RPT_TONE_MODE, rpt.toneMode)
            put(COL_RPT_CTCSS, rpt.ctcssFreq)
            put(COL_RPT_DCS, rpt.dcsCode)
            put(COL_RPT_OFFSET_DIR, rpt.offsetDir)
        }
        return db.insert(TABLE_REPEATER, null, cv)
    }

    fun getAllRepeaters(): List<RepeaterPreset> {
        val list = mutableListOf<RepeaterPreset>()
        val db = readableDatabase
        val cursor = db.query(TABLE_REPEATER, null, null, null, null, null, "$COL_RPT_NAME ASC")
        cursor.use { c ->
            val idIdx = c.getColumnIndexOrThrow(COL_RPT_ID)
            val nameIdx = c.getColumnIndexOrThrow(COL_RPT_NAME)
            val freqIdx = c.getColumnIndexOrThrow(COL_RPT_FREQ)
            val modeIdx = c.getColumnIndexOrThrow(COL_RPT_MODE)
            val toneModeIdx = c.getColumnIndexOrThrow(COL_RPT_TONE_MODE)
            val ctcssIdx = c.getColumnIndexOrThrow(COL_RPT_CTCSS)
            val dcsIdx = c.getColumnIndexOrThrow(COL_RPT_DCS)
            val dirIdx = c.getColumnIndexOrThrow(COL_RPT_OFFSET_DIR)

            while (c.moveToNext()) {
                list.add(
                    RepeaterPreset(
                        id = c.getLong(idIdx),
                        name = c.getString(nameIdx),
                        frequencyHz = c.getLong(freqIdx),
                        mode = c.getString(modeIdx),
                        toneMode = c.getString(toneModeIdx),
                        ctcssFreq = c.getDouble(ctcssIdx),
                        dcsCode = c.getInt(dcsIdx),
                        offsetDir = c.getString(dirIdx)
                    )
                )
            }
        }
        return list
    }

    fun deleteRepeater(id: Long): Int {
        val db = writableDatabase
        return db.delete(TABLE_REPEATER, "$COL_RPT_ID = ?", arrayOf(id.toString()))
    }

    // ADIF Export

    fun exportToAdif(qsos: List<Qso>): String {
        val sb = StringBuilder()
        
        // ADIF Header
        val format = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
        format.timeZone = TimeZone.getTimeZone("UTC")
        val dateStr = format.format(Date())
        
        sb.append("ADIF Export from FT-81x Companion\n")
        sb.append("Created on $dateStr UTC\n")
        sb.append("<ADIF_VER:5>3.1.4\n")
        sb.append("<PROGRAMID:17>FT-81x Companion\n")
        sb.append("<EOH>\n\n")

        // Parse ISO timestamps to ADIF dates & times
        // ISO format is: yyyy-MM-dd'T'HH:mm:ss'Z'
        val isoParser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val adifDateFormatter = SimpleDateFormat("yyyyMMdd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val adifTimeFormatter = SimpleDateFormat("HHmmss", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }

        for (qso in qsos) {
            var qsoDate = ""
            var qsoTime = ""
            try {
                val parsedDate = isoParser.parse(qso.timestamp)
                if (parsedDate != null) {
                    qsoDate = adifDateFormatter.format(parsedDate)
                    qsoTime = adifTimeFormatter.format(parsedDate)
                }
            } catch (e: Exception) {
                // Fallback to current date/time if parse fails
                val now = Date()
                qsoDate = adifDateFormatter.format(now)
                qsoTime = adifTimeFormatter.format(now)
            }

            val call = qso.callsign.uppercase(Locale.US).trim()
            val mode = qso.mode.uppercase(Locale.US).trim()
            
            // Convert frequency in Hz to MHz string (e.g. 14.250000)
            val freqMhz = qso.frequencyHz / 1_000_000.0
            val freqStr = String.format(Locale.US, "%.6f", freqMhz)
            val band = frequencyToBand(qso.frequencyHz)

            sb.append("<CALL:${call.length}>$call ")
            sb.append("<QSO_DATE:8>$qsoDate ")
            sb.append("<TIME_ON:6>$qsoTime ")
            sb.append("<FREQ:${freqStr.length}>$freqStr ")
            if (band.isNotEmpty()) {
                sb.append("<BAND:${band.length}>$band ")
            }
            sb.append("<MODE:${mode.length}>$mode ")
            sb.append("<RST_SENT:${qso.rstSent.length}>${qso.rstSent} ")
            sb.append("<RST_RCVD:${qso.rstRcvd.length}>${qso.rstRcvd} ")
            if (qso.powerWatts > 0) {
                val pwrStr = qso.powerWatts.toString()
                sb.append("<TX_PWR:${pwrStr.length}>$pwrStr ")
            }
            if (qso.notes.isNotEmpty()) {
                sb.append("<COMMENT:${qso.notes.length}>${qso.notes} ")
            }
            sb.append("<EOR>\n")
        }
        return sb.toString()
    }

    private fun frequencyToBand(freqHz: Long): String {
        return when (freqHz) {
            in 1800000L..2000000L -> "160M"
            in 3500000L..4000000L -> "80M"
            in 5330000L..5405000L -> "60M"
            in 7000000L..7300000L -> "40M"
            in 10100000L..10150000L -> "30M"
            in 14000000L..14350000L -> "20M"
            in 18068000L..18168000L -> "17M"
            in 21000000L..21450000L -> "15M"
            in 24890000L..24990000L -> "12M"
            in 28000000L..29700000L -> "10M"
            in 50000000L..54000000L -> "6M"
            in 144000000L..148000000L -> "2M"
            in 420000000L..450000000L -> "70CM"
            else -> ""
        }
    }
}

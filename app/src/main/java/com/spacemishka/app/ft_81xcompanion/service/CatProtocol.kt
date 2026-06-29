package com.spacemishka.app.ft_81xcompanion.service

import java.util.Locale

object CatProtocol {

    // Opcodes
    const val OP_LOCK_ON: Byte = 0x00
    const val OP_SET_FREQ: Byte = 0x01
    const val OP_SPLIT_ON: Byte = 0x02
    const val OP_READ_FREQ_MODE: Byte = 0x03
    const val OP_RIT_ON: Byte = 0x05
    const val OP_SET_MODE: Byte = 0x07
    const val OP_PTT_ON: Byte = 0x08
    const val OP_SET_RPT_DIR: Byte = 0x09
    const val OP_SET_TONE_MODE: Byte = 0x0A
    const val OP_SET_CTCSS_FREQ: Byte = 0x0B
    const val OP_SET_DCS_CODE: Byte = 0x0C
    const val OP_POWER_ON: Byte = 0x0F
    const val OP_LOCK_OFF: Byte = 0x80.toByte()
    const val OP_TOGGLE_VFO: Byte = 0x81.toByte()
    const val OP_SPLIT_OFF: Byte = 0x82.toByte()
    const val OP_RIT_OFF: Byte = 0x85.toByte()
    const val OP_PTT_OFF: Byte = 0x88.toByte()
    const val OP_POWER_OFF: Byte = 0x8F.toByte()
    const val OP_READ_RX_STATUS: Byte = 0xE7.toByte()
    const val OP_READ_TX_STATUS: Byte = 0xF5.toByte()

    // Undocumented Opcodes
    const val OP_READ_PTT_STATE: Byte = 0x10

    // Mode Constants
    const val MODE_LSB: Byte = 0x00
    const val MODE_USB: Byte = 0x01
    const val MODE_CW: Byte = 0x02
    const val MODE_CW_R: Byte = 0x03
    const val MODE_AM: Byte = 0x04
    const val MODE_WFM: Byte = 0x06
    const val MODE_FM: Byte = 0x08
    const val MODE_DIG: Byte = 0x0A
    const val MODE_PKT: Byte = 0x0C

    // Repeater Offset Directions
    const val RPT_DIR_MINUS: Byte = 0x09
    const val RPT_DIR_PLUS: Byte = 0x49
    const val RPT_DIR_SIMPLEX: Byte = 0x89.toByte()

    // Tone Modes
    const val TONE_MODE_DCS: Byte = 0x0A
    const val TONE_MODE_CTCSS: Byte = 0x2A
    const val TONE_MODE_ENC: Byte = 0x4A
    const val TONE_MODE_OFF: Byte = 0x8A.toByte()

    /**
     * Converts a frequency in Hz to a 4-byte BCD array.
     * FT-818ND frequency BCD covers 100MHz down to 10Hz in 8 digits.
     */
    fun frequencyToBcd(frequencyHz: Long): ByteArray {
        val div10 = frequencyHz / 10
        val bcdString = String.format(Locale.US, "%08d", div10)
        val bcd = ByteArray(4)
        for (i in 0..3) {
            val highDigit = bcdString[i * 2].digitToInt()
            val lowDigit = bcdString[i * 2 + 1].digitToInt()
            bcd[i] = ((highDigit shl 4) or lowDigit).toByte()
        }
        return bcd
    }

    /**
     * Converts a 4-byte BCD array back to frequency in Hz.
     */
    fun bcdToFrequency(bcd: ByteArray): Long {
        if (bcd.size < 4) return 0L
        val sb = StringBuilder()
        for (i in 0..3) {
            val byteVal = bcd[i].toInt() and 0xFF
            sb.append(String.format(Locale.US, "%02X", byteVal))
        }
        return try {
            sb.toString().toLong() * 10L
        } catch (e: NumberFormatException) {
            0L
        }
    }

    /**
     * Converts a CTCSS tone frequency in Hz (e.g. 88.5) to a 2-byte BCD array.
     */
    fun toneToBcd(toneHz: Double): ByteArray {
        val intVal = (toneHz * 10).toInt()
        val bcdString = String.format(Locale.US, "%04d", intVal)
        val bcd = ByteArray(2)
        for (i in 0..1) {
            val highDigit = bcdString[i * 2].digitToInt()
            val lowDigit = bcdString[i * 2 + 1].digitToInt()
            bcd[i] = ((highDigit shl 4) or lowDigit).toByte()
        }
        return bcd
    }

    /**
     * Converts a DCS code (e.g. 23) to a 2-byte BCD array.
     */
    fun dcsToBcd(dcsCode: Int): ByteArray {
        val bcdString = String.format(Locale.US, "%04d", dcsCode)
        val bcd = ByteArray(2)
        for (i in 0..1) {
            val highDigit = bcdString[i * 2].digitToInt()
            val lowDigit = bcdString[i * 2 + 1].digitToInt()
            bcd[i] = ((highDigit shl 4) or lowDigit).toByte()
        }
        return bcd
    }

    // Command builders

    fun buildLockOn(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_LOCK_ON)
    fun buildLockOff(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_LOCK_OFF)
    
    fun buildSetFrequency(frequencyHz: Long): ByteArray {
        val bcd = frequencyToBcd(frequencyHz)
        return byteArrayOf(bcd[0], bcd[1], bcd[2], bcd[3], OP_SET_FREQ)
    }

    fun buildToggleVfo(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_TOGGLE_VFO)
    fun buildSplitOn(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_SPLIT_ON)
    fun buildSplitOff(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_SPLIT_OFF)
    
    fun buildReadFrequencyAndMode(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_READ_FREQ_MODE)
    
    fun buildRitOn(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_RIT_ON)
    fun buildRitOff(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_RIT_OFF)
    
    fun buildSetMode(mode: Byte): ByteArray = byteArrayOf(mode, 0, 0, 0, OP_SET_MODE)
    
    fun buildPttOn(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_PTT_ON)
    fun buildPttOff(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_PTT_OFF)
    
    fun buildSetRepeaterOffsetDirection(direction: Byte): ByteArray = 
        byteArrayOf(direction, 0, 0, 0, OP_SET_RPT_DIR)
        
    fun buildSetToneMode(mode: Byte): ByteArray = 
        byteArrayOf(mode, 0, 0, 0, OP_SET_TONE_MODE)
        
    fun buildSetCtcssFreq(toneHz: Double): ByteArray {
        val bcd = toneToBcd(toneHz)
        return byteArrayOf(bcd[0], bcd[1], 0, 0, OP_SET_CTCSS_FREQ)
    }
    
    fun buildSetDcsCode(dcsCode: Int): ByteArray {
        val bcd = dcsToBcd(dcsCode)
        return byteArrayOf(bcd[0], bcd[1], 0, 0, OP_SET_DCS_CODE)
    }
    
    fun buildPowerOn(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_POWER_ON)
    fun buildPowerOff(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_POWER_OFF)
    
    fun buildReadRxStatus(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_READ_RX_STATUS)
    fun buildReadTxStatus(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_READ_TX_STATUS)
    
    fun buildReadPttState(): ByteArray = byteArrayOf(0, 0, 0, 0, OP_READ_PTT_STATE)
}

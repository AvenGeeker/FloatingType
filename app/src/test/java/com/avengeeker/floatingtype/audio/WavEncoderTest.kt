package com.avengeeker.floatingtype.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class WavEncoderTest {

    @Test
    fun headerIsCorrectFor16kMonoPcm16() {
        val pcm = ByteArray(3200) { (it % 256).toByte() } // 100 ms at 16 kHz
        val wav = WavEncoder.encode(pcm, 16000)
        assertEquals(44 + pcm.size, wav.size)

        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(wav, 0, 4, Charsets.US_ASCII))
        assertEquals(36 + pcm.size, buffer.getInt(4))
        assertEquals("WAVE", String(wav, 8, 4, Charsets.US_ASCII))
        assertEquals("fmt ", String(wav, 12, 4, Charsets.US_ASCII))
        assertEquals(16, buffer.getInt(16))
        assertEquals(1, buffer.getShort(20).toInt()) // PCM
        assertEquals(1, buffer.getShort(22).toInt()) // mono
        assertEquals(16000, buffer.getInt(24))
        assertEquals(32000, buffer.getInt(28)) // byte rate
        assertEquals(2, buffer.getShort(32).toInt()) // block align
        assertEquals(16, buffer.getShort(34).toInt()) // bits
        assertEquals("data", String(wav, 36, 4, Charsets.US_ASCII))
        assertEquals(pcm.size, buffer.getInt(40))
    }

    @Test
    fun payloadIsPreserved() {
        val pcm = byteArrayOf(1, 2, 3, 4, 5)
        val wav = WavEncoder.encode(pcm, 16000)
        for (i in pcm.indices) {
            assertEquals(pcm[i], wav[44 + i])
        }
    }

    @Test
    fun emptyPcmStillHasValidHeader() {
        val wav = WavEncoder.encode(ByteArray(0), 16000)
        assertEquals(44, wav.size)
        val buffer = ByteBuffer.wrap(wav).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(36, buffer.getInt(4))
        assertEquals(0, buffer.getInt(40))
    }
}

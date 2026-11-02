package com.visualtrace.ai.data

import com.visualtrace.ai.data.media.FrameHash
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FrameHashTest {
    @Test fun `identical grids hash equally and gradients differ`() {
        val rising = IntArray(72) { (it % 9) * 20 }
        val falling = IntArray(72) { 200 - (it % 9) * 20 }
        assertEquals(FrameHash.dHash(rising), FrameHash.dHash(rising.copyOf()))
        assertEquals(-1L, FrameHash.dHash(rising)) // every pixel brighter than its left neighbour
        assertEquals(0L, FrameHash.dHash(falling))
        assertEquals(64, FrameHash.hamming(FrameHash.dHash(rising), FrameHash.dHash(falling)))
        assertTrue(FrameHash.hamming(5L, 5L) == 0)
    }
}

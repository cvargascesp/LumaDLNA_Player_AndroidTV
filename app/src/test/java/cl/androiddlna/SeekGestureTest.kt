package cl.androiddlna

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekGestureTest {
    @Test fun shortPressMovesFiveSeconds() {
        assertEquals(15000L, SeekGesture(10000, 120000, 1).advance(0))
        assertEquals(5000L, SeekGesture(10000, 120000, -1).advance(0))
    }
    @Test fun heldPressAcceleratesAtTwoAndFiveSeconds() {
        val gesture = SeekGesture(0, 7200000, 1)
        assertEquals(5000L, gesture.advance(0))
        assertEquals(15000L, gesture.advance(1999))
        assertEquals(87000L, gesture.advance(2000))
        assertEquals(303000L, gesture.advance(5000))
    }
    @Test fun rewindStopsAtStart() {
        val gesture = SeekGesture(2000, 120000, -1)
        repeat(20) { assertEquals(0L, gesture.advance(6000)) }
    }
    @Test fun forwardStopsAtEnd() {
        val gesture = SeekGesture(119000, 120000, 1)
        repeat(20) { assertEquals(120000L, gesture.advance(6000)) }
    }
    @Test fun separateGestureStartsAtCurrentPosition() {
        SeekGesture(10000, 120000, 1).advance(6000)
        assertEquals(15000L, SeekGesture(20000, 120000, -1).advance(0))
    }
    @Test fun longFilesCapEachStepAtFiveMinutes() {
        val gesture = SeekGesture(0, 36000000, 1)
        assertEquals(300000L, gesture.advance(2000))
        assertEquals(600000L, gesture.advance(5000))
    }
    @Test fun shortFilesDoNotSlowDownWhenHoldingLonger() {
        val gesture = SeekGesture(0, 120000, 1)
        assertEquals(10000L, gesture.advance(450))
        assertEquals(20000L, gesture.advance(2000))
        assertEquals(30000L, gesture.advance(5000))
    }
    @Test fun rewindUsesTheSameDurationBasedAcceleration() {
        val gesture = SeekGesture(3600000, 7200000, -1)
        assertEquals(3528000L, gesture.advance(2000))
        assertEquals(3312000L, gesture.advance(5000))
    }
    @Test fun newPressResetsAccelerationAfterLongSeek() {
        val target = SeekGesture(0, 7200000, 1).advance(5000)
        assertEquals(target + 5000L, SeekGesture(target, 7200000, 1).advance(0))
    }
}

package cl.androiddlna

/** Destination preview, independent of key-repeat frequency and decoder buffering. */
internal class SeekGesture(position: Long, private val duration: Long, private val direction: Int) {
    var target = position.coerceIn(0, duration.coerceAtLeast(0))
        private set

    fun advance(heldMillis: Long): Long {
        val step = when {
            heldMillis >= 5000 -> (duration / 100 * 3).coerceIn(10000L, 300000L)
            heldMillis >= 2000 -> (duration / 100).coerceIn(10000L, 300000L)
            heldMillis > 0 -> 10000L
            else -> 5000L
        }
        target = (target + step * direction).coerceIn(0, duration.coerceAtLeast(0))
        return target
    }
}

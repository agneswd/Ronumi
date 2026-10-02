package dev.agneswd.stillpoint.usage

/** Counts foreground intervals inside a range. Earlier events establish the state at its start. */
internal class ForegroundTimeline(private val from: Long, private val to: Long) {
    private val totals = HashMap<String, Long>()
    private var current: String? = null
    private var startedAt = from
    private var stateKnown = false

    fun resume(pkg: String, at: Long) {
        if (at >= to) return
        if (at < from) {
            current = pkg
        } else if (current != pkg) {
            close(at)
            current = pkg
            startedAt = at
        }
        stateKnown = true
    }

    fun pause(pkg: String, at: Long) {
        if (at >= to) return
        if (at < from) {
            if (current == pkg) current = null
        } else if (current == pkg) {
            close(at)
        } else if (!stateKnown) {
            // A first pause can identify an app whose resume predates the available events.
            totals[pkg] = at - from
        }
        stateKnown = true
    }

    fun screenOff(at: Long) {
        if (at >= to) return
        if (at < from) current = null else close(at)
        stateKnown = true
    }

    fun result(): Map<String, Long> = totals.toMutableMap().apply {
        current?.let { this[it] = (this[it] ?: 0L) + (to - startedAt).coerceAtLeast(0) }
    }

    private fun close(at: Long) {
        current?.let { totals[it] = (totals[it] ?: 0L) + (at - startedAt).coerceAtLeast(0) }
        current = null
    }
}

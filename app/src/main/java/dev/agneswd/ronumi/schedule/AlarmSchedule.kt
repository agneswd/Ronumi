package dev.agneswd.ronumi.schedule

/** One alarm the app wants AlarmManager to hold. */
data class AlarmTarget(val atMillis: Long, val id: Long, val exact: Boolean)

enum class AlarmPresence { Unknown, Idle, Armed }

data class ArmedAlarm(val presence: AlarmPresence = AlarmPresence.Unknown, val target: AlarmTarget? = null)

enum class AlarmStep { Keep, Schedule, Cancel }

/**
 * Decides whether to touch an alarm.
 * An unknown arm, including a fresh process, never skips a schedule or a cancel.
 * An invalidated clock or permission also starts from unknown.
 */
fun alarmStep(current: ArmedAlarm, desired: AlarmTarget?, invalidated: Boolean): Pair<AlarmStep, ArmedAlarm> {
    val known = if (invalidated) ArmedAlarm() else current
    if (desired == null) {
        if (known.presence == AlarmPresence.Idle) return AlarmStep.Keep to known
        return AlarmStep.Cancel to ArmedAlarm(AlarmPresence.Idle, null)
    }
    if (known.presence == AlarmPresence.Armed && known.target == desired) return AlarmStep.Keep to known
    return AlarmStep.Schedule to ArmedAlarm(AlarmPresence.Armed, desired)
}

/** A fired alarm is forgotten, so the next refresh can set that target again. */
fun forgetAlarm(): ArmedAlarm = ArmedAlarm()

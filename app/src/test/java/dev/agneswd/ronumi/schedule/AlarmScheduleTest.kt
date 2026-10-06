package dev.agneswd.ronumi.schedule

import org.junit.Assert.assertEquals
import org.junit.Test

class AlarmScheduleTest {
    private val phase = AlarmTarget(atMillis = 5_000, id = 10, exact = true)

    @Test
    fun checkpointKeepsTheSameDeadline() {
        val armed = alarmStep(ArmedAlarm(), phase, invalidated = false).second
        val again = alarmStep(armed, phase, invalidated = false)
        assertEquals(AlarmStep.Keep, again.first)
    }

    @Test
    fun pauseCancelsAndResumeSchedulesTheNewDeadline() {
        val armed = alarmStep(ArmedAlarm(), phase, invalidated = false).second
        val paused = alarmStep(armed, null, invalidated = false)
        assertEquals(AlarmStep.Cancel, paused.first)
        val resumed = alarmStep(paused.second, phase.copy(atMillis = 9_000), invalidated = false)
        assertEquals(AlarmStep.Schedule, resumed.first)
    }

    @Test
    fun nextRoundWithTheSamePhaseStillSchedules() {
        val armed = alarmStep(ArmedAlarm(), phase, invalidated = false).second
        val round = alarmStep(armed, phase.copy(atMillis = phase.atMillis + 1_500_000), invalidated = false)
        assertEquals(AlarmStep.Schedule, round.first)
    }

    @Test
    fun firingForgetsTheTarget() {
        val armed = alarmStep(ArmedAlarm(), phase, invalidated = false).second
        val forgotten = forgetAlarm()
        assertEquals(AlarmPresence.Unknown, forgotten.presence)
        val again = alarmStep(forgotten, phase, invalidated = false)
        assertEquals(AlarmStep.Schedule, again.first)
        assertEquals(AlarmStep.Keep, alarmStep(armed, phase, invalidated = false).first)
    }

    @Test
    fun processStartDoesNotSkipAlarms() {
        val fresh = ArmedAlarm()
        assertEquals(AlarmStep.Schedule, alarmStep(fresh, phase, invalidated = false).first)
        assertEquals(AlarmStep.Cancel, alarmStep(fresh, null, invalidated = false).first)
    }

    @Test
    fun clockZoneOrPermissionChangeReschedules() {
        val armed = alarmStep(ArmedAlarm(), phase, invalidated = false).second
        assertEquals(AlarmStep.Schedule, alarmStep(armed, phase, invalidated = true).first)
        val idle = alarmStep(armed, null, invalidated = false).second
        assertEquals(AlarmStep.Cancel, alarmStep(idle, null, invalidated = true).first)
    }

    @Test
    fun changedPlanIdAtTheSameTimeUpdatesExtras() {
        val armed = alarmStep(ArmedAlarm(), phase, invalidated = false).second
        val moved = alarmStep(armed, phase.copy(id = 99), invalidated = false)
        assertEquals(AlarmStep.Schedule, moved.first)
        assertEquals(99L, moved.second.target?.id)
    }
}

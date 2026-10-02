package dev.agneswd.stillpoint.game

import dev.agneswd.stillpoint.data.FocusSession
import java.time.LocalDate

const val CURRENT_QUEST_VERSION = 1
const val DAILY_QUEST_TEMPLATE_COUNT = 24

/** The first saved session fixes the rules and goal for the entire day. */
fun questsFor(date: LocalDate, sessions: List<FocusSession>, goalMinutes: Int): List<Quest> {
    val first = sessions.minWithOrNull(compareBy<FocusSession> { it.startedAt }.thenBy { it.questVersion }.thenBy { it.id })
    val goal = first?.goalMinutes ?: goalMinutes
    if (first?.questVersion == 0) return legacyQuestsFor(date, sessions, goal)
    val minutes = (sessions.sumOf { it.focusedMillis } / 60_000).toInt()
    val completed = sessions.filter { it.completed }
    val longest = (completed.maxOfOrNull { it.focusedMillis } ?: 0) / 60_000
    val named = completed.count { it.tag.isNotBlank() }
    val reflected = completed.count { it.notes.isNotBlank() }
    fun quest(id: String, title: String, value: Int, target: Int, xp: Int, category: String, detail: String) =
        Quest(id, title, value.coerceAtMost(target), target, xp, category, detail)
    // Each category has its own six-day rotation. Dates alone select the tasks.
    val rotation = Math.floorMod(date.toEpochDay(), 6)
    val focusTargets = listOf(10, 15, 20, 25, 30, 40)
    val finishTargets = listOf(1, 2, 1, 2, 1, 3)
    val finishLengths = listOf(5, 0, 10, 5, 15, 0)
    val goalShares = listOf(25, 33, 50, 67, 75, 100)
    val fi = rotation
    val ci = Math.floorMod(date.toEpochDay() / 6 + rotation + 2, 6)
    val gi = Math.floorMod(date.toEpochDay() / 36 + rotation + 4, 6)
    val focusTarget = focusTargets[fi].coerceAtMost(goal.coerceAtLeast(5))
    val finishLength = finishLengths[ci]
    val finishCount = finishTargets[ci]
    val finishProgress = completed.count { it.focusedMillis >= finishLength * 60_000L }
    val goalTarget = (goal * goalShares[gi] / 100).coerceIn(5, 90)
    val care = when (rotation) {
        0 -> quest("v1_name1", "Finish a named session", named, 1, 15, "Intention", "Name a task before you start, then complete that session today. A saved session with any nonempty name counts.")
        1 -> quest("v1_note1", "Write a session reflection", reflected, 1, 15, "Intention", "Complete a session started today. Add a note on its completion screen, then select Continue to save it. Notes on abandoned sessions do not count.")
        2 -> quest("v1_steady10", "Finish 10 minutes in one session", longest.toInt(), 10, 20, "Intention", "Complete one session started today with at least 10 focus minutes. Break time does not count. Giving up does not complete this task.")
        3 -> quest("v1_name2", "Finish two named sessions", named, 2, 25, "Intention", "Complete two sessions started today with a task name. You can use the same name for both. Giving up does not count.")
        4 -> quest("v1_note2", "Reflect on two sessions", reflected, 2, 25, "Intention", "Complete two sessions started today. Add a note after each session and select Continue to save it. Each saved note counts once.")
        else -> quest("v1_steady20", "Finish 20 minutes in one session", longest.toInt(), 20, 25, "Intention", "Complete one session started today with at least 20 focus minutes. Break time does not count. Giving up does not complete this task.")
    }
    return listOf(
        quest("v1_focus$fi", "Focus for $focusTarget minutes", minutes, focusTarget, 10 + focusTarget / 2, "Focus", "Collect $focusTarget focus minutes across saved sessions started today. Sessions you end early count too. Break time does not count."),
        quest("v1_finish$ci", if (finishLength == 0) "Finish $finishCount ${if (finishCount == 1) "session" else "sessions"}" else "Finish $finishCount ${if (finishCount == 1) "session" else "sessions"} of $finishLength minutes", finishProgress, finishCount, 15 + finishCount * 5, "Finish", "Complete $finishCount ${if (finishCount == 1) "session" else "sessions"} started today.${if (finishLength > 0) " Each needs at least $finishLength focus minutes." else " Each needs at least one saved focus minute."} Let the timer finish, or select I'm done in stopwatch mode. Giving up does not count."),
        quest("v1_goal$gi", "Put $goalTarget minutes toward your goal", minutes, goalTarget, 15 + goalTarget / 3, "Your pace", "Collect $goalTarget focus minutes across sessions started today. This task uses ${goalShares[gi]}% of your $goal-minute goal, limited to 5-90 minutes. The first saved session fixes the target; later goal changes do not change it."),
        care,
    )
}

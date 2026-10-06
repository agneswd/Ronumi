package dev.agneswd.stillpoint.game

import dev.agneswd.stillpoint.ui.quantityResource
import dev.agneswd.stillpoint.ui.textResource
import dev.agneswd.stillpoint.ui.ResourceText
import dev.agneswd.stillpoint.R
import dev.agneswd.stillpoint.data.FocusSession
import java.time.LocalDate

const val CURRENT_QUEST_VERSION = 1
const val DAILY_QUEST_TEMPLATE_COUNT = 24

/** The first saved session fixes the rules and goal for the entire day. */
fun questsFor(date: LocalDate, sessions: List<FocusSession>, goalMinutes: Int): List<Quest> {
    val first = sessions.minWithOrNull(compareBy<FocusSession> { it.startedAt }.thenBy { it.questVersion }.thenBy { it.id })
    val goal = first?.goalMinutes ?: goalMinutes
    if (first?.questVersion == 0) return legacyQuestsFor(date, sessions, goal)
    val minutes = sessions.focusMinutesTotal()
    val completed = sessions.filter { it.completed }
    val longest = (completed.maxOfOrNull { it.safeFocusMillis() } ?: 0) / 60_000
    val named = completed.count { it.tag.isNotBlank() }
    val reflected = completed.count { it.notes.isNotBlank() }
    fun quest(id: String, title: ResourceText, value: Int, target: Int, xp: Int, category: String, detail: ResourceText) =
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
    val finishProgress = completed.count { it.safeFocusMillis() >= finishLength * 60_000L }
    val goalTarget = (goal * goalShares[gi] / 100).coerceIn(5, 90)
    val care = when (rotation) {
        0 -> quest("v1_name1", textResource(R.string.quest_v1_name1_title), named, 1, 15, "Intention", textResource(R.string.quest_v1_name1_detail))
        1 -> quest("v1_note1", textResource(R.string.quest_v1_note1_title), reflected, 1, 15, "Intention", textResource(R.string.quest_v1_note1_detail))
        2 -> quest("v1_steady10", textResource(R.string.quest_v1_steady10_title), longest.toInt(), 10, 20, "Intention", textResource(R.string.quest_v1_steady10_detail))
        3 -> quest("v1_name2", textResource(R.string.quest_v1_name2_title), named, 2, 25, "Intention", textResource(R.string.quest_v1_name2_detail))
        4 -> quest("v1_note2", textResource(R.string.quest_v1_note2_title), reflected, 2, 25, "Intention", textResource(R.string.quest_v1_note2_detail))
        else -> quest("v1_steady20", textResource(R.string.quest_v1_steady20_title), longest.toInt(), 20, 25, "Intention", textResource(R.string.quest_v1_steady20_detail))
    }
    return listOf(
        quest("v1_focus$fi", quantityResource(R.plurals.quest_focus_title, focusTarget, focusTarget), minutes, focusTarget, 10 + focusTarget / 2, "Focus",
            quantityResource(R.plurals.quest_focus_detail, focusTarget, focusTarget)),
        quest("v1_finish$ci",
            if (finishLength == 0) quantityResource(R.plurals.quest_finish_title, finishCount, finishCount)
            else quantityResource(R.plurals.quest_finish_length_title, finishCount, finishCount, finishLength),
            finishProgress, finishCount, 15 + finishCount * 5, "Finish",
            if (finishLength == 0) quantityResource(R.plurals.quest_finish_detail, finishCount, finishCount)
            else quantityResource(R.plurals.quest_finish_length_detail, finishCount, finishCount, finishLength)),
        quest("v1_goal$gi", quantityResource(R.plurals.quest_goal_title, goalTarget, goalTarget), minutes, goalTarget, 15 + goalTarget / 3, "Your pace",
            quantityResource(R.plurals.quest_goal_detail, goalTarget, goalTarget, goalShares[gi], goal)),
        care,
    )
}

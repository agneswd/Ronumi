package dev.agneswd.stillpoint

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import dev.agneswd.stillpoint.data.*
import dev.agneswd.stillpoint.game.gameState
import dev.agneswd.stillpoint.game.applyStreakFreezes
import dev.agneswd.stillpoint.guard.PolicyActions
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import dev.agneswd.stillpoint.game.PebbleStyles
import dev.agneswd.stillpoint.game.questsFor
import dev.agneswd.stillpoint.game.CURRENT_QUEST_VERSION
import java.io.File
import java.time.LocalDate
import java.time.ZoneId

/** Debug-only device workflow. The shell runs it; ordinary apps lack the DUMP permission. */
class StorageCheckReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.app.scope.launch {
            val result = runCatching {
                when (intent.getStringExtra("scenario")) {
                    "migration" -> checkMigration(context)
                    "storage" -> checkStorage(context)
                    "notifications-start" -> prepareNotifications(context)
                    "notifications-check" -> checkNotifications(context)
                    "demo-rewards" -> prepareDemoRewards(context)
                    "progression-workflow" -> checkProgression(context)
                    "demo-wardrobe" -> prepareDemoWardrobe(context)
                    "plan-start" -> preparePlan(context)
                    "plan-check" -> checkPlan(context)
                    else -> error("Unknown check")
                }
            }.fold({ "PASS\n$it" }, { "FAIL\n${it.stackTraceToString()}" })
            File(context.filesDir, "device-check.txt").writeText(result)
            pending.finish()
        }
    }
}

/** Seeds sample history. The demo then completes a real one-minute timer. */
private suspend fun prepareDemoRewards(context: Context): String {
    val dao = context.app.dao
    check(dao.activeFocus() == null)
    val now = System.currentTimeMillis()
    val today = LocalDate.now()
    val settings = dao.currentSettings().copy(focusMinutes = 1, timerMode = TimerMode.TIMER,
        focusStrict = false, focusLockHome = false, focusSound = FocusSound.WAVES, protection = false)
    val recent = FocusSession(1, now - 10 * 60_000, now - 60_000, 9 * 60_000, false, "Reading", goalMinutes = settings.focusGoalMinutes)
    val finish = FocusSession(3, now, now + 60_000, 60_000, true, "One small step", goalMinutes = settings.focusGoalMinutes)
    val start = today.minusDays(1).atTime(12, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val history = (1..99).firstNotNullOfOrNull { minutes ->
        val past = FocusSession(2, start, start + minutes * 60_000L, minutes * 60_000L, false, "Reading", goalMinutes = settings.focusGoalMinutes)
        val sample = listOf(past, recent)
        val before = gameState(sample, settings, today)
        val after = gameState(sample + finish, settings, today)
        sample.takeIf { after.level.number > before.level.number && after.streak > before.streak }
    } ?: error("Cannot prepare demo rewards for this date")
    dao.replaceAll(Backup(settings = settings, sessions = history, limits = emptyList(), schedules = emptyList(), sites = emptyList()))
    return "Sample history ready. Complete one real minute to show level and streak rewards."
}

private suspend fun preparePlan(context: Context): String {
    val dao = context.app.dao
    check(dao.activeFocus() == null)
    exportBackup(context, dao, Uri.fromFile(File(context.filesDir, "plan-original.json")))
    dao.updateSettings { it.copy(focusStrict = false, focusLockHome = false, focusSound = FocusSound.OFF, protection = false) }
    val now = java.time.LocalTime.now()
    val start = (now.hour * 60 + now.minute + 1) % 1440
    dao.saveSchedule(Schedule(9001, "Device alarm check", start, (start + 2) % 1440, startFocus = true, focusMinutes = 1))
    dev.agneswd.stillpoint.schedule.Plans.refresh(context)
    check(dev.agneswd.stillpoint.schedule.Plans.exactAllowed(context))
    return "One-minute focus scheduled for the next minute."
}

private suspend fun checkPlan(context: Context): String {
    val dao = context.app.dao
    try {
        check(dao.activeFocus() == null) { "Alarm did not end the timer" }
        val completed = dao.allSessions().filter { it.tag == "Device alarm check" }
        check(completed.single().completed && completed.single().focusedMillis == 60_000L)
        dev.agneswd.stillpoint.focus.Focus.advance(context)
        dev.agneswd.stillpoint.focus.Focus.giveUp(context)
        check(dao.allSessions().count { it.tag == "Device alarm check" } == 1)
        return "System alarm started focus after process death. Screen-off completion saved exactly one minute and one session."
    } finally {
        dao.clearActiveFocus()
        dev.agneswd.stillpoint.focus.Celebrations.consume()
        importBackup(context, dao, Uri.fromFile(File(context.filesDir, "plan-original.json")))
    }
}

private suspend fun prepareNotifications(context: Context): String {
    val dao = context.app.dao
    exportBackup(context, dao, Uri.fromFile(File(context.filesDir, "notifications-original.json")))
    dao.clearHeld()
    val day = LocalDate.now().toString()
    val usage = dao.usageDay(day) ?: UsageDay(day, emptyMap(), 0)
    dao.saveUsageDay(usage.copy(heldCount = 0))
    context.getSharedPreferences("delivery", Context.MODE_PRIVATE).edit().clear().apply()
    dao.updateSettings { it.copy(heldPackages = setOf("dev.agneswd.stillpoint.e2e"), holdAlways = true) }
    return "Notification listener prepared for device test messages."
}

private suspend fun checkNotifications(context: Context): String {
    val dao = context.app.dao
    try {
        check(dao.allHeld().single().title == "Updated test")
        check(dao.usageDay(LocalDate.now().toString())?.heldCount == 1)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        dev.agneswd.stillpoint.notify.Delivery.release(context)
        // Android queues notification posts. Wait for the system to publish the result.
        val first = kotlinx.coroutines.withTimeout(5_000) {
            while (manager.activeNotifications.none { it.id == 10 }) kotlinx.coroutines.delay(50)
            manager.activeNotifications.single { it.id == 10 }.postTime
        }
        kotlinx.coroutines.delay(100)
        dev.agneswd.stillpoint.notify.Delivery.release(context)
        check(manager.activeNotifications.single { it.id == 10 }.postTime == first)
        return "Real notifications persisted across process restart. Updates did not duplicate the inbox or delivery."
    } finally {
        importBackup(context, dao, Uri.fromFile(File(context.filesDir, "notifications-original.json")))
        context.getSystemService(android.app.NotificationManager::class.java).cancel(10)
    }
}

private suspend fun checkMigration(context: Context): String {
    val dao = context.app.dao
    check(dao.currentSettings().focusGoalMinutes == 90)
    check(dao.currentSettings().onboarded)
    check(dao.allLimits().single().minutesPerDay == 17)
    check(dao.allSchedules().single().name == "Preserve this plan")
    check(dao.allSites().single().domain == "example.com")
    check(dao.allSessions().single().tag == "Preserve this history")
    check(dao.allSessions().single().goalMinutes == 90)
    check(dao.allHeld().single().title == "Preserve this message")
    check(dao.activeFocus()?.tag == "Preserve this timer")
    check(dao.activeFocus()?.goalMinutes == 90)
    check(dao.currentSettings().pebbleItems.isEmpty())
    check(dao.allSessions().single().questVersion == 0)
    return "Schema 1 upgraded to schema 4. Original data and legacy quest rules were preserved."
}

/** Exercises actual file I/O, Room transactions, and policy changes in the installed app. */
private suspend fun checkStorage(context: Context): String {
    val dao = context.app.dao
    check(dao.activeFocus() == null) { "End the session before the storage check" }
    val original = File(context.filesDir, "check-original.json")
    exportBackup(context, dao, Uri.fromFile(original))
    val results = mutableListOf<String>()
    try {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val start = yesterday.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val fixture = Backup(settings = Settings(onboarded = true, focusGoalMinutes = 25),
            limits = listOf(AppLimit("com.google.android.deskclock", 1)),
            schedules = listOf(Schedule(1, "Overnight", 23 * 60, 6 * 60, days = 127)),
            sites = listOf(BlockedSite("example.com")),
            sessions = listOf(FocusSession(1, start, start + 25 * 60_000, 25 * 60_000, true, "Reading", "Local notes", 25)),
            usageDays = listOf(UsageDay(yesterday.toString(), mapOf("com.google.android.deskclock" to 12 * 60_000), 7, 2,
                limitMinutes = mapOf("com.google.android.deskclock" to 17))))
        val file = File(context.filesDir, "check-backup.json")
        file.writeText(Json.encodeToString(fixture))
        importBackup(context, dao, Uri.fromFile(file))
        check(dao.allSessions() == fixture.sessions && dao.allUsageDays().containsAll(fixture.usageDays))
        val exported = File(context.filesDir, "check-export.json")
        exportBackup(context, dao, Uri.fromFile(exported))
        val roundTrip = Json.decodeFromString<Backup>(exported.readText())
        check(roundTrip.copy(usageDays = fixture.usageDays) == fixture)
        check(roundTrip.usageDays.containsAll(fixture.usageDays))
        results += "Backup round trip preserved settings, limits, schedules, sites, notes, focus and usage history."

        file.writeText(Json.encodeToString(fixture.copy(limits = listOf(AppLimit("bad", -1)))))
        check(runCatching { importBackup(context, dao, Uri.fromFile(file)) }.isFailure)
        check(dao.allSessions() == fixture.sessions && dao.allLimits() == fixture.limits)
        file.writeText("{broken")
        check(runCatching { importBackup(context, dao, Uri.fromFile(file)) }.isFailure)
        check(dao.allSessions() == fixture.sessions && dao.allLimits() == fixture.limits)
        results += "Malformed and invalid backups left stored data unchanged."

        val xp = gameState(dao.allSessions(), dao.currentSettings(), today).xp
        dao.updateSettings { it.copy(focusGoalMinutes = 600) }
        check(gameState(dao.allSessions(), dao.currentSettings(), today).xp == xp)
        results += "Changing the current goal did not change historical XP."
        check(dev.agneswd.stillpoint.insights.limitStreak(dao.allUsageDays(), "com.google.android.deskclock", today) == 1)
        dao.saveLimit(AppLimit("com.google.android.deskclock", 120))
        check(dev.agneswd.stillpoint.insights.limitStreak(dao.allUsageDays(), "com.google.android.deskclock", today) == 1)
        results += "Changing the current app budget did not rewrite the saved limit streak."

        dao.updateSettings { it.copy(streakFreezes = 1) }
        applyStreakFreezes(dao, today.plusDays(1))
        val once = dao.currentSettings()
        applyStreakFreezes(dao, today.plusDays(1))
        check(once.streakFreezes == 0 && today.toString() in once.frozenDays)
        check(dao.currentSettings().streakFreezes == once.streakFreezes)
        results += "A missed day used one freeze. Reopening did not use another freeze."

        dao.recordHeld(HeldNotification(packageName = "test", notificationKey = "message", title = "First", text = "one", postedAt = start), today.toString())
        dao.recordHeld(HeldNotification(packageName = "test", notificationKey = "message", title = "Updated", text = "two", postedAt = start), today.toString())
        check(dao.allHeld().single().title == "Updated")
        check(dao.usageDay(today.toString())?.heldCount == 1)
        results += "Notification updates retained one inbox item and one daily count."

        check(PolicyActions.grantExtra(context, "com.google.android.deskclock"))
        check(PolicyActions.grantExtra(context, "com.google.android.deskclock"))
        check(PolicyActions.grantExtra(context, "com.google.android.deskclock"))
        check(!PolicyActions.grantExtra(context, "com.google.android.deskclock"))
        check(dao.passesUsed(today.toString()) == 3)
        dao.saveLimit(AppLimit("com.google.android.deskclock", 1, LimitMode.STRICT))
        check(!PolicyActions.grantExtra(context, "com.google.android.deskclock"))
        results += "Daily passes stopped at three. A strict limit refused a pass."

        dao.updateSettings { it.copy(protection = true) }
        dao.saveSchedule(Schedule(2, "All day", 0, 0))
        check(!PolicyActions.saveSchedule(context, Schedule(2, "All day", 0, 0, enabled = false)))
        check(dao.allSchedules().any { it.id == 2L && it.enabled })
        check(!PolicyActions.pauseBlocks(context, 10))
        file.writeText(Json.encodeToString(fixture))
        check(runCatching { importBackup(context, dao, Uri.fromFile(file)) }.isFailure)
        results += "Protected schedules refused editor changes, block pause and backup restore."
    } finally {
        dao.clearActiveFocus()
        dao.updateSettings { it.copy(protection = false) }
        importBackup(context, dao, Uri.fromFile(original))
    }
    return results.joinToString("\n")
}

/** Checks installed storage without replacing history, policies, or notification rows. */
private suspend fun checkProgression(context: Context): String {
    val dao = context.app.dao
    check(dao.activeFocus() == null) { "End the session before the progression check" }
    val original = dao.currentSettings()
    val originalSessions = dao.allSessions().sortedBy { it.id }
    var insertedId: Long? = null
    try {
        val worn = setOf("color_mint", "outfit_tee", "hat_beanie", "accessory_scarf")
        dao.updateSettings { it.copy(pebbleItems = worn) }
        check(dao.currentSettings().pebbleItems == worn) { "Room lost equipped items" }
        val date = LocalDate.of(2026, 10, 2)
        val start = date.atTime(10, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val row = FocusSession(startedAt = start, endedAt = start + 30 * 60_000,
            focusedMillis = 30 * 60_000, completed = true, tag = "Progression check", notes = "A short reflection",
            goalMinutes = 60, questVersion = CURRENT_QUEST_VERSION)
        val savedId = dao.addSession(row)
        insertedId = savedId
        val saved = dao.allSessions().single { it.id == savedId }
        check(saved == row.copy(id = savedId)) { "Room lost quest fields" }
        val fixture = Backup(settings = dao.currentSettings(), sessions = listOf(saved),
            limits = emptyList(), schedules = emptyList(), sites = emptyList())
        val encoded = Json.encodeToString(fixture)
        check(Json.decodeFromString<Backup>(encoded).validated() == fixture) { "Backup lost progression fields" }
        val tree = Json.parseToJsonElement(encoded).jsonObject
        val legacy = JsonObject(tree.toMutableMap().apply {
            put("settings", JsonObject(tree.getValue("settings").jsonObject.filterKeys { it != "pebbleItems" }))
            put("sessions", JsonArray(tree.getValue("sessions").jsonArray.map { element ->
                JsonObject(element.jsonObject.filterKeys { it != "questVersion" })
            }))
        })
        val restored = Json.decodeFromString<Backup>(legacy.toString()).validated()
        check(restored.settings.pebbleItems.isEmpty() && restored.sessions.single().questVersion == 0)
        check(questsFor(date, restored.sessions, 60).size == 3) { "Legacy quest rules changed" }
        val completed = (0..2).map { saved.copy(id = it.toLong()) }
        val tasks = questsFor(date, completed, 60)
        check(tasks.size == 4 && tasks.single { it.category == "Focus" }.done)
        check(tasks.single { it.category == "Finish" }.done)
        check(questsFor(date, completed.map { it.copy(completed = false) }, 60).none { it.category == "Finish" && it.done })
        check(questsFor(date, completed, 600) == tasks) { "Current goal changed saved quests" }
        val resolved = PebbleStyles.resolve(worn + setOf("hat_crown", "color_peach", "unknown_item"), 2)
        check(resolved == worn) { "Locked or unknown items were equipped" }
        check(PebbleStyles.resolve(setOf("color_mint", "color_peach"), 3).size == 1) { "Two items occupied one slot" }
        return "Room preserved wardrobe and quest fields. Backups retained new fields and accepted old defaults. " +
            "Completed sessions earned quests; abandoned sessions did not. Saved goals stayed stable. " +
            "Locked, unknown, and duplicate-slot items were filtered. Original data was restored."
    } finally {
        insertedId?.let { id -> dao.allSessions().find { it.id == id }?.let { dao.deleteSession(it) } }
        dao.saveSettings(original)
        check(dao.currentSettings() == original && dao.allSessions().sortedBy { it.id } == originalSessions) {
            "Progression check did not restore the original data"
        }
    }
}

/** Replaces history with sample data for an explicitly requested demo. */
private suspend fun prepareDemoWardrobe(context: Context): String {
    val dao = context.app.dao
    check(dao.activeFocus() == null)
    val today = LocalDate.now()
    val settings = dao.currentSettings().copy(onboarded = true, focusGoalMinutes = 60,
        focusMinutes = 25, focusStrict = false, focusLockHome = false, protection = false,
        pebbleItems = setOf("color_mint", "outfit_overalls", "hat_bucket", "accessory_glasses"))
    val sessions = (1..14).flatMap { daysAgo ->
        (0..1).map { index ->
            val start = today.minusDays(daysAgo.toLong()).atTime(9 + index * 5, 0)
                .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            FocusSession(id = (daysAgo * 2 + index).toLong(), startedAt = start,
                endedAt = start + 30 * 60_000, focusedMillis = 30 * 60_000, completed = true,
                tag = if (index == 0) "Reading" else "Practice", notes = "One useful step today.",
                goalMinutes = 60, questVersion = CURRENT_QUEST_VERSION)
        }
    }
    val state = gameState(sessions, settings, today)
    check(state.level.number in 5..8) { "Demo history has an unexpected level" }
    check(PebbleStyles.resolve(settings.pebbleItems, state.level.number) == settings.pebbleItems)
    dao.replaceAll(Backup(settings = settings, sessions = sessions,
        limits = emptyList(), schedules = emptyList(), sites = emptyList()))
    return "Sample wardrobe history ready at level ${state.level.number}. This fixture replaced the previous history."
}

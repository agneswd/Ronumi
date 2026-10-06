package dev.agneswd.ronumi

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.room.withTransaction
import dev.agneswd.ronumi.data.*
import dev.agneswd.ronumi.game.gameState
import dev.agneswd.ronumi.game.applyStreakFreezes
import dev.agneswd.ronumi.guard.PolicyActions
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonArray
import dev.agneswd.ronumi.game.RonumiStyles
import dev.agneswd.ronumi.game.questsFor
import dev.agneswd.ronumi.game.CURRENT_QUEST_VERSION
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
                    "focus-state" -> {
                        val focus = context.app.dao.activeFocus()
                        if (focus == null) "{}" else kotlinx.serialization.json.buildJsonObject {
                            put("startedAt", JsonPrimitive(focus.startedAt))
                            put("tag", JsonPrimitive(focus.tag))
                            put("timerMode", JsonPrimitive(focus.timerMode.name))
                            put("focusMinutes", JsonPrimitive(focus.focusMinutes))
                        }.toString()
                    }
                    "storage" -> checkStorage(context)
                    "notifications-start" -> prepareNotifications(context)
                    "notifications-check" -> checkNotifications(context)
                    "demo-rewards" -> prepareDemoRewards(context)
                    "progression-workflow" -> checkProgression(context)
                    "demo-wardrobe" -> prepareDemoWardrobe(context)
                    "plan-start" -> preparePlan(context)
                    // A plan that never runs by itself. Only an intent can start it.
                    "plan-intent" -> {
                        check(context.app.dao.activeFocus() == null)
                        context.app.dao.saveSchedule(Schedule(name = "Plan intent check", startMinute = 0, endMinute = 1, days = 0, startFocus = true, focusMinutes = 5))
                        context.app.dao.allSchedules().last { it.name == "Plan intent check" }.id.toString()
                    }
                    "plan-intent-cleanup" -> {
                        context.app.dao.allSchedules().filter { it.name == "Plan intent check" }.forEach { context.app.dao.deleteSchedule(it) }
                        "deleted"
                    }
                    "plan-notification" -> {
                        dev.agneswd.ronumi.ui.MainActivity.pendingPlan(context, intent.getLongExtra("planId", 0)).send()
                        "sent"
                    }
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
    exportFixtureBackup(context, dao, Uri.fromFile(File(context.filesDir, "plan-original.ronumi")))
    dao.updateSettings { it.copy(focusStrict = false, focusLockHome = false, focusSound = FocusSound.OFF, protection = false) }
    val now = java.time.LocalTime.now()
    val start = (now.hour * 60 + now.minute + 1) % 1440
    dao.saveSchedule(Schedule(9001, "Device alarm check", start, (start + 2) % 1440, startFocus = true, focusMinutes = 1))
    dev.agneswd.ronumi.schedule.Plans.refresh(context)
    check(dev.agneswd.ronumi.schedule.Plans.exactAllowed(context))
    return "One-minute focus scheduled for the next minute."
}

private suspend fun checkPlan(context: Context): String {
    val dao = context.app.dao
    try {
        check(dao.activeFocus() == null) { "Alarm did not end the timer" }
        val completed = dao.allSessions().filter { it.tag == "Device alarm check" }
        check(completed.single().completed && completed.single().focusedMillis == 60_000L)
        dev.agneswd.ronumi.focus.Focus.advance(context)
        dev.agneswd.ronumi.focus.Focus.giveUp(context)
        check(dao.allSessions().count { it.tag == "Device alarm check" } == 1)
        return "System alarm started focus after process death. Screen-off completion saved exactly one minute and one session."
    } finally {
        dao.clearActiveFocus()
        dev.agneswd.ronumi.focus.Celebrations.consume()
        importFixtureBackup(context, dao, Uri.fromFile(File(context.filesDir, "plan-original.ronumi")))
    }
}

private suspend fun prepareNotifications(context: Context): String {
    val dao = context.app.dao
    exportFixtureBackup(context, dao, Uri.fromFile(File(context.filesDir, "notifications-original.ronumi")))
    dao.clearHeld()
    val day = LocalDate.now().toString()
    val usage = dao.usageDay(day) ?: UsageDay(day, emptyMap(), 0)
    dao.saveUsageDay(usage.copy(heldCount = 0))
    context.getSharedPreferences("delivery", Context.MODE_PRIVATE).edit().clear().apply()
    dao.updateSettings { it.copy(heldPackages = setOf("dev.agneswd.ronumi.e2e"), holdAlways = true, notifyInboxSummaries = false) }
    return "Notification listener prepared for device test messages."
}

private suspend fun checkNotifications(context: Context): String {
    val dao = context.app.dao
    try {
        check(dao.allHeld().single().title == "Updated test")
        check(dao.usageDay(LocalDate.now().toString())?.heldCount == 1)
        val manager = context.getSystemService(android.app.NotificationManager::class.java)
        manager.cancel(10)
        val deliveryState = context.getSharedPreferences("delivery", Context.MODE_PRIVATE)
        val beforeDelivery = deliveryState.all.toMap()
        dev.agneswd.ronumi.notify.Delivery.release(context)
        kotlinx.coroutines.delay(100)
        check(manager.activeNotifications.none { it.id == 10 })
        check(deliveryState.all == beforeDelivery) { "Muted summaries marked messages as delivered" }
        check(dao.allHeld().single().title == "Updated test")
        check(dao.usageDay(LocalDate.now().toString())?.heldCount == 1)
        dao.updateSettings { it.copy(notifyInboxSummaries = true) }
        dev.agneswd.ronumi.notify.Delivery.release(context)
        // Android queues notification posts. Wait for the system to publish the result.
        val first = kotlinx.coroutines.withTimeout(5_000) {
            while (manager.activeNotifications.none { it.id == 10 }) kotlinx.coroutines.delay(50)
            manager.activeNotifications.single { it.id == 10 }.postTime
        }
        kotlinx.coroutines.delay(100)
        dev.agneswd.ronumi.notify.Delivery.release(context)
        check(manager.activeNotifications.single { it.id == 10 }.postTime == first)
        return "Held messages survived muted summaries and process restart. Enabling summaries delivered pending content once."
    } finally {
        importFixtureBackup(context, dao, Uri.fromFile(File(context.filesDir, "notifications-original.ronumi")))
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
    check(dao.currentSettings().let { it.notifyFocusEvents && it.notifyPlanReminders && it.notifyInboxSummaries })
    check(dao.allSchedules().single().icon == "auto")
    check(dao.currentSettings().petTapCount == 0)
    check(dao.currentSettings().themeMode == "SYSTEM" && dao.currentSettings().clockFormat == "SYSTEM" && dao.currentSettings().autoUpdateChecks)
    check(dao.currentSettings().freezeRewardedThrough.isEmpty())
    check(dao.allSessions().single().rewardDay.isEmpty() && dao.allSessions().single().rewardStartHour == -1)
    return "Schema 1 upgraded to schema 6. Original data, quest rules, notification defaults, and automatic icons were preserved."
}

/** Exercises actual file I/O, Room transactions, and policy changes in the installed app. */
private suspend fun checkStorage(context: Context): String {
    val dao = context.app.dao
    check(dao.activeFocus() == null) { "End the session before the storage check" }
    val originalPasses = context.app.database.withTransaction {
        context.app.database.openHelper.readableDatabase.query("SELECT day, packageName, expiresAt, uses FROM LimitPass").use { rows ->
            buildList {
                while (rows.moveToNext()) add(LimitPass(rows.getString(0), rows.getString(1), rows.getLong(2), rows.getInt(3)))
            }
        }
    }
    val original = File(context.filesDir, "check-original.ronumi")
    exportFixtureBackup(context, dao, Uri.fromFile(original))
    val results = mutableListOf<String>()
    try {
        val today = LocalDate.now()
        val yesterday = today.minusDays(1)
        val start = yesterday.atTime(9, 0).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val fixture = Backup(settings = Settings(onboarded = true, focusGoalMinutes = 25,
            notifyFocusEvents = false, notifyPlanReminders = false, notifyInboxSummaries = false, petTapCount = 1000, themeMode = "DARK", autoUpdateChecks = false),
            limits = listOf(AppLimit("com.google.android.deskclock", 1)),
            schedules = listOf(Schedule(1, "Overnight", 23 * 60, 6 * 60, days = 127, icon = "sleep")),
            sites = listOf(BlockedSite("example.com")),
            sessions = listOf(FocusSession(1, start, start + 25 * 60_000, 25 * 60_000, true, "Reading", "Local notes", 25, rewardDay = yesterday.toString(), rewardStartHour = 9)),
            usageDays = listOf(UsageDay(yesterday.toString(), mapOf("com.google.android.deskclock" to 12 * 60_000), 7, 2,
                limitMinutes = mapOf("com.google.android.deskclock" to 17))))
        val file = File(context.filesDir, "check-backup.ronumi")
        dao.replaceAll(fixture)
        val exported = File(context.filesDir, "check-export.ronumi")
        exportFixtureBackup(context, dao, Uri.fromFile(exported))
        dao.clearPasses()
        val usedPass = LimitPass(today.toString(), "dev.agneswd.ronumi.fixture", System.currentTimeMillis() + 60_000, 2)
        dao.savePass(usedPass)
        dao.updateSettings { it.copy(focusGoalMinutes = 60) }
        importFixtureBackup(context, dao, Uri.fromFile(exported))
        check(dao.currentSettings() == fixture.settings)
        check(dao.pass(usedPass.day, usedPass.packageName) == usedPass.copy(expiresAt = 0))
        check(dao.passesUsed(today.toString()) == 2)
        dao.clearPasses()
        results += "Restoring an older backup kept local pass usage and ended active passes."
        check(dao.allSessions() == fixture.sessions && dao.allUsageDays().containsAll(fixture.usageDays))
        check(dao.allLimits() == fixture.limits && dao.allSchedules() == fixture.schedules && dao.allSites() == fixture.sites)
        results += "Encrypted backup round trip preserved settings, limits, schedules, sites, notes, focus and usage history."

        val oldTree = Json.parseToJsonElement(Json.encodeToString(fixture)).jsonObject
        val oldBackup = JsonObject(oldTree.toMutableMap().apply {
            put("settings", JsonObject(oldTree.getValue("settings").jsonObject.filterKeys {
                it !in setOf("notifyFocusEvents", "notifyPlanReminders", "notifyInboxSummaries", "petTapCount", "themeMode", "clockFormat", "autoUpdateChecks", "freezeRewardedThrough")
            }))
            put("sessions", JsonArray(oldTree.getValue("sessions").jsonArray.map {
                JsonObject(it.jsonObject.filterKeys { key -> key !in setOf("rewardDay", "rewardStartHour") })
            }))
            put("schedules", JsonArray(oldTree.getValue("schedules").jsonArray.map {
                JsonObject(it.jsonObject.filterKeys { key -> key != "icon" })
            }))
        })
        val defaults = Json.decodeFromString<Backup>(oldBackup.toString()).validated()
        check(defaults.settings.let { it.notifyFocusEvents && it.notifyPlanReminders && it.notifyInboxSummaries })
        check(defaults.schedules.single().icon == "auto" && defaults.settings.petTapCount == 0)
        check(defaults.settings.themeMode == "SYSTEM" && defaults.settings.clockFormat == "SYSTEM" && defaults.settings.autoUpdateChecks)
        check(defaults.settings.freezeRewardedThrough.isEmpty())
        check(defaults.sessions.single().rewardDay.isEmpty() && defaults.sessions.single().rewardStartHour == -1)
        results += "Older record schemas preserve defaults for notifications, schedule icons, and reward dates."

        writeEncryptedFixture(file, Json.encodeToString(fixture.copy(limits = listOf(AppLimit("bad", -1)))))
        check(runCatching { importFixtureBackup(context, dao, Uri.fromFile(file)) }.isFailure)
        check(dao.allSessions() == fixture.sessions && dao.allLimits() == fixture.limits)
        check(runCatching { fixture.copy(schedules = fixture.schedules.map { it.copy(icon = "unknown") }).validated() }.isFailure)
        for (badCount in listOf(-1, 1001)) {
            check(runCatching { fixture.copy(settings = fixture.settings.copy(petTapCount = badCount)).validated() }.isFailure)
        }
        check(runCatching { fixture.copy(settings = fixture.settings.copy(themeMode = "unknown")).validated() }.isFailure)
        check(runCatching { fixture.copy(settings = fixture.settings.copy(clockFormat = "unknown")).validated() }.isFailure)
        check(runCatching { Json.decodeFromString<Backup>("{broken").validated() }.isFailure)
        check(runCatching { fixture.copy(sessions = listOf(
            fixture.sessions.single().copy(endedAt = start + 172_800_001L, focusedMillis = 172_800_001L)
        )).validated() }.isFailure)
        check(runCatching { fixture.copy(settings = fixture.settings.copy(freezeRewardedThrough = "not-a-date")).validated() }.isFailure)
        check(runCatching { fixture.copy(sessions = fixture.sessions.map { it.copy(rewardDay = "2026-02-30") }).validated() }.isFailure)
        check(runCatching { fixture.copy(sessions = fixture.sessions.map { it.copy(rewardStartHour = 24) }).validated() }.isFailure)
        results += "Record validation rejected invalid icons, counters, themes, focus durations, reward dates, and malformed records."

        val encrypted = exported.readBytes()
        val tampered = encrypted.copyOf().also { it[it.lastIndex] = (it.last().toInt() xor 1).toByte() }
        file.writeBytes(tampered)
        check(runCatching { importFixtureBackup(context, dao, Uri.fromFile(file)) }.isFailure)
        check(dao.allSessions() == fixture.sessions && dao.allLimits() == fixture.limits)
        val wrongPassword = "wrong fixture password".toCharArray()
        try {
            check(runCatching { importBackup(context, dao, Uri.fromFile(exported), wrongPassword) }.isFailure)
        } finally { wrongPassword.fill('\u0000') }
        check(dao.allSessions() == fixture.sessions && dao.allSchedules() == fixture.schedules)
        file.writeText(Json.encodeToString(fixture))
        check(runCatching { importFixtureBackup(context, dao, Uri.fromFile(file)) }.isFailure)
        check(dao.allSessions() == fixture.sessions && dao.allLimits() == fixture.limits)
        results += "Wrong passwords, changed ciphertext, and plaintext files left stored data unchanged."

        val xp = gameState(dao.allSessions(), dao.currentSettings(), today).xp
        dao.updateSettings { it.copy(focusGoalMinutes = 600) }
        check(gameState(dao.allSessions(), dao.currentSettings(), today).xp == xp)
        results += "Changing the current goal did not change historical XP."
        check(dev.agneswd.ronumi.insights.limitStreak(dao.allUsageDays(), "com.google.android.deskclock", today) == 1)
        dao.saveLimit(AppLimit("com.google.android.deskclock", 120))
        check(dev.agneswd.ronumi.insights.limitStreak(dao.allUsageDays(), "com.google.android.deskclock", today) == 1)
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
        val lockedRestore = runCatching { importFixtureBackup(context, dao, Uri.fromFile(exported)) }.exceptionOrNull()
        check(lockedRestore?.message == "Restore is locked while a protected schedule runs")
        results += "Protected schedules refused editor changes, block pause and backup restore."
    } finally {
        dao.clearActiveFocus()
        dao.updateSettings { it.copy(protection = false) }
        try {
            importFixtureBackup(context, dao, Uri.fromFile(original))
        } finally {
            context.app.database.withTransaction {
                dao.clearPasses()
                originalPasses.forEach { dao.savePass(it) }
            }
        }
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
        val secret = setOf("outfit_star_guardian")
        dao.updateSettings { it.copy(petTapCount = 990) }
        val taps = List(9) { dev.agneswd.ronumi.game.RonumiPets.pet(context) }
        taps.forEach { it.join() }
        check(dao.currentSettings().petTapCount == 999) { "Concurrent taps were lost" }
        check(RonumiStyles.resolve(secret, 20, 999).isEmpty())
        check(RonumiStyles.visibleItems(999).none { it.id in secret })
        dev.agneswd.ronumi.game.RonumiPets.pet(context).join()
        check(dao.currentSettings().petTapCount == 1000)
        check(RonumiStyles.resolve(secret, 1, 1000) == secret)
        dev.agneswd.ronumi.game.RonumiPets.pet(context).join()
        check(dao.currentSettings().petTapCount == 1000) { "Tap counter exceeded its cap" }
        val reopened = dev.agneswd.ronumi.data.RonumiDatabase.open(context)
        try {
            check(reopened.dao().currentSettings().petTapCount == 1000) { "Unlock did not persist" }
        } finally { reopened.close() }
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
        val resolved = RonumiStyles.resolve(worn + setOf("hat_crown", "color_peach", "unknown_item"), 2)
        check(resolved == worn) { "Locked or unknown items were equipped" }
        check(RonumiStyles.resolve(setOf("color_mint", "color_peach"), 3).size == 1) { "Two items occupied one slot" }
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
    check(RonumiStyles.resolve(settings.pebbleItems, state.level.number) == settings.pebbleItems)
    dao.replaceAll(Backup(settings = settings, sessions = sessions,
        limits = emptyList(), schedules = emptyList(), sites = emptyList()))
    return "Sample wardrobe history ready at level ${state.level.number}. This fixture replaced the previous history."
}


private inline fun <T> withFixturePassword(block: (CharArray) -> T): T {
    val password = "Ronumi debug backup fixture".toCharArray()
    return try { block(password) } finally { password.fill('\u0000') }
}

private suspend fun exportFixtureBackup(context: Context, dao: RonumiDao, target: Uri) =
    withFixturePassword { exportBackup(context, dao, target, it) }

private suspend fun importFixtureBackup(context: Context, dao: RonumiDao, source: Uri) =
    withFixturePassword { importBackup(context, dao, source, it) }

private fun writeEncryptedFixture(file: File, text: String) = withFixturePassword { password ->
    val bytes = text.encodeToByteArray()
    try { file.writeBytes(BackupCrypto.encrypt(bytes, password)) } finally { bytes.fill(0) }
}

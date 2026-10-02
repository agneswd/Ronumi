package dev.agneswd.stillpoint.ui.design

import dev.agneswd.stillpoint.game.GameState
import dev.agneswd.stillpoint.game.PebbleFeeling

/** Shared resting pose. Active focus and onboarding keep their own scene poses. */
fun GameState?.companionMood(): Mood = when (this?.disposition?.feeling) {
    PebbleFeeling.QUIET -> Mood.THINK
    PebbleFeeling.DOWN -> Mood.SAD
    PebbleFeeling.HAPPY -> Mood.HAPPY
    PebbleFeeling.PROUD -> Mood.PROUD
    PebbleFeeling.CELEBRATE -> Mood.CELEBRATE
    else -> Mood.IDLE
}

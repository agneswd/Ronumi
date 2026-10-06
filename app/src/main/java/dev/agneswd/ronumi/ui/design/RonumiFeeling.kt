package dev.agneswd.ronumi.ui.design

import dev.agneswd.ronumi.game.GameState
import dev.agneswd.ronumi.game.RonumiFeeling

/** Shared resting pose. Active focus and onboarding keep their own scene poses. */
fun GameState?.companionMood(): Mood = when (this?.disposition?.feeling) {
    RonumiFeeling.QUIET -> Mood.THINK
    RonumiFeeling.DOWN -> Mood.SAD
    RonumiFeeling.HAPPY -> Mood.HAPPY
    RonumiFeeling.PROUD -> Mood.PROUD
    RonumiFeeling.CELEBRATE -> Mood.CELEBRATE
    else -> Mood.IDLE
}

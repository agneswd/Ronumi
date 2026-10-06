package dev.agneswd.stillpoint

import androidx.compose.runtime.Composable
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.plus.Plus
import dev.agneswd.stillpoint.plus.PlayPlus
import dev.agneswd.stillpoint.plus.createStore

object Distribution {
    fun createPlus(app: StillpointApp): Plus = PlayPlus(app, createStore(app))

    /** Play does not show the Ronumi notice. */
    @Composable
    fun MigrationNotice() {}

    @Composable
    fun UpdateSettings(settings: Settings) = Unit
}

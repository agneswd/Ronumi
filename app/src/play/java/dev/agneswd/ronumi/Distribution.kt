package dev.agneswd.ronumi

import androidx.compose.runtime.Composable
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.plus.Plus
import dev.agneswd.ronumi.plus.PlayPlus
import dev.agneswd.ronumi.plus.createStore

object Distribution {
    /** Play builds do not block Android Settings, the installer, or uninstall. */
    const val guardsSystemScreens = false

    fun createPlus(app: RonumiApp): Plus = PlayPlus(app, createStore(app))

    @Composable
    fun UpdateSettings(settings: Settings) = Unit

    /** The Play build has no import screen. */
    @Composable
    fun ImportOffer(): Boolean? = false
}

package dev.agneswd.ronumi

import androidx.compose.runtime.Composable
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.plus.Plus
import dev.agneswd.ronumi.plus.PlayPlus
import dev.agneswd.ronumi.plus.createStore

object Distribution {
    fun createPlus(app: RonumiApp): Plus = PlayPlus(app, createStore(app))

    @Composable
    fun UpdateSettings(settings: Settings) = Unit
}

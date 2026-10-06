package dev.agneswd.ronumi

import android.app.Activity
import androidx.compose.runtime.Composable
import dev.agneswd.ronumi.data.Settings
import dev.agneswd.ronumi.data.settings
import dev.agneswd.ronumi.plus.Entitlement
import dev.agneswd.ronumi.plus.Plus
import dev.agneswd.ronumi.plus.PlusState
import dev.agneswd.ronumi.update.UpdateScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Distribution {
    fun createPlus(app: RonumiApp): Plus {
        app.scope.launch {
            app.dao.settings().map { it.autoUpdateChecks }.distinctUntilChanged().collect {
                UpdateScheduler.schedule(app, it)
            }
        }
        return GithubPlus
    }

    @Composable
    fun UpdateSettings(settings: Settings) = dev.agneswd.ronumi.ui.UpdateSettings(settings)
}

private object GithubPlus : Plus {
    override val state = MutableStateFlow(PlusState(Entitlement.UNLOCKED)).asStateFlow()
    override fun purchase(activity: Activity) = Unit
    override fun restore() = Unit
}

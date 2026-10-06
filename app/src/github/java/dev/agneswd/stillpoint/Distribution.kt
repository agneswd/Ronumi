package dev.agneswd.stillpoint

import android.app.Activity
import androidx.compose.runtime.Composable
import dev.agneswd.stillpoint.data.Settings
import dev.agneswd.stillpoint.data.settings
import dev.agneswd.stillpoint.plus.Entitlement
import dev.agneswd.stillpoint.plus.Plus
import dev.agneswd.stillpoint.plus.PlusState
import dev.agneswd.stillpoint.update.UpdateScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

object Distribution {
    fun createPlus(app: StillpointApp): Plus {
        app.scope.launch {
            app.dao.settings().map { it.autoUpdateChecks }.distinctUntilChanged().collect {
                UpdateScheduler.schedule(app, it)
            }
        }
        return GithubPlus
    }

    @Composable
    fun MigrationNotice() = dev.agneswd.stillpoint.ui.MigrationNotice()

    @Composable
    fun UpdateSettings(settings: Settings) = dev.agneswd.stillpoint.ui.UpdateSettings(settings)
}

private object GithubPlus : Plus {
    override val state = MutableStateFlow(PlusState(Entitlement.UNLOCKED)).asStateFlow()
    override fun purchase(activity: Activity) = Unit
    override fun restore() = Unit
}

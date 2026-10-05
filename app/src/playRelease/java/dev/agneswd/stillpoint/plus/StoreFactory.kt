package dev.agneswd.stillpoint.plus

import android.content.Context

internal fun createStore(context: Context): Store = GooglePlayStore(context)

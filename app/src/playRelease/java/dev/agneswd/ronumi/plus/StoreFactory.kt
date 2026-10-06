package dev.agneswd.ronumi.plus

import android.content.Context

internal fun createStore(context: Context): Store = GooglePlayStore(context)

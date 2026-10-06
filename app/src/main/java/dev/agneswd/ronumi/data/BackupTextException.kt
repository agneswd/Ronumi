package dev.agneswd.ronumi.data

import androidx.annotation.StringRes

/** Validation text is resolved by the screen, using its current locale. */
class BackupTextException(@param:StringRes val messageRes: Int) : IllegalArgumentException()

internal inline fun requireBackup(value: Boolean, message: () -> Int) {
    if (!value) throw BackupTextException(message())
}

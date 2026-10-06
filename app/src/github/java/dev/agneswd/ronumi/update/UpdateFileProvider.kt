package dev.agneswd.ronumi.update

import androidx.core.content.FileProvider

/** Exposes only the private update cache, with a temporary read grant for Android's installer. */
class UpdateFileProvider : FileProvider()

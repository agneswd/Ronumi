package dev.agneswd.stillpoint.plus

import android.content.Context
import java.io.File

/** Enabled only by a shell-created marker in a debuggable installation. Never compiled into release. */
internal fun createStore(context: Context): Store =
    if (File(context.noBackupFilesDir, "plus-fake-store").exists()) FakeStore(context) else GooglePlayStore(context)

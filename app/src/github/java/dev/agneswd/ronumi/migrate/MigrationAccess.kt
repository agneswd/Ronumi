package dev.agneswd.ronumi.migrate

import java.io.FileNotFoundException

/** The single export endpoint. URI grants cannot replace the signature permission. */
internal object MigrationAccess {
    const val PERMISSION = "dev.agneswd.stillpoint.permission.MIGRATE"
    const val URI = "content://dev.agneswd.stillpoint.migrate/backup"
    const val MIME_TYPE = "application/json"

    fun checkRead(uri: String, mode: String, permitted: Boolean) {
        if (!permitted) throw SecurityException("Migration permission required")
        if (mode != "r") throw SecurityException("Migration export is read-only")
        if (uri != URI) throw FileNotFoundException("Unknown migration URI")
    }
}

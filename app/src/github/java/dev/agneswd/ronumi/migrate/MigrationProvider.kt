package dev.agneswd.ronumi.migrate

import android.content.ContentProvider
import android.content.ContentProviderOperation
import android.content.ContentProviderResult
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import androidx.room.withTransaction
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.Backup
import dev.agneswd.ronumi.data.currentSettings
import dev.agneswd.ronumi.data.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import java.io.FileNotFoundException
import java.io.IOException

/** Gives same-signer apps a snapshot in the existing backup format, without a plaintext file. */
class MigrationProvider : ContentProvider() {
    // Include defaults so the existing Backup.version field is always explicit.
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    override fun onCreate() = true

    private fun checkRead(uri: Uri, mode: String = "r") {
        MigrationAccess.checkRead(uri.toString(), mode,
            requireNotNull(context).checkCallingOrSelfPermission(MigrationAccess.PERMISSION) == PackageManager.PERMISSION_GRANTED)
    }

    override fun getType(uri: Uri): String {
        checkRead(uri)
        return MigrationAccess.MIME_TYPE
    }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        checkRead(uri, mode)
        val app = requireNotNull(context).app
        val bytes = runBlocking(Dispatchers.IO) {
            val backup = app.database.withTransaction {
                val dao = app.dao
                Backup(settings = dao.currentSettings(), limits = dao.allLimits(), schedules = dao.allSchedules(),
                    sites = dao.allSites(), sessions = dao.allSessions(), usageDays = dao.allUsageDays())
            }
            json.encodeToString(backup).encodeToByteArray()
        }
        if (bytes.size > BackupCrypto.MAX_PLAINTEXT_BYTES) {
            bytes.fill(0)
            throw FileNotFoundException("Migration backup exceeds the size limit")
        }
        return try {
            openPipeHelper(uri, MigrationAccess.MIME_TYPE, null, bytes) { output, _, _, _, _ ->
                try {
                    ParcelFileDescriptor.AutoCloseOutputStream(output).use { it.write(bytes) }
                } catch (_: IOException) {
                    // The reader can close the pipe before the snapshot finishes.
                } finally {
                    bytes.fill(0)
                }
            }
        } catch (error: Exception) {
            bytes.fill(0)
            throw error
        }
    }

    private fun unsupported(): Nothing = throw UnsupportedOperationException("Only migration stream reads are supported")

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor = unsupported()
    override fun insert(uri: Uri, values: ContentValues?): Uri = unsupported()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = unsupported()
    override fun bulkInsert(uri: Uri, values: Array<out ContentValues>): Int = unsupported()
    override fun applyBatch(operations: ArrayList<ContentProviderOperation>): Array<ContentProviderResult> = unsupported()
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle = unsupported()
    override fun canonicalize(uri: Uri): Uri = unsupported()
    override fun uncanonicalize(uri: Uri): Uri = unsupported()
    override fun refresh(uri: Uri, extras: Bundle?, cancellationSignal: CancellationSignal?): Boolean = unsupported()
}

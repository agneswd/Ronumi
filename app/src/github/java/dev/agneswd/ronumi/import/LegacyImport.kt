package dev.agneswd.ronumi.import

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import dev.agneswd.ronumi.app
import dev.agneswd.ronumi.data.BackupCrypto
import dev.agneswd.ronumi.data.restorePlainDocument
import java.io.ByteArrayOutputStream
import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads one backup from the installed Stillpoint app.
 * The package name, permission, and provider authority stay on that app.
 */
internal object LegacyImport {
    const val PACKAGE = "dev.agneswd.stillpoint"
    const val PERMISSION = "dev.agneswd.stillpoint.permission.MIGRATE"
    const val AUTHORITY = "dev.agneswd.stillpoint.migrate"
    private const val URI = "content://dev.agneswd.stillpoint.migrate/backup"
    private const val PREFS = "legacy_import"
    private const val DECIDED = "decided"

    fun decided(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(DECIDED, false)

    fun remember(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(DECIDED, true).apply()
    }

    /** True only when the old app is installed, same-signed, and exports the provider. */
    fun offerAvailable(context: Context): Boolean {
        if (decided(context)) return false
        if (context.checkSelfPermission(PERMISSION) != PackageManager.PERMISSION_GRANTED) return false
        return trusted(context)
    }

    /**
     * The permission alone is not proof: an app with another key could define it at a weaker level.
     * The provider must belong to the old package, and that package must share Ronumi's signer.
     */
    @Suppress("DEPRECATION")
    private fun trusted(context: Context): Boolean {
        val manager = context.packageManager
        val provider = manager.resolveContentProvider(AUTHORITY, 0) ?: return false
        return provider.packageName == PACKAGE &&
            manager.checkSignatures(context.packageName, PACKAGE) == PackageManager.SIGNATURE_MATCH
    }

    /** Reads the provider to the end. The stream is a pipe, so this does not seek. */
    suspend fun importNow(context: Context) = withContext(Dispatchers.IO) {
        if (!trusted(context)) throw SecurityException("Stillpoint is not signed with the Ronumi key")
        val bytes = read(context)
        restorePlainDocument(context, context.app.dao, bytes)
    }

    private fun read(context: Context): ByteArray {
        val input = context.contentResolver.openInputStream(Uri.parse(URI)) ?: throw FileNotFoundException()
        return input.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val count = stream.read(buffer)
                if (count < 0) break
                if (out.size() + count > BackupCrypto.MAX_PLAINTEXT_BYTES) throw IOException("Backup exceeds 16 MB")
                out.write(buffer, 0, count)
            }
            out.toByteArray()
        }
    }
}

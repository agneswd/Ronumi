package dev.agneswd.stillpoint.update

import dev.agneswd.stillpoint.R
import androidx.annotation.StringRes
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/** Public GitHub metadata. The APK must pass independent package and signer checks before installation. */
data class UpdateRelease(
    val tag: String,
    val title: String,
    val assetName: String,
    val assetUrl: String,
    val bytes: Long,
    val sha256: String?,
)

sealed interface UpdateCheck {
    data class Available(val release: UpdateRelease) : UpdateCheck
    data object UpToDate : UpdateCheck
    data object NoRelease : UpdateCheck
    data class Failed(val message: String) : UpdateCheck
}

/** Requests public release metadata and APKs. Version 0.1.3 does not call this object. */
object UpdateClient {
    private val downloads = Mutex()

    suspend fun check(context: Context): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val installed = installed(context)
            val current = ReleaseVersion.parse(installed.versionName.orEmpty())
                ?: return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_unknown_version))
            val connection = connect(UpdatePolicy.RELEASE_API, metadata = true)
            val json = try {
                when (connection.responseCode) {
                    404 -> return@withContext UpdateCheck.NoRelease
                    403, 429 -> return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_rate_limit))
                    200 -> JSONObject(readSmallBody(connection))
                    else -> return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_release_unavailable))
                }
            } finally {
                connection.disconnect()
            }
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext UpdateCheck.NoRelease
            val tag = json.optString("tag_name").take(100)
            val latest = ReleaseVersion.parse(tag)
                ?: return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_unsupported_tag))
            if (latest <= current) return@withContext UpdateCheck.UpToDate
            val assets = json.optJSONArray("assets") ?: return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_no_apk))
            val candidates = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
                val name = it.optString("name")
                name.endsWith(".apk", true) && !name.contains("debug", true) && !name.contains("unsigned", true)
            }
            val asset = candidates.singleOrNull() ?: candidates.filter { it.optString("name").contains("universal", true) }.singleOrNull()
                ?: return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_multiple_apks))
            val url = asset.optString("browser_download_url")
            val bytes = asset.optLong("size", -1)
            if (!UpdatePolicy.isAssetUrl(url) || bytes !in 1..UpdatePolicy.MAX_APK_BYTES) {
                return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_invalid_asset))
            }
            val digest = asset.optString("digest").takeUnless { it.isBlank() || it == "null" }
            if (digest != null && !Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) {
                return@withContext UpdateCheck.Failed(context.getString(R.string.update_error_unsupported_checksum))
            }
            UpdateCheck.Available(UpdateRelease(
                tag, json.optString("name").ifBlank { tag }.take(120), asset.optString("name").take(200),
                url, bytes, digest?.substringAfter(':')?.lowercase(),
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            UpdateCheck.Failed(context.getString(R.string.update_error_check_connection))
        }
    }

    /** Downloads only after a user action. The progress callback runs on an IO worker. */
    suspend fun download(context: Context, release: UpdateRelease, onProgress: (Int) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            downloads.withLock {
                require(UpdatePolicy.isAssetUrl(release.assetUrl)) { context.getString(R.string.update_invalid_address) }
                require(release.bytes in 1..UpdatePolicy.MAX_APK_BYTES) { context.getString(R.string.update_invalid_size) }
                val version = ReleaseVersion.parse(release.tag) ?: throw UpdateException(R.string.update_error_unsupported_version)
                val directory = File(context.cacheDir, "updates").apply { if (!mkdirs() && !isDirectory) throw UpdateException(R.string.update_error_storage_unavailable) }
                val temporary = File.createTempFile("download-", ".part", directory)
                try {
                    val connection = connect(release.assetUrl, metadata = false)
                    val digest = MessageDigest.getInstance("SHA-256")
                    try {
                        if (connection.responseCode != 200) throw UpdateException(R.string.update_error_download_failed)
                        val declared = connection.contentLengthLong
                        if (declared > UpdatePolicy.MAX_APK_BYTES || declared > 0 && declared != release.bytes) {
                            throw UpdateException(R.string.update_error_size_changed)
                        }
                        connection.inputStream.use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                var lastProgress = -1
                                val deadline = System.nanoTime() + 600_000_000_000L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    if (System.nanoTime() >= deadline) throw UpdateException(R.string.update_error_download_timeout)
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > release.bytes || total > UpdatePolicy.MAX_APK_BYTES) throw UpdateException(R.string.update_error_too_large)
                                    output.write(buffer, 0, count)
                                    digest.update(buffer, 0, count)
                                    val progress = (total * 100 / release.bytes).toInt()
                                    if (progress != lastProgress) { onProgress(progress); lastProgress = progress }
                                }
                                if (total != release.bytes) throw UpdateException(R.string.update_error_incomplete)
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                    val hash = digest.digest().hex()
                    if (release.sha256 != null && release.sha256 != hash) throw UpdateException(R.string.update_error_checksum_mismatch)
                    val archive = validateApk(context, temporary)
                    if (ReleaseVersion.parse(archive.versionName.orEmpty()) != version) throw UpdateException(R.string.update_error_version_mismatch)
                    val ready = File(directory, "$hash.apk")
                    if (ready.exists()) {
                        // The identical content was already downloaded. Validate it before returning it.
                        if (sha256(ready) != hash) throw UpdateException(R.string.update_error_cache_changed)
                        validateApk(context, ready)
                    } else if (!temporary.renameTo(ready)) throw UpdateException(R.string.update_error_save_failed)
                    ready
                } finally {
                    temporary.delete()
                }
            }
        }

    fun canInstall(context: Context): Boolean = context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntent(context: Context): Intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"),
    )

    /** Call only after an Install tap. Android presents its own confirmation screen. */
    suspend fun installIntent(context: Context, file: File): Intent = withContext(Dispatchers.IO) {
        val directory = File(context.cacheDir, "updates").canonicalFile
        val ready = file.canonicalFile
        if (ready.parentFile != directory || !Regex("[a-f0-9]{64}\\.apk").matches(ready.name)) throw UpdateException(R.string.update_error_invalid_file)
        if (sha256(ready) != ready.name.removeSuffix(".apk")) throw UpdateException(R.string.update_error_file_changed)
        validateApk(context, ready)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready)
        @Suppress("DEPRECATION")
        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri(context.getString(R.string.update_title), uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Suppress("DEPRECATION")
    private fun installed(context: Context): PackageInfo = context.packageManager.getPackageInfo(
        context.packageName, PackageManager.GET_SIGNING_CERTIFICATES,
    )

    @Suppress("DEPRECATION")
    private fun validateApk(context: Context, file: File): PackageInfo {
        if (!file.isFile || file.length() !in 1..UpdatePolicy.MAX_APK_BYTES) throw UpdateException(R.string.update_error_file_missing)
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw UpdateException(R.string.update_error_invalid_apk)
        val current = installed(context)
        if (candidate.packageName != context.packageName) throw UpdateException(R.string.update_error_wrong_package)
        if (candidate.longVersionCode <= current.longVersionCode) throw UpdateException(R.string.update_error_not_newer)
        if ((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) throw UpdateException(R.string.update_error_android_version)
        fun signers(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty()
            .mapTo(mutableSetOf()) { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }
        if (!UpdatePolicy.sameSigners(signers(current), signers(candidate))) {
            throw UpdateException(R.string.update_error_signer_mismatch)
        }
        return candidate
    }

    private suspend fun readSmallBody(connection: HttpsURLConnection): String = connection.inputStream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val deadline = System.nanoTime() + 60_000_000_000L
        while (true) {
            currentCoroutineContext().ensureActive()
            if (System.nanoTime() >= deadline) throw UpdateException(R.string.update_error_check_timeout)
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > 2 * 1024 * 1024) throw UpdateException(R.string.update_error_response_too_large)
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }

    /** Redirects never leave the small allowlist of GitHub release hosts. */
    private fun connect(address: String, metadata: Boolean): HttpsURLConnection {
        var current = address
        repeat(6) {
            if (metadata && current != UpdatePolicy.RELEASE_API || !metadata && !UpdatePolicy.isDownloadUrl(current)) {
                throw UpdateException(R.string.update_error_unsafe_redirect)
            }
            val connection = URL(current).openConnection() as HttpsURLConnection
            connection.instanceFollowRedirects = false
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.useCaches = false
            connection.setRequestProperty("User-Agent", "Stillpoint-Updater")
            connection.setRequestProperty("Accept", if (metadata) "application/vnd.github+json" else "application/octet-stream")
            if (metadata) connection.setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
            try {
                if (connection.responseCode !in listOf(301, 302, 303, 307, 308)) return connection
                val location = connection.getHeaderField("Location") ?: throw UpdateException(R.string.update_error_missing_redirect)
                current = URL(URL(current), location).toString()
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw UpdateException(R.string.update_error_redirect_limit)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(32 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().hex()
    }

    private fun ByteArray.hex(): String = joinToString("") { "%02x".format(it.toInt() and 255) }
}

internal class UpdateException(@param:StringRes val messageRes: Int) : IOException()

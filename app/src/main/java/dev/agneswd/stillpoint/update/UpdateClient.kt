package dev.agneswd.stillpoint.update

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

/** Requests public release metadata and APKs. Never uploads app activity or account data. */
object UpdateClient {
    private val downloads = Mutex()

    suspend fun check(context: Context): UpdateCheck = withContext(Dispatchers.IO) {
        try {
            val installed = installed(context)
            val current = ReleaseVersion.parse(installed.versionName.orEmpty())
                ?: return@withContext UpdateCheck.Failed("This build has an unknown version. Check GitHub for updates.")
            val connection = connect(UpdatePolicy.RELEASE_API, metadata = true)
            val json = try {
                when (connection.responseCode) {
                    404 -> return@withContext UpdateCheck.NoRelease
                    403, 429 -> return@withContext UpdateCheck.Failed("GitHub is limiting update checks. Try again later.")
                    200 -> JSONObject(readSmallBody(connection))
                    else -> return@withContext UpdateCheck.Failed("GitHub could not provide an update. Try again later.")
                }
            } finally {
                connection.disconnect()
            }
            if (json.optBoolean("draft") || json.optBoolean("prerelease")) return@withContext UpdateCheck.NoRelease
            val tag = json.optString("tag_name").take(100)
            val latest = ReleaseVersion.parse(tag)
                ?: return@withContext UpdateCheck.Failed("The latest release has an unsupported version tag.")
            if (latest <= current) return@withContext UpdateCheck.UpToDate
            val assets = json.optJSONArray("assets") ?: return@withContext UpdateCheck.Failed("The release has no APK yet.")
            val candidates = (0 until assets.length()).map { assets.getJSONObject(it) }.filter {
                val name = it.optString("name")
                name.endsWith(".apk", true) && !name.contains("debug", true) && !name.contains("unsigned", true)
            }
            val asset = candidates.singleOrNull() ?: candidates.filter { it.optString("name").contains("universal", true) }.singleOrNull()
                ?: return@withContext UpdateCheck.Failed("The release needs one universal APK.")
            val url = asset.optString("browser_download_url")
            val bytes = asset.optLong("size", -1)
            if (!UpdatePolicy.isAssetUrl(url) || bytes !in 1..UpdatePolicy.MAX_APK_BYTES) {
                return@withContext UpdateCheck.Failed("The release APK has an invalid address or size.")
            }
            val digest = asset.optString("digest").takeUnless { it.isBlank() || it == "null" }
            if (digest != null && !Regex("sha256:[a-fA-F0-9]{64}").matches(digest)) {
                return@withContext UpdateCheck.Failed("The release APK has an unsupported checksum.")
            }
            UpdateCheck.Available(UpdateRelease(
                tag, json.optString("name").ifBlank { tag }.take(120), asset.optString("name").take(200),
                url, bytes, digest?.substringAfter(':')?.lowercase(),
            ))
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            UpdateCheck.Failed("Could not check for updates. Check your connection and try again.")
        }
    }

    /** Downloads only after a user action. The progress callback runs on an IO worker. */
    suspend fun download(context: Context, release: UpdateRelease, onProgress: (Int) -> Unit = {}): File =
        withContext(Dispatchers.IO) {
            downloads.withLock {
                require(UpdatePolicy.isAssetUrl(release.assetUrl)) { "Invalid update address." }
                require(release.bytes in 1..UpdatePolicy.MAX_APK_BYTES) { "Invalid update size." }
                val version = ReleaseVersion.parse(release.tag) ?: throw IOException("Unsupported release version.")
                val directory = File(context.cacheDir, "updates").apply { if (!mkdirs() && !isDirectory) throw IOException("Cannot create update storage.") }
                val temporary = File.createTempFile("download-", ".part", directory)
                try {
                    val connection = connect(release.assetUrl, metadata = false)
                    val digest = MessageDigest.getInstance("SHA-256")
                    try {
                        if (connection.responseCode != 200) throw IOException("GitHub could not download the update.")
                        val declared = connection.contentLengthLong
                        if (declared > UpdatePolicy.MAX_APK_BYTES || declared > 0 && declared != release.bytes) {
                            throw IOException("The update size changed. Check for updates again.")
                        }
                        connection.inputStream.use { input ->
                            temporary.outputStream().use { output ->
                                val buffer = ByteArray(32 * 1024)
                                var total = 0L
                                var lastProgress = -1
                                val deadline = System.nanoTime() + 600_000_000_000L
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    if (System.nanoTime() >= deadline) throw IOException("The update download timed out. Try again.")
                                    val count = input.read(buffer)
                                    if (count < 0) break
                                    total += count
                                    if (total > release.bytes || total > UpdatePolicy.MAX_APK_BYTES) throw IOException("The update exceeds its expected size.")
                                    output.write(buffer, 0, count)
                                    digest.update(buffer, 0, count)
                                    val progress = (total * 100 / release.bytes).toInt()
                                    if (progress != lastProgress) { onProgress(progress); lastProgress = progress }
                                }
                                if (total != release.bytes) throw IOException("The update download is incomplete. Try again.")
                            }
                        }
                    } finally {
                        connection.disconnect()
                    }
                    val hash = digest.digest().hex()
                    if (release.sha256 != null && release.sha256 != hash) throw IOException("The update checksum does not match.")
                    val archive = validateApk(context, temporary)
                    if (ReleaseVersion.parse(archive.versionName.orEmpty()) != version) throw IOException("The APK version does not match the release.")
                    val ready = File(directory, "$hash.apk")
                    if (ready.exists()) {
                        // The identical content was already downloaded. Validate it before returning it.
                        if (sha256(ready) != hash) throw IOException("The cached update changed. Clear the app cache and try again.")
                        validateApk(context, ready)
                    } else if (!temporary.renameTo(ready)) throw IOException("Cannot save the downloaded update.")
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
        if (ready.parentFile != directory || !Regex("[a-f0-9]{64}\\.apk").matches(ready.name)) throw IOException("Invalid update file.")
        if (sha256(ready) != ready.name.removeSuffix(".apk")) throw IOException("The downloaded update changed. Download it again.")
        validateApk(context, ready)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.updates", ready)
        @Suppress("DEPRECATION")
        Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            clipData = ClipData.newRawUri("Stillpoint update", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    @Suppress("DEPRECATION")
    private fun installed(context: Context): PackageInfo = context.packageManager.getPackageInfo(
        context.packageName, PackageManager.GET_SIGNING_CERTIFICATES,
    )

    @Suppress("DEPRECATION")
    private fun validateApk(context: Context, file: File): PackageInfo {
        if (!file.isFile || file.length() !in 1..UpdatePolicy.MAX_APK_BYTES) throw IOException("The update APK is missing or too large.")
        val candidate = context.packageManager.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: throw IOException("The download is not a valid Android APK.")
        val current = installed(context)
        if (candidate.packageName != context.packageName) throw IOException("The APK is for a different app.")
        if (candidate.longVersionCode <= current.longVersionCode) throw IOException("The APK is not newer than this installation.")
        if ((candidate.applicationInfo?.minSdkVersion ?: Int.MAX_VALUE) > Build.VERSION.SDK_INT) throw IOException("This update requires a newer Android version.")
        fun signers(info: PackageInfo): Set<String> = info.signingInfo?.apkContentsSigners.orEmpty()
            .mapTo(mutableSetOf()) { MessageDigest.getInstance("SHA-256").digest(it.toByteArray()).hex() }
        if (!UpdatePolicy.sameSigners(signers(current), signers(candidate))) {
            throw IOException("The update signing key does not match this installation.")
        }
        return candidate
    }

    private suspend fun readSmallBody(connection: HttpsURLConnection): String = connection.inputStream.use { input ->
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        val deadline = System.nanoTime() + 60_000_000_000L
        while (true) {
            currentCoroutineContext().ensureActive()
            if (System.nanoTime() >= deadline) throw IOException("The release check timed out.")
            val count = input.read(buffer)
            if (count < 0) break
            if (output.size() + count > 2 * 1024 * 1024) throw IOException("Release response is too large.")
            output.write(buffer, 0, count)
        }
        output.toString(Charsets.UTF_8.name())
    }

    /** Redirects never leave the small allowlist of GitHub release hosts. */
    private fun connect(address: String, metadata: Boolean): HttpsURLConnection {
        var current = address
        repeat(6) {
            if (metadata && current != UpdatePolicy.RELEASE_API || !metadata && !UpdatePolicy.isDownloadUrl(current)) {
                throw IOException("The update redirected outside GitHub releases.")
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
                val location = connection.getHeaderField("Location") ?: throw IOException("Missing update redirect.")
                current = URL(URL(current), location).toString()
            } catch (error: Exception) {
                connection.disconnect()
                throw error
            }
            connection.disconnect()
        }
        throw IOException("Too many update redirects.")
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

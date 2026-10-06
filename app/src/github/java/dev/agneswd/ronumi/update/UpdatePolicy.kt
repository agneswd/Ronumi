package dev.agneswd.ronumi.update

import java.net.URI

/** Stable numeric release tags only. Preview and unknown tag formats are never offered. */
data class ReleaseVersion(val major: Long, val minor: Long, val patch: Long) : Comparable<ReleaseVersion> {
    override fun compareTo(other: ReleaseVersion): Int = compareValuesBy(this, other, { it.major }, { it.minor }, { it.patch })

    companion object {
        fun parse(tag: String): ReleaseVersion? {
            val match = Regex("[vV]?([0-9]+)\\.([0-9]+)\\.([0-9]+)").matchEntire(tag) ?: return null
            val parts = match.groupValues.drop(1).map { it.toLongOrNull() ?: return null }
            return ReleaseVersion(parts[0], parts[1], parts[2])
        }
    }
}

/** What a release-API status means before the body is read. */
internal enum class ReleaseLookup { ReadBody, UpToDate, RateLimited, Unavailable }

/** Which installed package an APK belongs to. The old package is never installed. */
internal enum class ApkPackageDecision { CurrentApp, LegacyApp, OtherApp }

internal object UpdatePolicy {
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    const val LEGACY_PACKAGE = "dev.agneswd.stillpoint"
    const val RELEASE_API = "https://api.github.com/repos/agneswd/Ronumi/releases/latest"
    private const val ASSET_PATH = "/agneswd/Ronumi/releases/download/"

    /** A 404 means the Ronumi repository has no public release yet. That is not an error. */
    fun releaseLookup(status: Int): ReleaseLookup = when (status) {
        200 -> ReleaseLookup.ReadBody
        404 -> ReleaseLookup.UpToDate
        403, 429 -> ReleaseLookup.RateLimited
        else -> ReleaseLookup.Unavailable
    }

    /** The package name is known only after the APK is downloaded. */
    fun apkPackageDecision(candidatePackage: String, installedPackage: String): ApkPackageDecision = when (candidatePackage) {
        LEGACY_PACKAGE -> ApkPackageDecision.LegacyApp
        installedPackage -> ApkPackageDecision.CurrentApp
        else -> ApkPackageDecision.OtherApp
    }

    fun isAssetUrl(url: String): Boolean = safeUri(url)?.let {
        it.host == "github.com" && it.rawPath.startsWith(ASSET_PATH) && it.rawQuery == null &&
            '%' !in it.rawPath && it.normalize().rawPath == it.rawPath && it.rawPath.endsWith(".apk", ignoreCase = true)
    } == true

    fun isDownloadUrl(url: String): Boolean = safeUri(url)?.let {
        isAssetUrl(url) || it.host in setOf("release-assets.githubusercontent.com", "objects.githubusercontent.com")
    } == true

    private fun safeUri(url: String): URI? = runCatching { URI(url) }.getOrNull()?.takeIf {
        it.scheme == "https" && it.host != null && it.userInfo == null && it.fragment == null &&
            (it.port == -1 || it.port == 443)
    }

    /** Rotation is rejected until it can be verified safely. Android also verifies the final install. */
    fun sameSigners(installed: Set<String>, candidate: Set<String>): Boolean = installed.isNotEmpty() && installed == candidate
}

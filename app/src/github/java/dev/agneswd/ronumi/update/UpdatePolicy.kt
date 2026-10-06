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

internal object UpdatePolicy {
    const val MAX_APK_BYTES = 128L * 1024 * 1024
    const val RELEASE_API = "https://api.github.com/repos/agneswd/Stillpoint/releases/latest"
    private const val ASSET_PATH = "/agneswd/Stillpoint/releases/download/"

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

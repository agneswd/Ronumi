package dev.agneswd.stillpoint.guard

import android.view.accessibility.AccessibilityNodeInfo
import dev.agneswd.stillpoint.data.Settings

/**
 * A short-video feed inside an app. The guard finds it by view ids, or by a selected
 * tab with one of [selectedTabLabels]. Apps change these ids. Update this table when one breaks.
 */
data class ShortsFeed(
    val packageName: String,
    val name: String,
    val viewIds: List<String> = emptyList(),
    val selectedTabLabels: List<String> = emptyList(),
    val enabled: (Settings) -> Boolean,
)

val shortsFeeds = listOf(
    ShortsFeed(
        "com.google.android.youtube",
        "YouTube Shorts",
        viewIds = listOf("reel_recycler", "reel_player_page_container", "reel_watch_player", "shorts_container"),
        enabled = { it.blockYoutubeShorts },
    ),
    ShortsFeed(
        "com.instagram.android",
        "Instagram Reels",
        viewIds = listOf("clips_viewer_view_pager", "clips_viewer_container", "clips_video_container"),
        enabled = { it.blockInstagramReels },
    ),
    ShortsFeed(
        "com.snapchat.android",
        "Snapchat Spotlight",
        selectedTabLabels = listOf("Spotlight"),
        enabled = { it.blockSnapchatSpotlight },
    ),
    ShortsFeed(
        "com.facebook.katana",
        "Facebook Reels",
        selectedTabLabels = listOf("Reels", "Video"),
        enabled = { it.blockFacebookReels },
    ),
)

/** Address bar view ids of common browsers. */
val browserUrlBars = mapOf(
    "com.android.chrome" to "url_bar",
    "com.chrome.beta" to "url_bar",
    "com.brave.browser" to "url_bar",
    "com.microsoft.emmx" to "url_bar",
    "com.vivaldi.browser" to "url_bar",
    "com.kiwibrowser.browser" to "url_bar",
    "org.mozilla.firefox" to "mozac_browser_toolbar_url_view",
    "org.mozilla.fenix" to "mozac_browser_toolbar_url_view",
    "org.mozilla.focus" to "mozac_browser_toolbar_url_view",
    "com.sec.android.app.sbrowser" to "location_bar_edit_text",
    "com.opera.browser" to "url_field",
    "com.opera.mini.native" to "url_field",
    "com.duckduckgo.mobile.android" to "omnibarTextInput",
)

fun ShortsFeed.isShowing(root: AccessibilityNodeInfo): Boolean {
    if (viewIds.any { root.findAccessibilityNodeInfosByViewId("$packageName:id/$it").isNotEmpty() }) return true
    if (selectedTabLabels.isEmpty()) return false
    return root.anyNode { node ->
        node.isSelected && selectedTabLabels.any { label ->
            node.contentDescription?.toString()?.startsWith(label, ignoreCase = true) == true ||
                node.text?.toString().equals(label, ignoreCase = true)
        }
    }
}

/** The host in the address bar, or null when the bar holds a search query or nothing. */
fun AccessibilityNodeInfo.browserHost(packageName: String): String? {
    val id = browserUrlBars[packageName] ?: return null
    val bar = findAccessibilityNodeInfosByViewId("$packageName:id/$id").firstOrNull() ?: return null
    // Do not judge half-typed text. Check the address after the page loads.
    if (bar.isFocused) return null
    return hostOf(bar.text?.toString().orEmpty())
}

fun hostOf(text: String): String? {
    val trimmed = text.trim().lowercase()
    if (trimmed.isEmpty() || ' ' in trimmed || '.' !in trimmed) return null
    return trimmed.substringAfter("://").substringBefore('/').substringBefore('?').substringBefore(':')
        .removePrefix("www.").ifEmpty { null }
}

private val adultDomains = setOf(
    "pornhub.com", "xvideos.com", "xnxx.com", "xhamster.com", "redtube.com", "youporn.com",
    "onlyfans.com", "chaturbate.com", "stripchat.com", "spankbang.com", "eporner.com",
    "brazzers.com", "tube8.com", "rule34.xxx", "nhentai.net", "hentaihaven.xxx",
)
private val adultWords = listOf("porn", "xxx", "hentai", "nsfw")

/** The blocked domain that matches [host], or null. Subdomains match their parent. */
fun blockedSiteFor(host: String, sites: Set<String>, blockAdult: Boolean): String? {
    fun matches(domain: String) = host == domain || host.endsWith(".$domain")
    sites.firstOrNull(::matches)?.let { return it }
    if (!blockAdult) return null
    adultDomains.firstOrNull(::matches)?.let { return it }
    if (host.endsWith(".xxx") || adultWords.any { it in host }) return host
    return null
}

private fun AccessibilityNodeInfo.anyNode(predicate: (AccessibilityNodeInfo) -> Boolean): Boolean {
    if (predicate(this)) return true
    for (i in 0 until childCount) {
        val child = getChild(i) ?: continue
        if (child.anyNode(predicate)) return true
    }
    return false
}

/**
 * True when any text in the window contains [needle]. It walks the tree itself, because
 * findAccessibilityNodeInfosByText finds nothing in Compose screens such as the new Settings pages.
 */
fun AccessibilityNodeInfo.containsText(needle: String): Boolean = anyNode { node ->
    node.text?.contains(needle, ignoreCase = true) == true ||
        node.contentDescription?.contains(needle, ignoreCase = true) == true
}

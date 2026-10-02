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
        viewIds = listOf(
            "reel_recycler",
            "reel_player_page_container",
            "reel_watch_player",
            "reel_watch_fragment_root",
            "reel_player_overlay",
            "shorts_container",
        ),
        selectedTabLabels = listOf("Shorts"),
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
        selectedTabLabels = listOf("Reels"),
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

/** Normalizes a host without accepting spaces, credentials, or unrelated domain suffixes. */
fun hostOf(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.any(Char::isWhitespace)) return null
    return runCatching {
        val uri = java.net.URI(if ("://" in trimmed) trimmed else "https://$trimmed")
        if (uri.scheme !in setOf("http", "https") || uri.rawUserInfo != null) return null
        val host = (uri.host ?: uri.rawAuthority?.substringBefore(':')) ?: return null
        val ascii = java.net.IDN.toASCII(host.lowercase(java.util.Locale.ROOT).trimEnd('.')).removePrefix("www.")
        ascii.takeIf { it.length <= 253 && '.' in it && it.split('.').all { part ->
            part.isNotEmpty() && part.length <= 63 && part.first() != '-' && part.last() != '-' && part.all { c -> c.isLetterOrDigit() || c == '-' }
        } }
    }.getOrNull()
}

fun allowedSite(host: String, sites: Set<String>): Boolean = sites.any { host == it || host.endsWith(".$it") }

/** Uses the visible video title rather than a timer to permit exactly the first identified video. */
fun ShortsFeed.videoIdentity(root: AccessibilityNodeInfo): String? {
    val ids = when (packageName) {
        "com.google.android.youtube" -> listOf("reel_video_title", "reel_title")
        "com.instagram.android" -> listOf("clips_caption_text", "clips_video_title", "caption")
        else -> emptyList()
    }
    return ids.flatMap { root.findAccessibilityNodeInfosByViewId("$packageName:id/$it") }
        .filter { it.isVisibleToUser }
        .mapNotNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) }.joinToString("|").ifEmpty { null }
}

fun AccessibilityNodeInfo.youtubeChannel(): String? = listOf("channel_name", "owner_text", "channel_title", "video_owner")
    .firstNotNullOfOrNull { id -> findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$id")
        .filter { it.isVisibleToUser }
        .firstNotNullOfOrNull { it.text?.toString()?.trim()?.takeIf(String::isNotEmpty) } }

fun AccessibilityNodeInfo.youtubePlayer(): Boolean = listOf("player_view", "watch_fragment", "player_overlays", "watch_player")
    .any { findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$it").isNotEmpty() }

private val youtubeSearchIds = listOf(
    "search_edit_text",
    "search_query",
    "search_box",
    "search_clear",
    "search_results_list",
    "search_suggestion_list",
)

/**
 * True on the YouTube home feed.
 * Search, subscriptions, a channel, a normal player, and Shorts are not the home feed.
 * The Home tab stays selected while search is open, so the tab alone is not enough.
 */
fun AccessibilityNodeInfo.youtubeHome(): Boolean {
    if (youtubeSearchOpen() || youtubePlayer()) return false
    if (shortsFeeds.first { it.packageName == "com.google.android.youtube" }.isShowing(this)) return false
    if (selectedLabel("Subscriptions") || selectedLabel("Library") || selectedLabel("You")) return false
    if (visibleExact("Subscribe") || visibleExact("Subscribed")) return false
    return selectedLabel("Home")
}

enum class YoutubePlace { SEARCH, HOME, SUBSCRIPTIONS, OTHER }

/** Search wins over the Home tab. YouTube leaves that tab selected while search is open. */
fun AccessibilityNodeInfo.youtubePlace(): YoutubePlace = when {
    youtubeSearchOpen() -> YoutubePlace.SEARCH
    selectedLabel("Subscriptions") || selectedLabel("Library") -> YoutubePlace.SUBSCRIPTIONS
    youtubeHome() || selectedLabel("Home") -> YoutubePlace.HOME
    else -> YoutubePlace.OTHER
}

/** The typed query on the search results page. The hint "Search YouTube" is not a query. */
fun AccessibilityNodeInfo.youtubeSearchQuery(): String? {
    val text = findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/search_query")
        .firstOrNull { it.isVisibleToUser }
        ?.text?.toString()?.trim().orEmpty()
    if (text.isEmpty() || text.equals("Search YouTube", true)) return null
    return text
}

/** True while the search field or the search results are on screen. */
fun AccessibilityNodeInfo.youtubeSearchOpen(): Boolean {
    if (youtubeSearchIds.any { id ->
            findAccessibilityNodeInfosByViewId("com.google.android.youtube:id/$id").any { it.isVisibleToUser }
        }
    ) return true
    return anyNode { node ->
        if (!node.isVisibleToUser) return@anyNode false
        val id = node.viewIdResourceName.orEmpty()
        if (id.endsWith("/search_edit_text") || id.endsWith("/search_query")) return@anyNode true
        val cls = node.className?.toString().orEmpty()
        if (!cls.endsWith("EditText")) return@anyNode false
        val hint = node.hintText?.toString().orEmpty()
        val desc = node.contentDescription?.toString().orEmpty()
        hint.contains("search", true) || desc.contains("search", true)
    }
}

/** Clicks a bottom-bar tab or a control whose label is exactly [label]. */
fun AccessibilityNodeInfo.clickNav(label: String): Boolean = anyNode { node ->
    if (!node.isVisibleToUser) return@anyNode false
    val desc = node.contentDescription?.toString().orEmpty()
    val text = node.text?.toString().orEmpty()
    if (!labelMatches(desc, label) && !labelMatches(text, label)) return@anyNode false
    node.performAction(AccessibilityNodeInfo.ACTION_CLICK) || node.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
}

private fun AccessibilityNodeInfo.selectedLabel(label: String): Boolean = anyNode {
    it.isSelected && (labelMatches(it.contentDescription?.toString(), label) || labelMatches(it.text?.toString(), label))
}

private fun AccessibilityNodeInfo.visibleExact(label: String): Boolean = anyNode {
    it.isVisibleToUser && (it.text?.toString()?.equals(label, true) == true || it.contentDescription?.toString()?.equals(label, true) == true)
}

private fun labelMatches(value: String?, label: String): Boolean {
    val text = value?.trim().orEmpty()
    return text.equals(label, true) || text.startsWith("$label,", true) || text.startsWith("$label ", true)
}

fun channelKey(value: String): String = value.trim().removePrefix("@").lowercase(java.util.Locale.ROOT)

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

/** Opens search before covering the home feed, so closing the block leaves a useful route. */
fun AccessibilityNodeInfo.openYoutubeSearch(): Boolean = anyNode {
    it.contentDescription?.toString()?.equals("Search", true) == true &&
        (it.performAction(AccessibilityNodeInfo.ACTION_CLICK) || it.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true)
}

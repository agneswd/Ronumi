package dev.agneswd.stillpoint.guard

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/** Known channels supplement Android's list of apps that handle general web links. */
private val knownBrowsers = setOf(
    "com.android.chrome", "com.chrome.beta", "com.chrome.dev", "com.chrome.canary",
    "com.brave.browser", "com.brave.browser_beta", "com.brave.browser_nightly",
    "com.microsoft.emmx", "com.microsoft.emmx.beta", "com.microsoft.emmx.dev", "com.microsoft.emmx.canary",
    "com.vivaldi.browser", "com.vivaldi.browser.snapshot", "com.kiwibrowser.browser",
    "org.mozilla.firefox", "org.mozilla.firefox_beta", "org.mozilla.fenix",
    "org.mozilla.focus", "org.mozilla.klar", "org.torproject.torbrowser",
    "com.sec.android.app.sbrowser", "com.sec.android.app.sbrowser.beta",
    "com.opera.browser", "com.opera.browser.beta", "com.opera.mini.native", "com.opera.touch",
    "com.duckduckgo.mobile.android",
)

fun Context.browserPackages(): Set<String> {
    val web = Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com"))
        .addCategory(Intent.CATEGORY_BROWSABLE)
    return knownBrowsers + packageManager.queryIntentActivities(web, android.content.pm.PackageManager.GET_RESOLVED_FILTER)
        .filter { it.filter?.countDataAuthorities() == 0 }.map { it.activityInfo.packageName }
}

/** Native address controls, most specific first. Firefox shows its search box only while editing. */
private val addressIds = listOf(
    "url_bar", "url_field", "url_input", "url_edit_text", "address_bar", "addressbar",
    "location_bar_edit_text", "location_bar_text", "omnibarTextInput", "omnibar_text_input",
    "mozac_browser_toolbar_url_view", "mozac_browser_toolbar_edit_url_view", "urlView", "urlInput",
    "ADDRESSBAR_URL_BOX", "ADDRESSBAR_SEARCH_BOX",
)

/**
 * Read only native browser controls. A page cannot impersonate an address bar inside its WebView.
 * A focused control wins, because it is the one the user edits.
 */
fun AccessibilityNodeInfo.browserAddressBar(): AccessibilityNodeInfo? {
    val pending = ArrayDeque<AccessibilityNodeInfo>()
    pending.add(this)
    var visited = 0
    var best: AccessibilityNodeInfo? = null
    var bestRank = Int.MAX_VALUE
    while (pending.isNotEmpty() && visited++ < 300) {
        val node = pending.removeFirst()
        if (node.className?.toString()?.let { "WebView" in it || "GeckoView" in it } == true) continue
        val rank = addressIds.indexOf(node.viewIdResourceName?.substringAfterLast('/'))
        if (rank >= 0 && node.isVisibleToUser) {
            if (node.isFocused) return node
            if (rank < bestRank) { best = node; bestRank = rank }
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let(pending::addLast)
    }
    return best
}

/** A URL bar read from the changed node, or from one of its four parents. */
data class NearbyAddress(val inBar: Boolean, val host: String?)

/**
 * Reads the address when this node is the URL bar or a close child of it.
 * Returns [NearbyAddress.inBar] false when the change is somewhere else.
 * A focused bar returns no host, because the user is still editing it.
 */
fun AccessibilityNodeInfo.nearbyAddress(): NearbyAddress {
    var current = this
    var recycleCurrent = false
    var steps = 0
    try {
        while (steps++ < 5) {
            val id = current.viewIdResourceName?.substringAfterLast('/')
            if (id != null && id in addressIds) {
                if (current.isFocused) return NearbyAddress(inBar = true, host = null)
                val text = current.text?.toString().orEmpty()
                val address = if (text.isBlank() && id == "ADDRESSBAR_URL_BOX") {
                    current.contentDescription?.toString()?.trim()?.substringBefore(' ')?.trimEnd('.').orEmpty()
                } else text
                return NearbyAddress(inBar = true, host = hostOf(address))
            }
            val parent = current.parent ?: return NearbyAddress(inBar = false, host = null)
            if (recycleCurrent) current.recycle()
            current = parent
            recycleCurrent = true
        }
        return NearbyAddress(inBar = false, host = null)
    } finally {
        if (recycleCurrent) current.recycle()
    }
}

/** True when this node is a scrollable list and not a web page. */
fun AccessibilityNodeInfo.inAppList(): Boolean {
    var current = this
    var recycleCurrent = false
    var steps = 0
    try {
        while (steps++ < 8) {
            val type = current.className?.toString().orEmpty()
            if ("WebView" in type || "GeckoView" in type) return false
            if (current.isScrollable || current.collectionInfo != null) return true
            val parent = current.parent ?: return false
            if (recycleCurrent) current.recycle()
            current = parent
            recycleCurrent = true
        }
        return false
    } finally {
        if (recycleCurrent) current.recycle()
    }
}

fun AccessibilityNodeInfo.browserHost(): String? {
    val bar = browserAddressBar() ?: return null
    // Editing is a safe exit from a block. Never act on a half-typed address.
    if (bar.isFocused) return null
    val text = bar.text?.toString().orEmpty()
    // Firefox's Compose toolbar exposes the address before its action label in the description.
    val address = if (text.isBlank() && bar.viewIdResourceName?.substringAfterLast('/') == "ADDRESSBAR_URL_BOX") {
        bar.contentDescription?.toString()?.trim()?.substringBefore(' ')?.trimEnd('.').orEmpty()
    } else text
    return hostOf(address)
}

/** Replace the current page with a blank page. This preserves other tabs and the browser task. */
fun AccessibilityNodeInfo.clearBrowserPage(tap: (AccessibilityNodeInfo) -> Unit): Boolean {
    val bar = browserAddressBar() ?: return false
    if (!bar.isFocused) {
        var control: AccessibilityNodeInfo? = bar
        repeat(4) {
            if (control?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true) return false
            control = control?.parent
        }
        // Some Compose toolbars expose bounds but omit ACTION_CLICK.
        tap(bar)
        return false // Wait for the editable toolbar to replace the display control.
    }
    if (bar.text?.toString() != "about:blank") {
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, "about:blank") }
        if (!bar.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) return false
    }
    if (Build.VERSION.SDK_INT >= 30 &&
        bar.performAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_IME_ENTER.id)) return true
    // Android 9 and 10 cannot submit an IME action. Use the browser's native URL suggestion.
    val pkg = packageName ?: return false
    val suggestions = listOf("omnibox_suggestions_dropdown", "omnibox_results_container")
        .flatMap { findAccessibilityNodeInfosByViewId("$pkg:id/$it") }
    return suggestions.any { list ->
        list.findAccessibilityNodeInfosByText("about:blank").any suggestion@ { node ->
            if (!node.isVisibleToUser || node.text?.toString() != "about:blank") return@suggestion false
            var control: AccessibilityNodeInfo? = node
            var clicked = false
            repeat(4) {
                if (!clicked) clicked = control?.performAction(AccessibilityNodeInfo.ACTION_CLICK) == true
                control = control?.parent
            }
            clicked
        }
    }
}

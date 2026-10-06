package dev.agneswd.ronumi.e2e

import android.accessibilityservice.AccessibilityServiceInfo
import android.app.Instrumentation
import android.app.UiAutomation
import android.os.Bundle
import android.graphics.Rect
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo
import java.io.StringWriter

/** Reads the actual accessibility tree without requiring animated screens to become idle. */
class E2eDriver : Instrumentation() {
    private var notificationTitle: String? = null

    override fun onCreate(arguments: Bundle?) {
        notificationTitle = arguments?.getString("notificationTitle")
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        notificationTitle?.let { title ->
            val manager = targetContext.getSystemService(android.app.NotificationManager::class.java)
            manager.createNotificationChannel(android.app.NotificationChannel("fixture", "Test messages", android.app.NotificationManager.IMPORTANCE_DEFAULT))
            manager.notify(1, android.app.Notification.Builder(targetContext, "fixture")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle(title).setContentText("Device test message").build())
            finish(0, Bundle())
            return
        }
        val writer = StringWriter()
        val xml = Xml.newSerializer()
        xml.setOutput(writer)
        xml.startDocument("UTF-8", true)
        xml.startTag(null, "hierarchy")
        // Reading the screen must not stop the guard that the test is checking.
        val automation = getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS or
                AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        val root = (1..20).firstNotNullOfOrNull {
            // After a process restart, the active root can lag behind the focused application window.
            automation.rootInActiveWindow ?: automation.windows
                .filter { it.type == AccessibilityWindowInfo.TYPE_APPLICATION && (it.isFocused || it.isActive) }
                .sortedByDescending { it.isFocused }
                .firstNotNullOfOrNull { it.root }
                ?: run { Thread.sleep(100); null }
        }
        fun node(info: AccessibilityNodeInfo) {
            val bounds = Rect().also(info::getBoundsInScreen)
            xml.startTag(null, "node")
            xml.attribute(null, "text", info.text?.toString().orEmpty())
            xml.attribute(null, "content-desc", info.contentDescription?.toString().orEmpty())
            xml.attribute(null, "bounds", "[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]")
            xml.attribute(null, "clickable", info.isClickable.toString())
            xml.attribute(null, "enabled", info.isEnabled.toString())
            xml.attribute(null, "visible", info.isVisibleToUser.toString())
            xml.attribute(null, "class", info.className?.toString().orEmpty())
            xml.attribute(null, "resource-id", info.viewIdResourceName.orEmpty())
            xml.attribute(null, "selected", info.isSelected.toString())
            for (i in 0 until info.childCount) info.getChild(i)?.let(::node)
            xml.endTag(null, "node")
        }
        root?.let(::node)
        xml.endTag(null, "hierarchy")
        xml.endDocument()
        finish(if (root == null) 1 else 0, Bundle().apply { putString("hierarchy", writer.toString()) })
    }
}

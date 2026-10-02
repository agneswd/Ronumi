package dev.agneswd.stillpoint.e2e

import android.app.Instrumentation
import android.os.Bundle
import android.graphics.Rect
import android.util.Xml
import android.view.accessibility.AccessibilityNodeInfo
import java.io.StringWriter

/** Reads the actual accessibility tree without requiring animated screens to become idle. */
class E2eDriver : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val writer = StringWriter()
        val xml = Xml.newSerializer()
        xml.setOutput(writer)
        xml.startDocument("UTF-8", true)
        xml.startTag(null, "hierarchy")
        val root = (1..10).firstNotNullOfOrNull {
            uiAutomation.rootInActiveWindow ?: run { Thread.sleep(100); null }
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
            for (i in 0 until info.childCount) info.getChild(i)?.let(::node)
            xml.endTag(null, "node")
        }
        root?.let(::node)
        xml.endTag(null, "hierarchy")
        xml.endDocument()
        finish(if (root == null) 1 else 0, Bundle().apply { putString("hierarchy", writer.toString()) })
    }
}

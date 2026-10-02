package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import dev.agneswd.stillpoint.app
import dev.agneswd.stillpoint.ui.design.Sp

/** A compact summary of the selected apps. The containing row opens the picker. */
@Composable
fun AppSelectionPreview(packages: Set<String>, maxVisible: Int = 3) {
    val catalog = LocalContext.current.app.catalog
    val visible = remember(packages, maxVisible) { packages.sorted().take(maxVisible.coerceIn(0, 3)) }
    val names = remember(visible) { visible.map { catalog.label(it) } }
    val remaining = packages.size - visible.size
    val description = (names + if (remaining > 0) listOf("$remaining more apps") else emptyList()).joinToString(", ")
    Row(
        modifier = Modifier.clearAndSetSemantics { if (description.isNotEmpty()) contentDescription = description },
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        visible.forEach { AppIcon(it, size = 24.dp) }
        if (remaining > 0) Text("+$remaining", style = MaterialTheme.typography.labelSmall, color = Sp.colors.textDim)
        Chevron()
    }
}

package dev.agneswd.stillpoint.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

private class FocusedField {
    var token: Any? = null
    var bounds: Rect? = null
    var hostPosition = Offset.Zero

    fun remove(owner: Any) {
        if (token === owner) {
            token = null
            bounds = null
        }
    }
}

private val LocalFocusedField = staticCompositionLocalOf<FocusedField?> { null }

/** Observes taps before buttons consume them. The original click still reaches its target. */
@Composable
fun KeyboardDismissHost(content: @Composable () -> Unit) {
    val field = remember { FocusedField() }
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    CompositionLocalProvider(LocalFocusedField provides field) {
        Box(
            Modifier
                .onGloballyPositioned { field.hostPosition = it.positionInWindow() }
                .pointerInput(focusManager, keyboard) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val bounds = field.bounds
                        if (bounds != null && !bounds.contains(down.position + field.hostPosition)) {
                            focusManager.clearFocus()
                            keyboard?.hide()
                        }
                    }
                },
        ) { content() }
    }
}

/** Registers only the focused text field, so taps inside it retain cursor and selection behavior. */
fun Modifier.trackTextFieldFocus(): Modifier = composed {
    val field = LocalFocusedField.current
    if (field == null) return@composed this
    val token = remember { Any() }
    val measured = remember { arrayOfNulls<Rect>(1) }
    DisposableEffect(field, token) {
        onDispose { field.remove(token) }
    }
    this
        .onGloballyPositioned {
            measured[0] = it.boundsInWindow()
            if (field.token === token) field.bounds = measured[0]
        }
        .onFocusChanged {
            if (it.isFocused) {
                field.token = token
                field.bounds = measured[0]
            } else {
                field.remove(token)
            }
        }
}

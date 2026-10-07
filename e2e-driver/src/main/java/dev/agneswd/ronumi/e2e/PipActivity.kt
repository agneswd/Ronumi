package dev.agneswd.ronumi.e2e

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.os.Bundle
import android.util.Rational
import android.view.View
import android.graphics.Color

/** A second app window. Picture-in-picture close must not dismiss this window. */
class PipActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(View(this).apply { setBackgroundColor(Color.rgb(40, 90, 70)) })
    }

    override fun onResume() {
        super.onResume()
        enterPip()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enterPip()
    }

    /** Enters picture-in-picture after the window exists. Retries once when Android refuses the first call. */
    private fun enterPip() {
        if (Build.VERSION.SDK_INT < 26 || isInPictureInPictureMode) return
        window.decorView.post {
            if (isInPictureInPictureMode) return@post
            val params = PictureInPictureParams.Builder().setAspectRatio(Rational(9, 16)).build()
            val entered = runCatching { enterPictureInPictureMode(params) }.getOrDefault(false)
            if (entered || isInPictureInPictureMode) return@post
            window.decorView.postDelayed({
                if (!isInPictureInPictureMode) runCatching { enterPictureInPictureMode(params) }
            }, 400)
        }
    }
}

package com.politex.widget

import android.content.Context
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.widget.RemoteViews
import com.politex.R

object WidgetConfetti {

    private const val TOTAL_FRAMES = 16
    private const val FRAME_MS = 90L

    @Volatile
    private var currentBitmap: Bitmap? = null

    private val handler = Handler(Looper.getMainLooper())
    private var playing = false

    fun applyFrame(views: RemoteViews) {
        val bmp = currentBitmap
        if (bmp == null) {
            views.setViewVisibility(R.id.iv_confetti_overlay, android.view.View.GONE)
        } else {
            views.setViewVisibility(R.id.iv_confetti_overlay, android.view.View.VISIBLE)
            views.setImageViewBitmap(R.id.iv_confetti_overlay, bmp)
        }
    }

    fun play(context: Context) {
        if (playing) return
        playing = true

        var frame = 0
        fun step() {
            currentBitmap = if (frame < TOTAL_FRAMES) {
                com.politex.util.ConfettiRenderer.drawFrame(420, 300, frame, TOTAL_FRAMES)
            } else {
                null
            }
            WidgetUpdateHelper.updateAllWidgets(context)
            frame++
            if (frame <= TOTAL_FRAMES) {
                handler.postDelayed(::step, FRAME_MS)
            } else {
                playing = false
            }
        }
        step()
    }
}

package com.digitalsix.YouSafe.face

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import kotlin.math.min

class FaceGuideView(context: Context) : View(context) {
    val radius get() = min(width, height) * 0.40f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    var aligned = false
        set(value) { if (field != value) { field = value; invalidate() } }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f; val cy = height / 2f
        val mask = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
            addCircle(cx, cy, radius, Path.Direction.CW)
        }
        paint.style = Paint.Style.FILL; paint.color = 0x99000000.toInt()
        canvas.drawPath(mask, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 3 * resources.displayMetrics.density
        paint.color = if (aligned) Color.rgb(76, 220, 145) else Color.WHITE
        canvas.drawCircle(cx, cy, radius, paint)
    }
}

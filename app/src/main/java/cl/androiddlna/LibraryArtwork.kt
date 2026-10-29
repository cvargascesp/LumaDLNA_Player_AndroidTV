package cl.androiddlna

import android.graphics.*
import android.graphics.drawable.Drawable

/** Lightweight local artwork for items without a server-provided cover. */
internal class LibraryArtwork(private val kind: String) : Drawable() {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun draw(canvas: Canvas) {
        val w = bounds.width().toFloat(); val h = bounds.height().toFloat()
        canvas.save(); canvas.translate(bounds.left.toFloat(), bounds.top.toFloat())
        val server = kind == "▣"; val folder = kind == "▰" || kind == "★"
        val accent = if (server) Color.rgb(138,171,255) else if (folder) Color.rgb(107,227,201) else Color.rgb(195,159,255)
        paint.shader = LinearGradient(0f,0f,w,h, if (server) 0xff263b64.toInt() else 0xff173f43.toInt(),0xff111c2c.toInt(),Shader.TileMode.CLAMP)
        canvas.drawRect(0f,0f,w,h,paint); paint.shader = null
        paint.color = accent; paint.alpha = 12
        canvas.drawCircle(w*.85f,h*.2f,h*.95f,paint); canvas.drawCircle(w*.85f,h*.2f,h*.65f,paint)
        paint.alpha = 255; paint.color = accent; paint.style = Paint.Style.STROKE; paint.strokeWidth = h*.022f
        val x = w*.15f; val y = h*.3f; val s = h*.43f
        if (server) {
            repeat(2) { i ->
                val top = y + i*s*.58f
                canvas.drawRoundRect(x,top,x+s*1.35f,top+s*.42f,s*.08f,s*.08f,paint)
                paint.style = Paint.Style.FILL; canvas.drawCircle(x+s*.2f,top+s*.21f,s*.04f,paint); paint.style = Paint.Style.STROKE
                canvas.drawLine(x+s*.65f,top+s*.21f,x+s*1.12f,top+s*.21f,paint)
            }
        } else if (folder) {
            val path = Path().apply { moveTo(x,y+s*.15f); lineTo(x+s*.48f,y+s*.15f); lineTo(x+s*.65f,y+s*.33f); lineTo(x+s*1.35f,y+s*.33f); lineTo(x+s*1.35f,y+s); lineTo(x,y+s); close() }
            canvas.drawPath(path,paint)
        } else {
            canvas.drawRoundRect(x,y,x+s*1.4f,y+s,s*.1f,s*.1f,paint)
            paint.style = Paint.Style.FILL
            canvas.drawPath(Path().apply { moveTo(x+s*.57f,y+s*.28f); lineTo(x+s*.98f,y+s*.5f); lineTo(x+s*.57f,y+s*.72f); close() },paint)
        }
        paint.style = Paint.Style.FILL
        canvas.restore()
    }
    override fun setAlpha(alpha: Int) { paint.alpha = alpha }
    override fun setColorFilter(colorFilter: ColorFilter?) { paint.colorFilter = colorFilter }
    @Deprecated("Deprecated in Android") override fun getOpacity() = PixelFormat.TRANSLUCENT
}

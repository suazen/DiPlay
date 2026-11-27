package com.shilapi.xcertplay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import android.view.View
import androidx.core.content.ContextCompat
import com.shilapi.xcertplay.airplay.CarPlayClusterDisplay
import com.shilapi.xcertplay.host.R
import com.shilapi.xcertplay.airplay.ClusterTurnCardOverlay
import com.shilapi.xcertplay.hud.ClusterTurnGuidance
import com.shilapi.xcertplay.iap2.message.LaneGuidanceLane

/**
 * Instruction card drawn by DiPlay on top of the dashboard map.
 *
 * Visual language follows Apple's turn banners: a dark glass capsule with a hairline stroke,
 * the maneuver glyph in a soft chip on the left, distance and road stacked on the right.
 * Maneuver glyphs are Material Symbols (Apache 2.0), tinted the system blue; the roundabout
 * exit number sits in a small badge on the glyph.
 */
internal class ClusterTurnCardView(context: Context) : View(context) {
    /** Two visual languages: DiPlay's glass banner and the stock BYD simple-navigation block. */
    enum class Style { GLASS, BYD_SIMPLE }

    private var style = Style.GLASS
    private var guidance: ClusterTurnGuidance? = null
    private var lanes: List<LaneGuidanceLane> = emptyList()
    private var night = false
    private var xPercent = ClusterTurnCardOverlay.DEFAULT_X_PERCENT
    private var yPercent = ClusterTurnCardOverlay.DEFAULT_Y_PERCENT
    private var sizePercent = ClusterTurnCardOverlay.DEFAULT_SIZE_PERCENT

    private val accent = Color.rgb(10, 132, 255)
    /** Card opacity as percent; 100 keeps the historical fully-opaque look available. */
    private var opacityPercent = 85
    private val glassPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }

    private val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val badgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; textAlign = Paint.Align.CENTER
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val distancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE; typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val roadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(179, 199, 199, 204); typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.argb(224, 235, 235, 240); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val rect = RectF()
    private var glyph: Drawable? = null
    private var glyphTag: Int = -1
    private val bydPrimary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    private val bydSecondary = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    }
    private val bydDivider = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 2f
    }
    private var bydGlyph: Drawable? = null
    private var bydGlyphTag: Int = -1

    init { applyPalette() }

    fun setLayout(xPercent: Int, yPercent: Int, sizePercent: Int) {
        if (this.xPercent == xPercent && this.yPercent == yPercent && this.sizePercent == sizePercent) return
        this.xPercent = xPercent
        this.yPercent = yPercent
        this.sizePercent = sizePercent
        invalidate()
    }

    /** Percent 20..100 of the card's background alpha; text stays readable at any value. */
    fun setOpacity(percent: Int) {
        val next = percent.coerceIn(20, 100)
        if (next == opacityPercent) return
        opacityPercent = next
        applyPalette()
        invalidate()
    }

    /** Day: dark glass on a light map; night: lighter translucent panel for the dark map. */
    fun setNightMode(night: Boolean) {
        if (night == this.night) return
        this.night = night
        applyPalette()
        invalidate()
    }

    fun setLanes(next: List<LaneGuidanceLane>) {
        if (lanes == next) return
        lanes = next
        invalidate()
    }

    fun setStyle(next: Style) {
        if (style == next) return
        style = next
        bydGlyphTag = -1
        applyPalette()
        invalidate()
    }

    private fun applyPalette() {
        if (night) {
            bydPrimary.color = Color.rgb(242, 242, 247)
            bydSecondary.color = Color.rgb(176, 176, 184)
            bydDivider.color = Color.argb(72, 255, 255, 255)
        } else {
            bydPrimary.color = Color.rgb(17, 17, 17)
            bydSecondary.color = Color.rgb(107, 107, 107)
            bydDivider.color = Color.argb(72, 0, 0, 0)
        }
        bydGlyphTag = -1
        val alpha = (opacityPercent * 255 / 100).coerceIn(51, 255)
        if (night) {
            glassPaint.color = Color.argb(alpha, 12, 14, 18)
            chipPaint.color = Color.argb((alpha * 0.18f).toInt().coerceAtLeast(12), 255, 255, 255)
            strokePaint.color = Color.argb((alpha * 0.16f).toInt().coerceAtLeast(10), 255, 255, 255)
            distancePaint.color = Color.argb(255, 240, 242, 246)
            roadPaint.color = Color.argb(179, 176, 182, 192)
            infoPaint.color = Color.argb(224, 226, 228, 236)
            badgePaint.color = accent
        } else {
            glassPaint.color = Color.argb(alpha, 28, 28, 30)
            chipPaint.color = Color.argb((alpha * 0.18f).toInt().coerceAtLeast(12), 255, 255, 255)
            strokePaint.color = Color.argb((alpha * 0.15f).toInt().coerceAtLeast(10), 255, 255, 255)
            distancePaint.color = Color.WHITE
            roadPaint.color = Color.argb(179, 199, 199, 204)
            infoPaint.color = Color.argb(224, 235, 235, 240)
            badgePaint.color = accent
        }
    }

    fun setGuidance(next: ClusterTurnGuidance?) {
        val wasVisible = visibility == VISIBLE
        if (guidance == next) {
            visibility = if (next == null) GONE else VISIBLE
            return
        }
        guidance = next
        visibility = if (next == null) GONE else VISIBLE
        // Only raising the card when it becomes visible avoids a relayout on every guidance update,
        // which made the transparent cluster window redraw and appear to flicker.
        if (next != null && !wasVisible) bringToFront()
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(
            MeasureSpec.getSize(widthMeasureSpec).coerceAtLeast(1),
            MeasureSpec.getSize(heightMeasureSpec).coerceAtLeast(1),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val next = guidance ?: return
        if (style == Style.BYD_SIMPLE) {
            drawBydSimple(canvas, next)
            return
        }
        val card = ClusterTurnCardOverlay.card(width, height, xPercent, yPercent, sizePercent)
        val h = card.height.toFloat()
        val w = card.width.toFloat()

        val radius = h * 0.30f
        rect.set(card.left.toFloat(), card.top.toFloat(), card.left + w, card.top + h)
        canvas.drawRoundRect(rect, radius, radius, glassPaint)
        canvas.drawRoundRect(rect, radius, radius, strokePaint)

        val chip = h * 0.76f
        val chipLeft = card.left + h * 0.12f
        val chipTop = card.top + (h - chip) / 2f
        rect.set(chipLeft, chipTop, chipLeft + chip, chipTop + chip)
        canvas.drawRoundRect(rect, chip * 0.26f, chip * 0.26f, chipPaint)

        val exit = next.roundaboutExit.takeIf { it in 1..9 }
        drawGlyph(canvas, next, chipLeft, chipTop, chip, exit)

        val textLeft = chipLeft + chip + h * 0.14f
        val textWidth = card.left + w - h * 0.10f - textLeft
        if (textWidth <= 0f) return
        distancePaint.textSize = h * 0.30f
        roadPaint.textSize = h * 0.17f
        canvas.drawText(
            ellipsize(distanceLabel(next.distanceMeters), textWidth, distancePaint),
            textLeft, card.top + h * 0.44f, distancePaint,
        )
        val road = roadLabel(next)
        if (road.isNotEmpty()) {
            canvas.drawText(
                ellipsize(road, textWidth, roadPaint),
                textLeft, card.top + h * 0.72f, roadPaint,
            )
        }
        drawInfoStrip(canvas, next, card.left, (card.top + h).toInt(), w.toInt(), h.toInt())
    }

    /** The arrival/duration/distance pill that hangs under the card, like the stock nav bar. */
    private fun drawInfoStrip(canvas: Canvas, next: ClusterTurnGuidance, left: Int, belowTop: Int, width: Int, cardHeight: Int) {
        val parts = infoParts(next)
        if (parts.isEmpty()) return
        val h = (cardHeight * 0.40f).coerceAtLeast(34f)
        val gap = cardHeight * 0.05f
        val top = (belowTop + gap).coerceAtMost(height - h)
        infoPaint.textSize = h * 0.42f
        val separator = "   ·   "
        val text = parts.joinToString(separator)
        val textWidth = paintMeasure(text)
        // Wider than the card is fine: the strip centers on the card and may reach the panel edges.
        // left is an absolute panel coordinate, so every clamp below uses the panel width, never
        // the card width — clamping against the card width is what threw the strip to panel-left.
        val maxStrip = this.width - (dp(8f) * 2).toInt()
        val stripWidth = maxOf(width, (textWidth + h * 0.9f).toInt()).coerceAtMost(maxStrip)
        val stripLeft = (left + width / 2f - stripWidth / 2f).toInt()
            .coerceIn(4, (this.width - stripWidth - 4).coerceAtLeast(4))
        rect.set(stripLeft.toFloat(), top.toFloat(), (stripLeft + stripWidth).toFloat(), top + h)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, glassPaint)
        canvas.drawRoundRect(rect, h / 2f, h / 2f, strokePaint)
        val draw = if (textWidth <= stripWidth - h * 0.6f) text else ellipsize(text, stripWidth - h * 0.6f, infoPaint)
        val drawWidth = paintMeasure(draw)
        val centerX = stripLeft + stripWidth / 2f
        canvas.drawText(draw, centerX - drawWidth / 2f, top + h * 0.69f, infoPaint)
    }

    private fun paintMeasure(text: String): Float = infoPaint.measureText(text)

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun infoParts(next: ClusterTurnGuidance): List<String> {
        val parts = mutableListOf<String>()
        next.arrivalEpochSeconds?.takeIf { it > 0 }?.let { epoch ->
            val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(epoch * 1000L))
            parts += context.getString(R.string.turn_card_info_arrival, time)
        }
        next.remainingSeconds?.takeIf { it > 0 }?.let { total ->
            val hours = total / 3600
            val minutes = (total % 3600) / 60
            parts += if (hours > 0) {
                context.getString(R.string.turn_card_info_duration_hm, hours.toInt(), minutes.toInt())
            } else {
                context.getString(R.string.turn_card_info_duration_m, minutes.toInt())
            }
        }
        next.remainingMeters?.takeIf { it > 0 }?.let { meters ->
            parts += context.getString(R.string.turn_card_info_km, meters / 1000.0)
        }
        return parts
    }

    /** Draws the tinted Material Symbols glyph; the roundabout exit number gets a corner badge. */
    private fun drawGlyph(canvas: Canvas, next: ClusterTurnGuidance, left: Float, top: Float, side: Float, exit: Int?) {
        if (next.icon == 0) return
        val resId = glyphRes(next.icon)
        if (resId != glyphTag) {
            glyph = ContextCompat.getDrawable(context, resId)?.mutate()?.apply { setTint(accent) }
            glyphTag = resId
        }
        val inset = side * 0.10f
        glyph?.setBounds(
            (left + inset).toInt(), (top + inset).toInt(),
            (left + side - inset).toInt(), (top + side - inset).toInt(),
        )
        glyph?.draw(canvas)
        if (exit != null) {
            val d = side * 0.42f
            val cx = left + side - d / 2f
            val cy = top + side - d / 2f
            canvas.drawCircle(cx, cy, d / 2f, badgePaint)
            badgeTextPaint.textSize = d * 0.62f
            val textY = cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f
            canvas.drawText(exit.toString(), cx, textY, badgeTextPaint)
        }
    }

    private fun glyphRes(icon: Int): Int = when (icon) {
        2 -> R.drawable.ic_turn_card_turn_left
        3 -> R.drawable.ic_turn_card_turn_right
        4 -> R.drawable.ic_turn_card_turn_slight_left
        5 -> R.drawable.ic_turn_card_turn_slight_right
        6 -> R.drawable.ic_turn_card_turn_sharp_left
        7 -> R.drawable.ic_turn_card_turn_sharp_right
        8 -> R.drawable.ic_turn_card_u_turn_left
        19 -> R.drawable.ic_turn_card_u_turn_right
        11, 12, 17, 18 -> R.drawable.ic_turn_card_roundabout_right
        15 -> R.drawable.ic_turn_card_sports_score
        else -> R.drawable.ic_turn_card_straight
    }

    private fun distanceLabel(meters: Int): String = when {
        meters <= 20 -> context.getString(R.string.turn_card_now)
        meters < 1000 -> context.getString(R.string.turn_card_distance_m, meters)
        else -> context.getString(
            R.string.turn_card_distance_km, meters / 100 / 10f,
        )
    }

    private fun roadLabel(next: ClusterTurnGuidance): String =
        if (next.roundaboutExit in 1..9) {
            context.getString(R.string.turn_card_exit, next.roundaboutExit)
        } else {
            next.road
        }

    /** Stock BYD simple-navigation block: transparent background, large dark glyph, title/road, hairline and trip line. */
    private fun drawBydSimple(canvas: Canvas, next: ClusterTurnGuidance) {
        val panelW = width.toFloat()
        val panelH = height.toFloat()
        val right = panelW * 0.945f
        val left = panelW * 0.745f
        // The trip line and divider keep their panel position; only the maneuver block and lane strip
        // move up when lanes appear, so the trip line never lands on the lane arrows.
        val baseCenterY = panelH * 0.450f
        val centerY = baseCenterY - (if (lanes.isNotEmpty()) panelH * 0.130f else 0f)
        val arrow = panelH * 0.118f
        val gap = panelH * 0.070f
        val titleSize = panelH * 0.076f
        val roadSize = panelH * 0.034f
        val infoSize = panelH * 0.039f

        drawBydGlyph(canvas, next, left, centerY - panelH * 0.110f, arrow)
        val textLeft = left + arrow + gap

        bydPrimary.textSize = titleSize
        canvas.drawText(distanceLabel(next.distanceMeters), textLeft, centerY - panelH * 0.020f, bydPrimary)

        val road = roadLabel(next)
        if (road.isNotEmpty()) {
            bydSecondary.textSize = roadSize
            canvas.drawText(
                ellipsize(road, right - textLeft, bydSecondary),
                textLeft, centerY + panelH * 0.022f, bydSecondary,
            )
        }

        val dividerY = baseCenterY + panelH * 0.043f
        canvas.drawLine(left, dividerY, right, dividerY, bydDivider)
        if (lanes.isNotEmpty()) drawBydLanes(canvas, dividerY)

        val info = bydInfoLine(next)
        if (info.isNotEmpty()) {
            bydPrimary.textSize = infoSize
            canvas.drawText(info, left, baseCenterY + panelH * 0.100f, bydPrimary)
        }
    }

    private fun bydInfoLine(next: ClusterTurnGuidance): String {
        val parts = mutableListOf<String>()
        next.remainingMeters?.takeIf { it > 0 }?.let { meters ->
            parts += if (meters >= 1000) "${meters / 1000} 公里" else "$meters 米"
        }
        val eta = next.arrivalEpochSeconds?.takeIf { it > 0 }
            ?: next.remainingSeconds?.takeIf { it > 0 }?.let { System.currentTimeMillis() / 1000 + it }
        if (eta != null) {
            parts += SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(eta * 1000L)) + " 到"
        }
        return parts.joinToString("     ")
    }

    /** One arrow per lane in the strip, above the divider; the recommended lane is DiPlay blue. */
    private fun drawBydLanes(canvas: Canvas, dividerY: Float) {
        val panelW = width.toFloat()
        val panelH = height.toFloat()
        val side = panelH * 0.078f
        val gap = panelH * 0.018f
        val total = lanes.size * side + (lanes.size - 1) * gap
        val left = panelW * 0.745f
        val right = panelW * 0.945f
        val startX = (left + right) / 2f - total / 2f
        val top = dividerY - panelH * 0.045f - side
        lanes.forEachIndexed { index, lane ->
            val tint = if (lane.recommended) accent else bydPrimary.color
            val arrow = ContextCompat.getDrawable(context, R.drawable.ic_turn_card_straight)
                ?.mutate()?.apply { setTint(tint) }
            // The phone sends the lane's own angle plus, for the recommended lane, the direction to
            // take (90 = right in the captured sample); draw the highlighted direction when present.
            val angle = if (lane.recommended) lane.highlightAngle ?: lane.angle else lane.angle
            val x = startX + index * (side + gap)
            canvas.save()
            canvas.rotate(angle.toFloat(), x + side / 2f, top + side / 2f)
            arrow?.setBounds(x.toInt(), top.toInt(), (x + side).toInt(), (top + side).toInt())
            arrow?.draw(canvas)
            canvas.restore()
        }
    }

    private fun drawBydGlyph(canvas: Canvas, next: ClusterTurnGuidance, left: Float, top: Float, side: Float) {
        if (next.icon == 0) return
        val resId = glyphRes(next.icon)
        if (resId != bydGlyphTag) {
            bydGlyph = ContextCompat.getDrawable(context, resId)?.mutate()?.apply { setTint(bydPrimary.color) }
            bydGlyphTag = resId
        }
        bydGlyph?.setBounds(left.toInt(), top.toInt(), (left + side).toInt(), (top + side).toInt())
        bydGlyph?.draw(canvas)
        val exit = next.roundaboutExit.takeIf { it in 1..9 }
        if (exit != null) {
            val d = side * 0.42f
            val cx = left + side - d / 2f
            val cy = top + side - d / 2f
            badgePaint.color = bydPrimary.color
            canvas.drawCircle(cx, cy, d / 2f, badgePaint)
            canvas.drawText(exit.toString(), cx,
                cy - (badgeTextPaint.descent() + badgeTextPaint.ascent()) / 2f,
                badgeTextPaint.apply { color = if (night) Color.rgb(17, 17, 17) else Color.WHITE; textSize = d * 0.62f })
        }
    }

    private fun ellipsize(text: String, maxWidth: Float, paint: Paint): String {
        if (paint.measureText(text) <= maxWidth) return text
        val ellipsis = "…"
        var end = text.length
        while (end > 0 && paint.measureText(text.take(end) + ellipsis) > maxWidth) end--
        return if (end == 0) ellipsis else text.take(end) + ellipsis
    }
}

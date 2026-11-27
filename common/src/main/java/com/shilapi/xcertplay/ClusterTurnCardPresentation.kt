package com.shilapi.xcertplay

import android.app.Presentation
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Display
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import com.shilapi.xcertplay.hud.ClusterTurnGuidance

/**
 * A floating instruction card on the cluster projection display.
 *
 * The window covers the whole projection panel but is transparent and never takes touch, so only
 * the card itself is visible; the cluster keeps its own gauges and SR view. The card uses the same
 * panel percentages as [ClusterTurnCardView] on the dashboard map, which places it in the stock
 * simple-navigation column on the right. Shown only while a maneuver is active.
 */
internal class ClusterTurnCardPresentation(context: Context, display: Display) : Presentation(context, display) {
    private var card: ClusterTurnCardView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        window?.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window?.addFlags(
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        )
        val root = FrameLayout(context).apply { setBackgroundColor(Color.TRANSPARENT) }
        card = ClusterTurnCardView(context).apply {
            visibility = View.GONE
            setStyle(ClusterTurnCardView.Style.BYD_SIMPLE)
        }
        root.addView(card, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
    }

    fun setTurnCardOverlay(xPercent: Int, yPercent: Int, sizePercent: Int) =
        card?.setLayout(xPercent, yPercent, sizePercent)

    fun setTurnCardOpacity(percent: Int) = card?.setOpacity(percent)

    fun setTurnCardNightMode(night: Boolean) = card?.setNightMode(night)

    fun setTurnCardLanes(lanes: List<com.shilapi.xcertplay.iap2.message.LaneGuidanceLane>) =
        card?.setLanes(lanes)

    fun setTurnCardGuidance(guidance: ClusterTurnGuidance?) = card?.setGuidance(guidance)
}

package com.wormx.app.ui

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout

/**
 * Forces height to equal width regardless of what the parent LayoutManager
 * hands it. GridLayoutManager cells given a plain match_parent height can
 * end up stretched to the full RecyclerView height instead of a square
 * thumbnail — this is what made the Vault grid show tall vertical strips
 * instead of a 3-column photo grid. Using this as the item root sidesteps
 * that measurement quirk entirely.
 */
class SquareFrameLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, widthMeasureSpec)
    }
}

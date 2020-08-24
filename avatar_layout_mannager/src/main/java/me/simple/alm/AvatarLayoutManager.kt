package me.simple.alm

import android.util.Log
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

/**
 * @param orientation 支持的方向
 */
class AvatarLayoutManager @JvmOverloads constructor(
    private val orientation: Int = HORIZONTAL,
    private val reverseLayout: Boolean = false,
    private val offset: Int = 1,
    private val changeDrawingOrder: Boolean = false
) : RecyclerView.LayoutManager() {

    companion object {
        const val HORIZONTAL = LinearLayoutManager.HORIZONTAL
        const val VERTICAL = LinearLayoutManager.VERTICAL

        const val FILL_START = -1
        const val FILL_END = 1
    }

    private var mPendingPosition: Int = RecyclerView.NO_POSITION
    private var mCurrentPosition: Int = 0
    private var mAvailable: Int = 0

    private var mItemFillDirection: Int = FILL_END

    private var mFillAnchor: Int = 0

    private val mOutChildren = hashSetOf<View>()

    override fun generateDefaultLayoutParams(): RecyclerView.LayoutParams {
        return RecyclerView.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    override fun isAutoMeasureEnabled(): Boolean {
        return true
    }

    override fun onLayoutChildren(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ) {
        if (state.itemCount == 0) {
            removeAndRecycleAllViews(recycler)
            return
        }

//        logDebug("state - $state")

        if (state.isPreLayout) return

//        mCurrentPosition = if (mPendingPosition != RecyclerView.NO_POSITION) {
//            mPendingPosition
//        } else {
//            0
//        }
        mCurrentPosition = 0

        detachAndScrapAttachedViews(recycler)

        fillLayout(recycler, state)

        logChildren(recycler)
    }

    override fun onLayoutCompleted(state: RecyclerView.State) {
        mPendingPosition = RecyclerView.NO_POSITION
    }

    override fun canScrollHorizontally() = orientation == HORIZONTAL

    override fun canScrollVertically() = orientation == VERTICAL

    override fun scrollHorizontallyBy(
        dx: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (orientation == VERTICAL) return 0

        return scrollBy(dx, recycler, state)
    }

    override fun scrollVerticallyBy(
        dy: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (orientation == HORIZONTAL) return 0

        return scrollBy(dy, recycler, state)
    }

    //delta > 0 向右或者下滑，反之则反
    private fun scrollBy(
        delta: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        if (childCount == 0 || delta == 0) {
            return 0
        }

//        logDebug("delta == $delta")

        val consume = fillScroll(delta, recycler, state)
        offsetChildren(-consume)
        recycleChildren(consume, recycler)

        logChildren(recycler)

        return consume
    }

    override fun scrollToPosition(position: Int) {
        mPendingPosition = position
        requestLayout()
    }

    override fun smoothScrollToPosition(
        recyclerView: RecyclerView,
        state: RecyclerView.State,
        position: Int
    ) {

    }

    //自定义方法

    private fun fill(
        available: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        mItemFillDirection = if (available > 0) FILL_END else FILL_START

        var remainingSpace = abs(available)

        while (remainingSpace > 0 && hasMore(state)) {
            val child = nextView(recycler)
            if (mItemFillDirection == FILL_END) {
                addView(child)
            } else {
                addView(child, 0)
            }
            measureChildWithMargins(child, 0, 0)
            layoutChunk(child)

            logDebug("fill -- ${getPosition(child)}")

            remainingSpace -= getItemSpace(child) / 2
//            logDebug("remainingSpace == $remainingSpace")
        }

        return available
    }

    private fun layoutChunk(
        child: View
    ) {
        var left = 0
        var top = 0
        var right = 0
        var bottom = 0
        if (orientation == HORIZONTAL) {
            if (reverseLayout) {
                right = mFillAnchor
                left = right - getItemWidth(child)
            } else {
                left = mFillAnchor
                right = left + getItemWidth(child)
            }
            top = paddingTop
            bottom = top + getItemHeight(child) - paddingBottom
        } else {
            if (reverseLayout) {
                bottom = mFillAnchor
                top = bottom - getItemHeight(child)
            } else {
                top = mFillAnchor
                bottom = top + getItemHeight(child)
            }
            left = paddingLeft
            right = left + getItemWidth(child) - paddingRight
        }

        layoutDecoratedWithMargins(child, left, top, right, bottom)

        if (orientation == HORIZONTAL) {
            if (reverseLayout) {
                mFillAnchor -= (getItemWidth(child) / 2 + offset)
            } else {
                mFillAnchor += (getItemWidth(child) / 2 + offset)
            }
        } else {
            if (reverseLayout) {
                mFillAnchor -= (getItemHeight(child) / 2 + offset)
            } else {
                mFillAnchor += (getItemHeight(child) / 2 + offset)
            }
        }

    }

    private fun fillLayout(
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ) {
        mFillAnchor = if (orientation == HORIZONTAL) {
            if (reverseLayout) {
                width - paddingRight
            } else {
                paddingLeft
            }
        } else {
            if (reverseLayout) {
                height - paddingBottom
            } else {
                paddingTop
            }
        }
        fill(getTotalSpace(), recycler, state)
    }

    private fun fillScroll(
        delta: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        return if (delta > 0) {
            fillEnd(delta, recycler, state)
        } else {
            fillStart(delta, recycler, state)
        }
    }

    private fun fillEnd(
        delta: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val lastView = getChildAt(childCount - 1)!!
        val lastLeft = getDecoratedLeft(lastView)
        if (lastLeft > getEnd()) {
            return delta
        }

        val lastRight = getDecoratedRight(lastView)
        val lastPosition = getPosition(lastView)
        if (lastPosition == state.itemCount - 1 && lastRight <= getEnd()) {
            return lastRight - getEnd()
        }

        mCurrentPosition = lastPosition + 1
        mFillAnchor = lastRight - getItemWidth(lastView) / 2

        return fill(delta, recycler, state)
    }

    private fun fillStart(
        delta: Int,
        recycler: RecyclerView.Recycler,
        state: RecyclerView.State
    ): Int {
        val firstView = getChildAt(0)!!
        val firstRight = getDecoratedRight(firstView)
        if (firstRight < getStart()) {
            return delta
        }

        val firstLeft = getDecoratedLeft(firstView)
        val firstPosition = getPosition(firstView)
        if (firstPosition == 0 && firstLeft >= getStart()) {
            return firstLeft - getStart()
        }

        mCurrentPosition = firstPosition - 1
        mFillAnchor = firstLeft - getItemWidth(firstView) / 2

        return fill(delta, recycler, state)
    }

    private fun recycleChildren(
        consume: Int,
        recycler: RecyclerView.Recycler
    ) {
        if (childCount == 0) return

        if (consume > 0) {//recycle start
            recycleStart()
        } else {//recycle end
            recycleEnd()
        }

        recycleOutChildren(recycler)
    }

    private fun recycleStart() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)!!
            val right = getDecoratedRight(child)
            if (right > getStart()) break

            mOutChildren.add(child)
        }
    }

    private fun recycleEnd() {
        for (i in childCount - 1 downTo 0) {
            val child = getChildAt(i)!!
            val left = getDecoratedLeft(child)
            if (left < getEnd()) break

            mOutChildren.add(child)
        }
    }

    private fun recycleOutChildren(recycler: RecyclerView.Recycler) {
        for (view in mOutChildren) {
            logDebug("recycle -- ${getPosition(view)}")
            removeAndRecycleView(view, recycler)
        }
        mOutChildren.clear()
    }

    //模仿创建OrientationHelper帮助类开始

    private fun hasMore(state: RecyclerView.State): Boolean {
        return mCurrentPosition >= 0 && mCurrentPosition < state.itemCount
    }

    private fun getTotalSpace(): Int {
        return if (orientation == HORIZONTAL) {
            width - paddingLeft - paddingRight
        } else {
            height - paddingTop - paddingBottom
        }
    }

    private fun offsetChildren(amount: Int) {
        if (orientation == HORIZONTAL) {
            offsetChildrenHorizontal(amount)
        } else {
            offsetChildrenVertical(amount)
        }
    }

    private fun nextView(recycler: RecyclerView.Recycler): View {
        val view = recycler.getViewForPosition(mCurrentPosition)
        mCurrentPosition += mItemFillDirection
        return view
    }

    private fun getItemWidth(child: View): Int {
        val params = child.layoutParams as RecyclerView.LayoutParams
        return getDecoratedMeasuredWidth(child) + params.leftMargin + params.rightMargin
    }

    private fun getItemHeight(child: View): Int {
        val params = child.layoutParams as RecyclerView.LayoutParams
        return getDecoratedMeasuredHeight(child) + params.topMargin + params.bottomMargin
    }

    private fun getItemSpace(child: View) = if (orientation == HORIZONTAL) {
        getItemWidth(child)
    } else {
        getItemHeight(child)
    }

    private fun getStart(): Int {
        return if (orientation == HORIZONTAL) {
            paddingLeft
        } else {
            paddingTop
        }
    }

    private fun getEnd(): Int {
        return if (orientation == HORIZONTAL) {
            width - paddingRight
        } else {
            height - paddingBottom
        }
    }

    //模仿创建OrientationHelper帮助类结束


    private fun logDebug(msg: String) {
        Log.d("AvatarLayoutManager", msg)
    }

    private fun logChildren(recycler: RecyclerView.Recycler) {
        logDebug("childCount = $childCount -- scrapSize = ${recycler.scrapList.size}")
    }

    override fun onAttachedToWindow(view: RecyclerView) {
        super.onAttachedToWindow(view)

        //改变children绘制顺序
        if (changeDrawingOrder) {
            view.setChildDrawingOrderCallback { childCount, i ->
                childCount - 1 - i
            }
        }
    }

}
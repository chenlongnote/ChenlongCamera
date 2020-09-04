package com.chenlongguo.lib_cl_camera.camera2.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import com.chenlongguo.lib_cl_camera.camera2.utils.Logger
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import java.util.concurrent.TimeUnit

class FocusView @JvmOverloads constructor(context: Context?, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : View(context, attrs, defStyleAttr) {
    private var mPaint: Paint = Paint()
    private var mCenterX = 0
    private var mCenterY = 0

    // 当前显示的外圈大小
    private var mRadiusOuter = 0

    // 当前显示的内圈大小
    private var mRadiusInner = 0

    // 外圈最大的尺寸
    private var mRadiusOuterBig = 0

    // 内圈最大的尺寸
    private var mRadiusInnerBig = 0

    // 外圈最小的尺寸
    private var mRadiusOuterSmall = 0

    // 内圈最小的尺寸
    private var mRadiusInnerSmall = 0
    private val mStrokeWidth = 2
    var deltaX = 0
        private set
    var deltaY = 0
        private set

    init {
        mPaint.color = -0x1
        mPaint.style = Paint.Style.STROKE
        mPaint.strokeWidth = 2f
        mPaint.isAntiAlias = true
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        mCenterX = width / 2
        mCenterY = height / 2
        deltaX = mCenterX
        deltaY = mCenterY
        // 最大半径取两个中较小的一个, 在减去画笔宽度, 当然减去1/2宽度也可以
        mRadiusOuterBig = if (width < height) width / 2 else height / 2 - mStrokeWidth
        mRadiusInnerBig = (mRadiusOuterBig * 0.3f).toInt()
        mRadiusOuterSmall = (mRadiusOuterBig * 0.85f).toInt()
        mRadiusInnerSmall = (mRadiusOuterBig * 0.2f).toInt()
        mRadiusOuter = mRadiusOuterSmall
        mRadiusInner = mRadiusInnerBig
        Logger.d(
            TAG, "mCenterX: " + mCenterX
                + ", mCenterY: " + mCenterY
                + ", mRadiusOuterBig: " + mRadiusOuterBig
                + ", mRadiusOuterSmall: " + mRadiusOuterSmall
                + ", mRadiusInnerBig: " + mRadiusInnerBig
                + ", mRadiusInnerSmall: " + mRadiusInnerSmall)
    }

    override fun onDraw(canvas: Canvas) {
        canvas.drawCircle(mCenterX.toFloat(), mCenterY.toFloat(), mRadiusOuter.toFloat(), mPaint)
        canvas.drawCircle(mCenterX.toFloat(), mCenterY.toFloat(), mRadiusInner.toFloat(), mPaint)
    }

    //内圆动画
    fun finishFocus(success: Boolean) {
        val outerAnim = ValueAnimator.ofInt(mRadiusOuterSmall, mRadiusOuterBig)
        outerAnim.addUpdateListener { animation -> mRadiusOuter = animation.animatedValue as Int }
        val innerAnim = ValueAnimator.ofInt(mRadiusInnerBig, mRadiusInnerSmall)
        innerAnim.addUpdateListener { animation ->
            mRadiusInner = animation.animatedValue as Int
            invalidate()
        }
        val set = AnimatorSet()
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                Observable.timer(1, TimeUnit.SECONDS)
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe { visibility = GONE }
            }
        })
        set.playTogether(outerAnim, innerAnim)
        set.duration = 350
        set.start()
    }

    companion object {
        private const val TAG = "FocusView"
    }
}
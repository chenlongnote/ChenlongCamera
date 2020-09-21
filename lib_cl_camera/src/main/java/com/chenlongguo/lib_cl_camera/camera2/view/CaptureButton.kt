package com.chenlongguo.lib_cl_camera.camera2.view

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.os.CountDownTimer
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.chenlongguo.lib_cl_camera.CaptureUtil
import com.chenlongguo.lib_cl_camera.R
import com.chenlongguo.lib_cl_camera.camera2.utils.DisplayUtil
import com.chenlongguo.lib_cl_camera.camera2.utils.Logger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

class CaptureButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {
    //当前按钮状态
    private var state = STATE_IDLE

    //中心坐标
    private var mCenterX = 0f
    private var mCenterY = 0f

    //按钮半径
    private var mButtonRadius = 0f

    //按钮大小
    private var mButtonSize = 0

    //进度条颜色
    private val mProgressColor = getContext().resources.getColor(R.color.capture_btn_process_color)

    //进度条宽度
    private var mStrokeWidth = 0f

    //录制视频的进度
    private var mProgress = 0f

    //进度条绘制的范围
    private lateinit var rectF: RectF

    //外圆背景色
    private val mOutsideColor = getContext().resources.getColor(R.color.capture_btn_outside_color)

    //外圆半径
    private var mButtonOutsideRadius = 0f

    //长按后，外圆半径变大的Size
    private var mOutsideAddSize = 0

    //内圆背景色
    private val mInsideColor = getContext().resources.getColor(R.color.capture_btn_inside_color)

    //内圆半径
    private var mButtonInsideRadius = 0f

    //长按内圆缩小的Size
    private var mInsideReduceSize = 0

    //Touch_Event_Down时候记录的Y值
    private var mDownY = 0f

    //录制视频最大时间长度
    private var mDuration = 0

    //最短录制时间限制
    private var mMinDuration = 0

    //记录当前录制的时间
    private var mRecordedTime = 0

    private var mLongPressRunnable //长按后处理的逻辑Runnable
            : LongPressRunnable? = null
    private var mCaptureButtonListener //按钮回调接口
            : CaptureButtonListener? = null
    private var timer //计时器
            : RecordCountDownTimer? = null


    var type = CaptureUtil.TYPE_ALL

    private var mPaint = Paint()

    init {
        init(DisplayUtil.dpToPx(76))
    }

    private fun init(size: Int) {
        mButtonSize = size
        mButtonRadius = size / 2.0f
        mButtonOutsideRadius = mButtonRadius
        mButtonInsideRadius = mButtonRadius * 0.75f
        mStrokeWidth = size / 14.toFloat()
        mOutsideAddSize = size / 5
        mInsideReduceSize = size / 8

        mPaint.isAntiAlias = true
        mProgress = 0f
        mLongPressRunnable = LongPressRunnable()
        state = STATE_IDLE //初始化为空闲状态
        mDuration = 10 * 1000 //默认最长录制时间为10s
        mMinDuration = 500 //默认最短录制时间为0.5s
        mCenterX = (mButtonSize + mOutsideAddSize * 2) / 2.toFloat()
        mCenterY = (mButtonSize + mOutsideAddSize * 2) / 2.toFloat()
        rectF = RectF(
            mCenterX - (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2),
            mCenterY - (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2),
            mCenterX + (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2),
            mCenterY + (mButtonRadius + mOutsideAddSize - mStrokeWidth / 2)
        )
        timer = RecordCountDownTimer(mDuration.toLong(), (mDuration / 360).toLong()) //录制定时器
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        setMeasuredDimension(mButtonSize + mOutsideAddSize * 2, mButtonSize + mOutsideAddSize * 2)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        mPaint.style = Paint.Style.FILL
        mPaint.color = mOutsideColor //外圆（半透明灰色）
        canvas.drawCircle(mCenterX, mCenterY, mButtonOutsideRadius, mPaint)
        mPaint.color = mInsideColor //内圆（白色）
        canvas.drawCircle(mCenterX, mCenterY, mButtonInsideRadius, mPaint)

        //如果状态为录制状态，则绘制录制进度条
        if (state == STATE_RECORDING) {
            mPaint.color = mProgressColor
            mPaint.style = Paint.Style.STROKE
            mPaint.strokeWidth = mStrokeWidth
            canvas.drawArc(rectF, -90f, mProgress, false, mPaint)
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                Logger.d(TAG, "state = $state")
                if (event.pointerCount == 1 && state == STATE_IDLE) {
                    mDownY = event.y //记录Y值
                    state = STATE_PRESS //修改当前状态为点击按下

                    //判断按钮状态是否为可录制状态
                    postDelayed(mLongPressRunnable, 350) //同时延长350启动长按后处理的逻辑Runnable
                }
            }
            MotionEvent.ACTION_MOVE ->
                if (state == STATE_RECORDING) {
                    //记录当前Y值与按下时候Y值的差值，调用缩放回调接口
                    mCaptureButtonListener?.recordZoom(mDownY - event.y)
                }
            MotionEvent.ACTION_UP ->                 //根据当前按钮的状态进行相应的处理
                handlerUpByState()
        }
        return true
    }

    //当手指松开按钮时候处理的逻辑
    private fun handlerUpByState() {
        removeCallbacks(mLongPressRunnable) //移除长按逻辑的Runnable
        when (state) {
            STATE_PRESS -> {
                if (mCaptureButtonListener != null) {
                    startCaptureAnimation(mButtonInsideRadius)
                }
                state = STATE_IDLE
            }
            STATE_RECORDING -> {
                timer?.cancel() //停止计时器
                recordEnd() //录制结束
            }
        }
    }

    //录制结束
    private fun recordEnd() {
        resetRecordAnim() //重制按钮状态
        if (mRecordedTime < mMinDuration)
            mCaptureButtonListener?.recordShort(mRecordedTime.toLong()) //回调录制时间过短
        else
            mCaptureButtonListener?.recordEnd(mRecordedTime.toLong()) //回调录制结束
    }

    private fun recordDurationMaxAsync() {
        GlobalScope.launch(Dispatchers.IO) {
            Logger.d(TAG, "1 ${Thread.currentThread().name}")
            mCaptureButtonListener?.recordDurationMax()
            Logger.d(TAG, "2 ${Thread.currentThread().name}")
        }
//        runBlocking(Dispatchers.IO) {
//            Logger.d(TAG, "1 ${Thread.currentThread().name}")
//            mCaptureButtonListener?.recordDurationMax()
//            Logger.d(TAG, "2 ${Thread.currentThread().name}")
//        }

        Logger.d(TAG, "3 ${Thread.currentThread().name}")
        resetRecordAnim() //重制按钮状态
    }
    //重制状态
    private fun resetRecordAnim() {
        Logger.d(TAG, "resetRecordAnim")
        state = STATE_BAN
        mProgress = 0f //重制进度
        invalidate()
        //还原按钮初始状态动画
        stopRecordAnimation(
            mButtonOutsideRadius,
            mButtonRadius,
            mButtonInsideRadius,
            mButtonRadius * 0.75f
        )
    }

    //内圆动画
    private fun startCaptureAnimation(inside_start: Float) {
        val insideAnim = ValueAnimator.ofFloat(inside_start, inside_start * 0.75f, inside_start)
        insideAnim.addUpdateListener { animation ->
            mButtonInsideRadius = animation.animatedValue as Float
            invalidate()
        }
        insideAnim.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                super.onAnimationEnd(animation)
                //回调拍照接口
                mCaptureButtonListener!!.takePictures()
                state = STATE_BAN
            }
        })
        insideAnim.duration = 100
        insideAnim.start()
    }

    private fun startRecord() {
        if (mCaptureButtonListener != null) {
            mCaptureButtonListener!!.startRecord()
        }
    }

    fun startRecordAnimation() {
        //设置为录制状态
        if (state == STATE_LONG_PRESS) {
            state = STATE_RECORDING
            timer!!.start()
        }
        startRecordAnimation(
            mButtonOutsideRadius,
            mButtonOutsideRadius + mOutsideAddSize,
            mButtonInsideRadius,
            mButtonInsideRadius - mInsideReduceSize
        )
    }

    //内外圆动画
    private fun startRecordAnimation(
        outside_start: Float,
        outside_end: Float,
        inside_start: Float,
        inside_end: Float
    ) {
        val outsideAnim = ValueAnimator.ofFloat(outside_start, outside_end)
        val insideAnim = ValueAnimator.ofFloat(inside_start, inside_end)
        //外圆动画监听
        outsideAnim.addUpdateListener { animation ->
            mButtonOutsideRadius = animation.animatedValue as Float
        }
        //内圆动画监听
        insideAnim.addUpdateListener { animation ->
            mButtonInsideRadius = animation.animatedValue as Float
            invalidate()
        }
        val set = AnimatorSet()
        set.playTogether(outsideAnim, insideAnim)
        set.duration = 350
        set.start()
    }

    //内外圆动画
    private fun stopRecordAnimation(
        outside_start: Float,
        outside_end: Float,
        inside_start: Float,
        inside_end: Float
    ) {
        val outsideAnim = ValueAnimator.ofFloat(outside_start, outside_end)
        val insideAnim = ValueAnimator.ofFloat(inside_start, inside_end)
        //外圆动画监听
        outsideAnim.addUpdateListener { animation ->
            mButtonOutsideRadius = animation.animatedValue as Float
        }
        //内圆动画监听
        insideAnim.addUpdateListener { animation ->
            mButtonInsideRadius = animation.animatedValue as Float
            invalidate()
        }
        val set = AnimatorSet()
        set.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                mCaptureButtonListener!!.onStopAnimFinished()
            }
        })
        set.playTogether(outsideAnim, insideAnim)
        set.duration = 350
        set.start()
    }

    //更新进度条
    private fun updateProgress(millisUntilFinished: Long) {
        mRecordedTime = (mDuration - millisUntilFinished).toInt()
        mProgress = 360f - millisUntilFinished / mDuration.toFloat() * 360f
        postInvalidate()
    }

    //录制视频计时器
    private inner class RecordCountDownTimer(
        millisInFuture: Long,
        countDownInterval: Long
    ) : CountDownTimer(millisInFuture, countDownInterval) {
        override fun onTick(millisUntilFinished: Long) {
            updateProgress(millisUntilFinished)
        }

        override fun onFinish() {
            updateProgress(0)
            recordDurationMaxAsync()
        }
    }

    //长按线程
    private inner class LongPressRunnable : Runnable {
        override fun run() {
            if (type != CaptureUtil.TYPE_IMAGE) {
                state = STATE_LONG_PRESS //如果按下后经过一段时间则会修改当前状态为长按状态
                //启动按钮动画，外圆变大，内圆缩小
                startRecord()
            }
        }
    }

    /**************************************************
     * 对外提供的API                     *
     */
    //设置最长录制时间
    fun setDuration(duration: Int) {
        mDuration = duration
        timer = RecordCountDownTimer(duration.toLong(), (duration / 360).toLong()) //录制定时器
    }

    //设置最短录制时间
    fun setMinDuration(duration: Int) {
        mMinDuration = duration
    }

    //设置回调接口
    fun setCaptureButtonListener(listener: CaptureButtonListener?) {
        mCaptureButtonListener = listener
    }

    //是否空闲状态
    val isIdle: Boolean
        get() = state == STATE_IDLE

    //设置状态
    fun resetState() {
        state = STATE_IDLE
    }

    companion object {
        private const val TAG = "CaptureButton"
        const val STATE_IDLE = 0x001 //空闲状态
        const val STATE_PRESS = 0x002 //按下状态
        const val STATE_LONG_PRESS = 0x003 //长按状态
        const val STATE_RECORDING = 0x004 //录制状态
        const val STATE_BAN = 0x005 //禁止状态
    }
}
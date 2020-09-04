package com.chenlongguo.lib_cl_camera.camera2.view

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.databinding.DataBindingUtil
import com.chenlongguo.lib_cl_camera.camera2.flash.FlashState
import com.chenlongguo.lib_cl_camera.R
import com.chenlongguo.lib_cl_camera.databinding.ChooseFlashLayout2Binding

class ChooseFlashView2 @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0) : LinearLayout(context, attrs, defStyleAttr), View.OnClickListener {
    private lateinit var binding: ChooseFlashLayout2Binding

    private var mCurrentFlashState = FlashState.AUTO
    private var mViewState = VIEW_STATE_NORMAL
    private var mOnFlashStateChangedListener: OnFlashStateChangedListener? = null
    fun setOnFlashStateChangedListener(listener: OnFlashStateChangedListener?) {
        mOnFlashStateChangedListener = listener
    }

    private fun init(context: Context) {
        binding = DataBindingUtil.inflate(LayoutInflater.from(context),R.layout.choose_flash_layout2, this, true)
        binding.flashIcon.setOnClickListener(this)
        binding.tvFlashOff.setOnClickListener(this)
        binding.tvFlashAuto.setOnClickListener(this)
        binding.tvFlashOn.setOnClickListener(this)
        binding.tvFlashAlwaysOn.setOnClickListener(this)
        setViewState(mCurrentFlashState)
    }

    private fun setViewState(state: Int) {
        when (state) {
            FlashState.AUTO -> {
                binding.flashIcon.setImageResource(R.drawable.ic_flash_auto)
                binding.tvFlashOff.background = null
                binding.tvFlashAuto.setBackgroundResource(R.drawable.flash_item_bg_selected)
                binding.tvFlashOn.background = null
                binding.tvFlashAlwaysOn.background = null
            }
            FlashState.OFF -> {
                binding.flashIcon.setImageResource(R.drawable.ic_flash_off)
                binding.tvFlashOff.setBackgroundResource(R.drawable.flash_item_bg_selected)
                binding.tvFlashAuto.background = null
                binding.tvFlashOn.background = null
                binding.tvFlashAlwaysOn.background = null
            }
            FlashState.ON -> {
                binding.flashIcon.setImageResource(R.drawable.ic_flash_on)
                binding.tvFlashOff.background = null
                binding.tvFlashAuto.background = null
                binding.tvFlashOn.setBackgroundResource(R.drawable.flash_item_bg_selected)
                binding.tvFlashAlwaysOn.background = null
            }
            FlashState.ALWAYS_ON -> {
                binding.flashIcon.setImageResource(R.drawable.ic_flash_always_on)
                binding.tvFlashOff.background = null
                binding.tvFlashAuto.background = null
                binding.tvFlashOn.background = null
                binding.tvFlashAlwaysOn.setBackgroundResource(R.drawable.flash_item_bg_selected)
            }
        }
    }

    override fun onClick(v: View) {
        when (mViewState) {
            VIEW_STATE_NORMAL -> {
                doExpand()
            }
            VIEW_STATE_EXPAND -> {
                var state = mCurrentFlashState
                when (v.id) {
                    R.id.tv_flash_always_on -> {
                        state = FlashState.ALWAYS_ON
                    }
                    R.id.tv_flash_on -> {
                        state = FlashState.ON
                    }
                    R.id.tv_flash_off -> {
                        state = FlashState.OFF
                    }
                    R.id.tv_flash_auto -> {
                        state = FlashState.AUTO
                    }
                }
                chooseFlashState(state)
            }
        }
    }

    private fun chooseFlashState(state: Int) {
        binding.tvFlashOff.visibility = View.GONE
        binding.tvFlashAuto.visibility = View.GONE
        binding.tvFlashOn.visibility = View.GONE
        binding.tvFlashAlwaysOn.visibility = View.GONE
        requestLayout()
        mViewState = VIEW_STATE_NORMAL
        if (mCurrentFlashState != state) {
            setViewState(state)
            mCurrentFlashState = state
            mOnFlashStateChangedListener!!.onChanged(state)
        }
    }

    private fun doExpand() {
        binding.tvFlashOff.visibility = View.VISIBLE
        binding.tvFlashAuto.visibility = View.VISIBLE
        binding.tvFlashOn.visibility = View.VISIBLE
        binding.tvFlashAlwaysOn.visibility = View.VISIBLE
        invalidate()
        mViewState = VIEW_STATE_EXPAND
    }

    companion object {
        const val VIEW_STATE_NORMAL = 1
        const val VIEW_STATE_EXPAND = 2
    }

    init {
        init(context)
    }
}
package com.chenlongguo.lib_cl_camera.camera2

import android.hardware.camera2.params.MeteringRectangle




class Settings3A {
    /**
     * Width of touch metering region in [0,1] relative to shorter edge of the
     * current crop region. Multiply this number by the number of pixels along
     * shorter edge of the current crop region's width to get a value in pixels.
     *
     *
     * This value has been tested on Nexus 5 and Shamu, but will need to be
     * tuned per device depending on how its ISP interprets the metering box and
     * weight.
     *
     *
     *
     * Was fixed at 300px x 300px prior to L release.
     *
     */
    private val GCAM_METERING_REGION_FRACTION = 0.1225f

    /**
     * @Return The weight to use for [MeteringRectangle]s for 3A.
     */
    fun getMeteringWeight(): Int {
        val weightMin = MeteringRectangle.METERING_WEIGHT_MIN
        val weightRange = (MeteringRectangle.METERING_WEIGHT_MAX
                - MeteringRectangle.METERING_WEIGHT_MIN)
        return (weightMin + GCAM_METERING_REGION_FRACTION * weightRange).toInt()
    }

    /**
     * @return The size of (square) metering regions, normalized with respect to
     * the smallest dimension of the current crop-region.
     */
    fun getMeteringRegionFraction(): Float {
        return GCAM_METERING_REGION_FRACTION
    }
}
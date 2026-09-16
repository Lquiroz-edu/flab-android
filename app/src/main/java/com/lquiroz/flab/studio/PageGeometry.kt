package com.lquiroz.flab.studio

import kotlin.math.*

object PageGeometry {
    fun progress(value: Float): Float = if (value.isFinite()) value.coerceIn(0f, 1f) else 0f
    fun edge(value: Float): Float = cos(Math.PI * progress(value)).toFloat()
    fun lift(value: Float): Float = sin(Math.PI * progress(value)).toFloat().coerceAtLeast(0f)
}

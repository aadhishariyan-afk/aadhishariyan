package com.example

import androidx.compose.ui.graphics.Color

data class FluidParticle(
    val id: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var color: Color = Color.White
) {
    val mass: Float
        get() = radius * radius // mass proportional to area
}

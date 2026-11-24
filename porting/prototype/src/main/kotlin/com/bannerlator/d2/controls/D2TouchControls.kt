package com.bannerlator.d2.controls

import kotlin.math.hypot

/** Normalized touch position in the game viewport (0.0 .. 1.0). */
data class NormalizedPoint(val x: Float, val y: Float)

/** Independent analog movement-stick settings. */
data class StickConfig(
    val radiusDp: Float = 64f,
    val deadZone: Float = 0.14f,
    val followsThumb: Boolean = false,
) {
    init {
        require(radiusDp > 0f)
        require(deadZone in 0f..0.8f)
    }
}

data class StickVector(val x: Float, val y: Float, val strength: Float) {
    companion object {
        val ZERO = StickVector(0f, 0f, 0f)
    }
}

/**
 * Converts a finger position to an analog vector.
 * The result is a game command vector, not a mouse cursor position.
 */
object D2JoystickMath {
    fun fromTouch(
        centerX: Float,
        centerY: Float,
        touchX: Float,
        touchY: Float,
        radiusPx: Float,
        deadZone: Float,
    ): StickVector {
        require(radiusPx > 0f)
        require(deadZone in 0f..0.8f)

        val dx = touchX - centerX
        val dy = touchY - centerY
        val distance = hypot(dx, dy)
        if (distance <= 0f) return StickVector.ZERO

        val rawStrength = (distance / radiusPx).coerceIn(0f, 1f)
        if (rawStrength <= deadZone) return StickVector.ZERO

        // Remap the active range so full travel still reaches 1.0.
        val strength = ((rawStrength - deadZone) / (1f - deadZone)).coerceIn(0f, 1f)
        val nx = dx / distance
        val ny = dy / distance
        return StickVector(nx, ny, strength)
    }
}

enum class D2ButtonShape { CIRCLE, ROUNDED_RECT, RECTANGLE, CUSTOM_TEXTURE }

data class D2ControlButtonStyle(
    val x: Float,
    val y: Float,
    val widthDp: Float = 58f,
    val heightDp: Float = 58f,
    val opacity: Float = 0.82f,
    val shape: D2ButtonShape = D2ButtonShape.CIRCLE,
    val texturePath: String? = null,
    val rotationDegrees: Float = 0f,
    val zIndex: Int = 0,
) {
    init {
        require(x in 0f..1f && y in 0f..1f)
        require(widthDp in 16f..240f && heightDp in 16f..240f)
        require(opacity in 0.2f..1f)
        require(rotationDegrees.isFinite())
        require(shape != D2ButtonShape.CUSTOM_TEXTURE || !texturePath.isNullOrBlank())
    }
}

/** Movement, target selection and attack are deliberately independent channels. */
data class D2TouchFrame(
    val movement: StickVector = StickVector.ZERO,
    val attackPressed: Boolean = false,
    val targetSelectionRequested: Boolean = false,
    val skillSlotPressed: Int? = null,
)

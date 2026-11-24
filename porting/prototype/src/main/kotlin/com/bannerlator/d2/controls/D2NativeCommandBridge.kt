package com.bannerlator.d2.controls

/**
 * Narrow interface between Android touch controls and the in-process Diablo II module.
 * Implementations must send game-native commands; mouse-click emulation is not a fallback.
 */
interface D2NativeCommandBridge {
    fun move(directionX: Float, directionY: Float, strength: Float)
    fun setAttackPressed(pressed: Boolean)
    fun requestTargetSelection()
    fun useSkill(slot: Int)
    fun stopAll()
}

data class ExecutableFingerprint(
    val sha256: String,
    val peTimestamp: Long,
    val imageSize: Long,
    val version: String,
) {
    init {
        require(sha256.matches(Regex("[0-9a-fA-F]{64}")))
        require(peTimestamp >= 0L)
        require(imageSize > 0L)
        require(version.isNotBlank())
    }
}

/**
 * Fail-closed compatibility gate. The supported list must be populated only after
 * offsets, signatures and commands have been verified against the exact game build.
 */
class D2CompatibilityGate(
    private val verifiedBuilds: Set<ExecutableFingerprint> = emptySet(),
) {
    fun isSupported(actual: ExecutableFingerprint): Boolean =
        verifiedBuilds.contains(actual)
}

class D2TouchCommandDispatcher(
    private val bridge: D2NativeCommandBridge,
    private val gate: D2CompatibilityGate,
) {
    fun dispatch(frame: D2TouchFrame, actualBuild: ExecutableFingerprint): Boolean {
        if (!gate.isSupported(actualBuild)) {
            bridge.stopAll()
            return false
        }

        val movement = frame.movement
        bridge.move(movement.x, movement.y, movement.strength)
        bridge.setAttackPressed(frame.attackPressed)

        if (frame.targetSelectionRequested) bridge.requestTargetSelection()
        frame.skillSlotPressed?.let { slot ->
            require(slot in 0..15) { "Skill slot must be between 0 and 15" }
            bridge.useSkill(slot)
        }
        return true
    }
}

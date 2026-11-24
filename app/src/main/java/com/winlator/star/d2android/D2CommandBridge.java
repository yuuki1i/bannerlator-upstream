package com.winlator.star.d2android;

/**
 * Semantic command boundary between Android touch input and a version-verified in-game adapter.
 * Implementations must translate these commands to the matching game build; never guess RVAs.
 */
public final class D2CommandBridge {
    public interface GameAdapter {
        boolean isReadyFor(String buildId);
        void move(float x, float y, float strength);
        void attack(boolean pressed);
        void selectSkill(int slot);

        /** Optional directional aim update while a skill pointer is dragged. */
        default void aim(float x, float y, float strength) {}
        /** Optional held-skill lifecycle; old adapters remain source-compatible. */
        default void castSkill(int slot, boolean pressed) {
            if (pressed) selectSkill(slot);
        }
        /** Optional target cycling: direction is -1 or +1. */
        default void cycleTarget(int direction) {}
        default void openPanel(int panel) {}
        default void autoPickup() {}
        default void talk() {}
        default void sortInventory() {}
        default void tap(float normalizedX, float normalizedY, boolean pressed) {}
    }

    private final GameAdapter adapter;
    private final D2CompatibilityGate gate;

    public D2CommandBridge(GameAdapter adapter, D2CompatibilityGate gate) {
        if (adapter == null || gate == null) throw new IllegalArgumentException("Adapter and gate required");
        this.adapter = adapter;
        this.gate = gate;
    }

    public boolean isAvailable() {
        return gate.isVerified() && gate.isEnabled()
                && adapter.isReadyFor(D2CompatibilityGate.TARGET_BUILD);
    }

    public boolean move(D2JoystickMath.Vector vector) {
        if (!isAvailable() || vector == null) return false;
        adapter.move(clamp(vector.x), clamp(vector.y), clamp(vector.strength));
        return true;
    }

    public boolean aim(D2JoystickMath.Vector vector) {
        if (!isAvailable() || vector == null) return false;
        adapter.aim(clamp(vector.x), clamp(vector.y), clamp(vector.strength));
        return true;
    }

    public boolean attack(boolean pressed) {
        if (!isAvailable()) return false;
        adapter.attack(pressed);
        return true;
    }

    public boolean selectSkill(int slot) {
        if (!isAvailable() || slot < 0 || slot > 15) return false;
        adapter.selectSkill(slot);
        return true;
    }

    public boolean castSkill(int slot, boolean pressed) {
        if (!isAvailable() || slot < 0 || slot > 15) return false;
        adapter.castSkill(slot, pressed);
        return true;
    }

    public boolean cycleTarget(int direction) {
        if (!isAvailable() || direction == 0) return false;
        adapter.cycleTarget(direction < 0 ? -1 : 1);
        return true;
    }

    public boolean openPanel(int panel) {
        if (!isAvailable() || panel < 0 || panel > 4) return false;
        adapter.openPanel(panel);
        return true;
    }

    public boolean autoPickup() {
        if (!isAvailable()) return false;
        adapter.autoPickup();
        return true;
    }

    public boolean talk() {
        if (!isAvailable()) return false;
        adapter.talk();
        return true;
    }

    public boolean sortInventory() {
        if (!isAvailable()) return false;
        adapter.sortInventory();
        return true;
    }

    public boolean tap(float normalizedX, float normalizedY, boolean pressed) {
        if (!isAvailable()) return false;
        adapter.tap(Math.max(0f,Math.min(1f,normalizedX)),
                Math.max(0f,Math.min(1f,normalizedY)), pressed);
        return true;
    }

    private static float clamp(float value) {
        return Math.max(-1f, Math.min(1f, value));
    }
}

package com.winlator.star.d2android;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class D2CommandBridgeTest {
    private static final String HASH = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";

    private static final class FakeAdapter implements D2CommandBridge.GameAdapter {
        boolean ready;
        int moveCalls, attackCalls, selectCalls, aimCalls, castCalls, targetCalls;
        boolean lastAttack, lastCast;
        int lastSkill, lastTargetDirection;
        float lastX, lastY, lastStrength;

        @Override public boolean isReadyFor(String buildId) {
            return ready && D2CompatibilityGate.TARGET_BUILD.equals(buildId);
        }
        @Override public void move(float x, float y, float strength) {
            moveCalls++; lastX=x; lastY=y; lastStrength=strength;
        }
        @Override public void attack(boolean pressed) { attackCalls++; lastAttack=pressed; }
        @Override public void selectSkill(int slot) { selectCalls++; lastSkill=slot; }
        @Override public void aim(float x, float y, float strength) {
            aimCalls++; lastX=x; lastY=y; lastStrength=strength;
        }
        @Override public void castSkill(int slot, boolean pressed) {
            castCalls++; lastSkill=slot; lastCast=pressed;
        }
        @Override public void cycleTarget(int direction) {
            targetCalls++; lastTargetDirection=direction;
        }
    }

    @Test public void commandsFailClosedUntilHashAndAdapterAreVerified() {
        FakeAdapter adapter = new FakeAdapter();
        D2CompatibilityGate gate = new D2CompatibilityGate(Collections.singleton(HASH));
        D2CommandBridge bridge = new D2CommandBridge(adapter, gate);
        assertFalse(bridge.move(D2JoystickMath.Vector.zero()));
        assertFalse(bridge.attack(true));
        assertFalse(bridge.castSkill(0, true));
        assertEquals(0, adapter.moveCalls + adapter.attackCalls + adapter.castCalls);
        assertFalse(gate.verifyExecutableSha256("bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"));
    }

    @Test public void heldSkillAimAndTargetCommandsReachVerifiedAdapter() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.ready = true;
        D2CompatibilityGate gate = new D2CompatibilityGate(Collections.singleton(HASH));
        assertTrue(gate.verifyExecutableSha256(HASH));
        D2CommandBridge bridge = new D2CommandBridge(adapter, gate);

        assertTrue(bridge.aim(new D2JoystickMath.Vector(2f, -2f, 2f)));
        assertTrue(bridge.castSkill(3, true));
        assertTrue(bridge.castSkill(3, false));
        assertTrue(bridge.cycleTarget(-9));

        assertEquals(1, adapter.aimCalls);
        assertEquals(1f, adapter.lastStrength, 0f);
        assertEquals(3, adapter.lastSkill);
        assertFalse(adapter.lastCast);
        assertEquals(-1, adapter.lastTargetDirection);
    }

    @Test public void invalidSkillSlotsAreRejected() {
        FakeAdapter adapter = new FakeAdapter();
        adapter.ready = true;
        D2CompatibilityGate gate = new D2CompatibilityGate(Collections.singleton(HASH));
        gate.verifyExecutableSha256(HASH);
        D2CommandBridge bridge = new D2CommandBridge(adapter, gate);
        assertFalse(bridge.castSkill(-1, true));
        assertFalse(bridge.selectSkill(16));
        assertEquals(0, adapter.castCalls + adapter.selectCalls);
    }
}

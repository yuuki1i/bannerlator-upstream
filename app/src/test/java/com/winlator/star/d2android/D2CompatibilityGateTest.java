package com.winlator.star.d2android;

import org.junit.Test;
import java.util.Collections;
import static org.junit.Assert.*;

public class D2CompatibilityGateTest {
    @Test public void emptyAllowlistAlwaysFailsClosed() {
        D2CompatibilityGate gate=new D2CompatibilityGate(Collections.<String>emptySet());
        assertFalse(gate.verifyExecutableSha256("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(gate.isEnabled());
        assertFalse(gate.isVerified());
    }
    @Test public void malformedFingerprintIsRejected() {
        D2CompatibilityGate gate=new D2CompatibilityGate(Collections.singleton("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"));
        assertFalse(gate.verifyExecutableSha256("not-a-hash"));
        assertFalse(gate.isEnabled());
    }
    @Test public void exactAllowlistedFingerprintEnablesGate() {
        String hash="aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
        D2CompatibilityGate gate=new D2CompatibilityGate(Collections.singleton(hash));
        assertTrue(gate.verifyExecutableSha256(hash.toUpperCase()));
        assertTrue(gate.isEnabled());
        gate.disable();
        assertFalse(gate.isEnabled());
    }
}

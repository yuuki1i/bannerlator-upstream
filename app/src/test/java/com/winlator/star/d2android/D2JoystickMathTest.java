package com.winlator.star.d2android;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.*;

public class D2JoystickMathTest {
    @Test public void deadZoneProducesZero() {
        D2JoystickMath.Vector v = D2JoystickMath.fromTouch(100, 100, 105, 100, 100, .14f);
        assertEquals(0f, v.strength, .0001f);
    }

    @Test public void fullDeflectionIsNormalized() {
        D2JoystickMath.Vector v = D2JoystickMath.fromTouch(100, 100, 300, 100, 100, .14f);
        assertEquals(1f, v.x, .0001f);
        assertEquals(0f, v.y, .0001f);
        assertEquals(1f, v.strength, .0001f);
    }

    @Test public void diagonalIsClampedToUnitCircle() {
        D2JoystickMath.Vector v = D2JoystickMath.fromTouch(0, 0, 100, 100, 100, .1f);
        assertTrue(Math.hypot(v.x, v.y) <= 1.0001);
    }

    @Test public void profileKeepsMovementAndAttackSeparate() throws Exception {
        D2ControlProfile profile = new D2ControlProfile();
        assertNotEquals(profile.movement.x, profile.attack.x, .0001f);
        assertEquals("diablo2-lod-1.13c", profile.toJson().getString("game"));
        assertEquals(4, profile.toJson().getJSONArray("skills").length());
    }

    @Test public void profileRoundTripsAllControlSettings() throws Exception {
        D2ControlProfile original = new D2ControlProfile();
        original.attack.set(.79f, .72f, .17f, .17f, .55f,
                D2ControlProfile.Shape.SQUARE, "custom/attack.png");
        D2ControlProfile restored = D2ControlProfile.fromJson(original.toJson());
        assertEquals(.79f, restored.attack.x, .0001f);
        assertEquals(.55f, restored.attack.opacity, .0001f);
        assertEquals(D2ControlProfile.Shape.SQUARE, restored.attack.shape);
        assertEquals("custom/attack.png", restored.attack.texture);
    }

    @Test(expected = IllegalArgumentException.class)
    public void profileRejectsElementsOutsideScreen() {
        new D2ControlProfile.Element(.9f, .9f, .2f, .2f, 1f,
                D2ControlProfile.Shape.CIRCLE, "");
    }

    @Test(expected = org.json.JSONException.class)
    public void profileRejectsWrongGameSchema() throws Exception {
        D2ControlProfile.fromJson(new JSONObject().put("schema", 1).put("game", "diablo2-lod-1.13d"));
    }
}

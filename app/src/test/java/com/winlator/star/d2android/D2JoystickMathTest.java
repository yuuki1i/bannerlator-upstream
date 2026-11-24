package com.winlator.star.d2android;

import org.junit.Test;
import static org.junit.Assert.*;

public class D2JoystickMathTest {
    @Test public void deadZoneProducesZero() {
        D2JoystickMath.Vector v=D2JoystickMath.fromTouch(100,100,105,100,100,.14f);
        assertEquals(0f,v.strength,0.0001f);
    }
    @Test public void fullDeflectionIsNormalized() {
        D2JoystickMath.Vector v=D2JoystickMath.fromTouch(100,100,300,100,100,.14f);
        assertEquals(1f,v.x,0.0001f);
        assertEquals(0f,v.y,0.0001f);
        assertEquals(1f,v.strength,0.0001f);
    }
    @Test public void diagonalIsClampedToUnitCircle() {
        D2JoystickMath.Vector v=D2JoystickMath.fromTouch(0,0,100,100,100,.1f);
        assertTrue(Math.hypot(v.x,v.y)<=1.0001);
    }
    @Test public void profileKeepsMovementAndAttackSeparate() throws Exception {
        D2ControlProfile p=new D2ControlProfile();
        assertNotEquals(p.movement.x,p.attack.x,0.0001f);
        assertEquals("diablo2-lod-1.13c",p.toJson().getString("game"));
        assertEquals(4,p.toJson().getJSONArray("skills").length());
    }
}

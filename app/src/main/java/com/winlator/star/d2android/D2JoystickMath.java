package com.winlator.star.d2android;

/** Pure joystick math: outputs a normalized movement vector, never a cursor position. */
public final class D2JoystickMath {
    private D2JoystickMath() {}
    public static final class Vector {
        public final float x, y, strength;
        public Vector(float x, float y, float strength) { this.x=x; this.y=y; this.strength=strength; }
        public static Vector zero() { return new Vector(0f,0f,0f); }
    }
    public static Vector fromTouch(float cx,float cy,float tx,float ty,float radiusPx,float deadZone) {
        if (!(radiusPx>0f) || deadZone<0f || deadZone>=1f) throw new IllegalArgumentException("Invalid joystick geometry");
        float dx=tx-cx, dy=ty-cy, distance=(float)Math.hypot(dx,dy);
        if (distance<=0f) return Vector.zero();
        float raw=Math.min(1f,distance/radiusPx);
        if (raw<=deadZone) return Vector.zero();
        float strength=(raw-deadZone)/(1f-deadZone);
        return new Vector(dx/distance*strength,dy/distance*strength,strength);
    }
}

package com.winlator.star.d2android;

/** Semantic game bridge contract. A game-side adapter must implement this; no fake mouse fallback. */
public final class D2CommandBridge {
    public interface GameAdapter { boolean isReadyFor(String buildId); void move(float x,float y,float strength); void attack(boolean pressed); void selectSkill(int slot); }
    private final GameAdapter adapter; private final D2CompatibilityGate gate;
    public D2CommandBridge(GameAdapter adapter,D2CompatibilityGate gate) { if(adapter==null||gate==null)throw new IllegalArgumentException("Adapter and gate required");this.adapter=adapter;this.gate=gate; }
    public boolean isAvailable() { return gate.isVerified()&&gate.isEnabled()&&adapter.isReadyFor(D2CompatibilityGate.TARGET_BUILD); }
    public boolean move(D2JoystickMath.Vector v) { if(!isAvailable()||v==null)return false;adapter.move(clamp(v.x),clamp(v.y),clamp(v.strength));return true; }
    public boolean attack(boolean pressed) { if(!isAvailable())return false;adapter.attack(pressed);return true; }
    public boolean selectSkill(int slot) { if(!isAvailable()||slot<0||slot>15)return false;adapter.selectSkill(slot);return true; }
    private static float clamp(float v) { return Math.max(-1f,Math.min(1f,v)); }
}

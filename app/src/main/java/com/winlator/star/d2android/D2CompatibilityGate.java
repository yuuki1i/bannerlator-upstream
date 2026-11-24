package com.winlator.star.d2android;

import java.util.Collections;import java.util.HashSet;import java.util.Set;

/** Fail-closed build gate; whitelist only hashes measured from verified owner-supplied 1.13c files. */
public final class D2CompatibilityGate {
    public static final String TARGET_BUILD="diablo2-lod-1.13c";
    public static final String APPROVED_GAME_SHA256 =
            "7baeda5e815fbe7a807d6a102aada579cd7a1b89383dd4a9b5d92f887ef165bf";
    private final Set<String> approved; private String observed=""; private boolean enabled;
    public D2CompatibilityGate(Set<String> hashes) { Set<String> copy=new HashSet<>(); if(hashes!=null)for(String h:hashes)if(h!=null&&h.matches("(?i)[0-9a-f]{64}"))copy.add(h.toLowerCase());approved=Collections.unmodifiableSet(copy); }
    public synchronized boolean verifyExecutableSha256(String hash) { observed=hash==null?"":hash.toLowerCase();enabled=observed.matches("[0-9a-f]{64}")&&approved.contains(observed);return enabled; }
    public synchronized boolean isVerified() { return !observed.isEmpty()&&approved.contains(observed); }
    public synchronized boolean isEnabled() { return enabled; }
    public synchronized void disable() { enabled=false; }
}

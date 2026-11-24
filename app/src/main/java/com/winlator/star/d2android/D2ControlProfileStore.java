package com.winlator.star.d2android;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONException;
import org.json.JSONObject;

/** Persists the D2 touch layout separately from Bannerlator's generic control profiles. */
public final class D2ControlProfileStore {
    private static final String PREFS = "d2_control_profiles";
    private static final String KEY_113C = "diablo2_lod_1_13c";
    private final SharedPreferences preferences;

    public D2ControlProfileStore(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public D2ControlProfile load() {
        String encoded = preferences.getString(KEY_113C, null);
        if (encoded == null) return new D2ControlProfile();
        try {
            return D2ControlProfile.fromJson(new JSONObject(encoded));
        } catch (JSONException | IllegalArgumentException exception) {
            // A corrupt or obsolete profile must never prevent the game controls from opening.
            return new D2ControlProfile();
        }
    }

    public boolean save(D2ControlProfile profile) {
        if (profile == null) return false;
        try {
            return preferences.edit().putString(KEY_113C, profile.toJson().toString()).commit();
        } catch (JSONException exception) {
            return false;
        }
    }

    public boolean reset() {
        return preferences.edit().remove(KEY_113C).commit();
    }
}

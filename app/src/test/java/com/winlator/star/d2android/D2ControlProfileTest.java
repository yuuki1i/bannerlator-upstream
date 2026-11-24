package com.winlator.star.d2android;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public class D2ControlProfileTest {
    @Test public void roundTripPreservesEightSkillsAndTargetControls() throws Exception {
        D2ControlProfile original = new D2ControlProfile();
        original.targetNext.set(.81f, .41f, .075f, .075f, .55f,
                D2ControlProfile.Shape.SQUARE, "target");
        D2ControlProfile restored = D2ControlProfile.fromJson(original.toJson());
        assertEquals(8, restored.skills.length);
        assertEquals(.81f, restored.targetNext.x, 0f);
        assertEquals(D2ControlProfile.Shape.SQUARE, restored.targetNext.shape);
    }

    @Test public void migratesFourSkillProfilesWithoutLosingLayout() throws Exception {
        D2ControlProfile original = new D2ControlProfile();
        JSONObject full = original.toJson();
        JSONArray four = new JSONArray();
        JSONArray all = full.getJSONArray("skills");
        for (int i=0;i<4;i++) four.put(all.getJSONObject(i));
        JSONObject legacy = new JSONObject()
                .put("schema", 1)
                .put("game", "diablo2-lod-1.13c")
                .put("movement", full.getJSONObject("movement"))
                .put("attack", full.getJSONObject("attack"))
                .put("skills", four);
        D2ControlProfile migrated = D2ControlProfile.fromJson(legacy);
        assertEquals(8, migrated.skills.length);
        assertEquals(original.skills[0].x, migrated.skills[0].x, 0f);
        assertEquals(original.skills[4].x, migrated.skills[4].x, 0f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void elementRejectsOutOfBoundsGeometry() {
        new D2ControlProfile.Element(.9f, .9f, .2f, .2f, .5f,
                D2ControlProfile.Shape.CIRCLE, "");
    }
}

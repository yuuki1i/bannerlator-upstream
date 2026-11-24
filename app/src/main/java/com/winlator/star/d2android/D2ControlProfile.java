package com.winlator.star.d2android;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/** Per-game touch layout. Movement and attack remain independent controls. */
public final class D2ControlProfile {
    public enum Shape { CIRCLE, SQUARE, ROUNDED, TEXTURE }

    public static final class Element {
        public float x, y, width, height, opacity;
        public Shape shape;
        public String texture;

        public Element(float x, float y, float width, float height, float opacity,
                       Shape shape, String texture) {
            set(x, y, width, height, opacity, shape, texture);
        }

        public void set(float x, float y, float width, float height, float opacity,
                        Shape shape, String texture) {
            if (!finite(x) || !finite(y) || !finite(width) || !finite(height)
                    || !finite(opacity) || x < 0f || y < 0f
                    || width < .025f || width > .6f || height < .025f || height > .6f
                    || x + width > 1f || y + height > 1f
                    || opacity < 0f || opacity > 1f || shape == null) {
                throw new IllegalArgumentException("Invalid control element bounds/style");
            }
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.opacity = opacity;
            this.shape = shape;
            this.texture = texture == null ? "" : texture;
        }

        JSONObject toJson() throws JSONException {
            return new JSONObject().put("x", x).put("y", y).put("width", width)
                    .put("height", height).put("opacity", opacity)
                    .put("shape", shape.name()).put("texture", texture);
        }

        static Element fromJson(JSONObject json) throws JSONException {
            return new Element((float) json.getDouble("x"), (float) json.getDouble("y"),
                    (float) json.getDouble("width"), (float) json.getDouble("height"),
                    (float) json.getDouble("opacity"),
                    Shape.valueOf(json.getString("shape")), json.optString("texture", ""));
        }
    }

    public final Element movement = new Element(.12f, .76f, .23f, .23f, .72f, Shape.CIRCLE, "");
    public final Element attack = new Element(.88f, .82f, .12f, .12f, .88f, Shape.CIRCLE, "");
    public final Element skillPageToggle = new Element(.90f, .55f, .07f, .07f, .78f, Shape.ROUNDED, "");
    // Two independently persisted skill pages. Only four are visible at a time.
    public final Element[] skills = {
            new Element(.62f, .82f, .085f, .085f, .82f, Shape.ROUNDED, ""),
            new Element(.72f, .72f, .085f, .085f, .82f, Shape.ROUNDED, ""),
            new Element(.82f, .62f, .085f, .085f, .82f, Shape.ROUNDED, ""),
            new Element(.72f, .88f, .085f, .085f, .82f, Shape.ROUNDED, ""),
            new Element(.62f, .82f, .085f, .085f, .82f, Shape.ROUNDED, ""),
            new Element(.72f, .72f, .085f, .085f, .82f, Shape.ROUNDED, ""),
            new Element(.82f, .62f, .085f, .085f, .82f, Shape.ROUNDED, ""),
            new Element(.72f, .88f, .085f, .085f, .82f, Shape.ROUNDED, "")
    };

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject().put("schema", 1).put("game", "diablo2-lod-1.13c")
                .put("movement", movement.toJson()).put("attack", attack.toJson())
                .put("skillPageToggle", skillPageToggle.toJson());
        JSONArray array = new JSONArray();
        for (Element skill : skills) array.put(skill.toJson());
        return root.put("skills", array);
    }

    public static D2ControlProfile fromJson(JSONObject root) throws JSONException {
        if (root == null || root.optInt("schema", -1) != 1
                || !"diablo2-lod-1.13c".equals(root.optString("game"))) {
            throw new JSONException("Unsupported Diablo II control profile");
        }
        D2ControlProfile profile = new D2ControlProfile();
        apply(profile.movement, root.getJSONObject("movement"));
        apply(profile.attack, root.getJSONObject("attack"));
        JSONArray array = root.getJSONArray("skills");
        if (array.length() != profile.skills.length) throw new JSONException("Expected four skill controls");
        for (int i = 0; i < profile.skills.length; i++) apply(profile.skills[i], array.getJSONObject(i));
        return profile;
    }

    private static void apply(Element target, JSONObject source) throws JSONException {
        Element parsed = Element.fromJson(source);
        target.set(parsed.x, parsed.y, parsed.width, parsed.height, parsed.opacity, parsed.shape, parsed.texture);
    }

    private static boolean finite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}

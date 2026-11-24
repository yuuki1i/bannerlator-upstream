package com.winlator.star.d2android;

import org.json.JSONException;
import org.json.JSONObject;
import org.json.JSONArray;

/** Per-game touch layout. Movement and attack remain independent controls. */
public final class D2ControlProfile {
    public enum Shape { CIRCLE, SQUARE, ROUNDED, TEXTURE }
    public static final class Element {
        public float x,y,width,height,opacity; public Shape shape; public String texture;
        public Element(float x,float y,float w,float h,float opacity,Shape shape,String texture) { set(x,y,w,h,opacity,shape,texture); }
        public void set(float x,float y,float w,float h,float opacity,Shape shape,String texture) {
            if(x<0||x>1||y<0||y>1||w<.025f||w>.6f||h<.025f||h>.6f||opacity<0||opacity>1||shape==null)
                throw new IllegalArgumentException("Invalid control element bounds/style");
            this.x=x;this.y=y;width=w;height=h;this.opacity=opacity;this.shape=shape;this.texture=texture==null?"":texture;
        }
        JSONObject toJson() throws JSONException { return new JSONObject().put("x",x).put("y",y).put("width",width).put("height",height).put("opacity",opacity).put("shape",shape.name()).put("texture",texture); }
    }
    public final Element movement=new Element(.12f,.76f,.23f,.23f,.72f,Shape.CIRCLE,"");
    public final Element attack=new Element(.86f,.76f,.16f,.16f,.88f,Shape.CIRCLE,"");
    public final Element[] skills={new Element(.66f,.82f,.10f,.10f,.82f,Shape.ROUNDED,""),new Element(.77f,.66f,.10f,.10f,.82f,Shape.ROUNDED,""),new Element(.88f,.56f,.10f,.10f,.82f,Shape.ROUNDED,""),new Element(.65f,.60f,.10f,.10f,.82f,Shape.ROUNDED,"")};
    public JSONObject toJson() throws JSONException { JSONObject root=new JSONObject().put("schema",1).put("game","diablo2-lod-1.13c").put("movement",movement.toJson()).put("attack",attack.toJson()); JSONArray a=new JSONArray(); for(Element e:skills)a.put(e.toJson()); return root.put("skills",a); }
}

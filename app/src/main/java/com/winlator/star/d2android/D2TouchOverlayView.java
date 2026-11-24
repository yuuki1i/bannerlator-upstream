package com.winlator.star.d2android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;

/**
 * Multitouch overlay for Diablo II. It emits semantic commands only through D2CommandBridge.
 * The host must attach this view to the game's overlay layer and provide a verified adapter.
 */
public final class D2TouchOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final D2ControlProfile profile = new D2ControlProfile();
    private D2CommandBridge bridge;
    private int movePointer = -1, attackPointer = -1;
    private float moveX, moveY, moveRadius;
    private boolean editing;

    public D2TouchOverlayView(Context context, D2CommandBridge bridge) {
        super(context);
        this.bridge = bridge;
        setClickable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public D2ControlProfile getProfile() { return profile; }
    public void setBridge(D2CommandBridge bridge) { this.bridge = bridge; }
    public void setEditing(boolean editing) { this.editing = editing; invalidate(); }

    /** Adds this overlay above the game surface; call from the owning game Activity. */
    public void attachTo(FrameLayout gameLayer) {
        if (getParent() != null) ((FrameLayout)getParent()).removeView(this);
        gameLayer.addView(this, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private RectF bounds(D2ControlProfile.Element e) {
        float w=getWidth(), h=getHeight();
        float left=e.x*w, top=e.y*h;
        return new RectF(left,top,left+e.width*w,top+e.height*h);
    }
    private boolean contains(D2ControlProfile.Element e,float x,float y) { return bounds(e).contains(x,y); }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        drawElement(c,profile.movement,"移动");
        drawElement(c,profile.attack,"攻击");
        for(int i=0;i<profile.skills.length;i++) drawElement(c,profile.skills[i],"技"+(i+1));
        if(movePointer!=-1) {
            paint.setColor(0xBFFFFFFF); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3f);
            c.drawCircle(moveX,moveY,moveRadius,paint); paint.setStyle(Paint.Style.FILL);
        }
    }
    private void drawElement(Canvas c,D2ControlProfile.Element e,String label) {
        RectF r=bounds(e);
        paint.setColor((Math.round(e.opacity*255f)<<24)|0x003B4355);
        paint.setStyle(Paint.Style.FILL);
        if(e.shape==D2ControlProfile.Shape.CIRCLE) c.drawOval(r,paint);
        else if(e.shape==D2ControlProfile.Shape.SQUARE) c.drawRect(r,paint);
        else c.drawRoundRect(r,Math.min(r.width(),r.height())*.18f,Math.min(r.width(),r.height())*.18f,paint);
        paint.setColor(0xEFFFFFFF); paint.setTextAlign(Paint.Align.CENTER); paint.setTextSize(Math.max(12f,r.height()*.22f));
        c.drawText(label,r.centerX(),r.centerY()+paint.getTextSize()*.35f,paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if(editing) return true;
        int action=event.getActionMasked(), index=event.getActionIndex(), id=event.getPointerId(index);
        float x=event.getX(index), y=event.getY(index);
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_POINTER_DOWN) {
            if(movePointer==-1&&contains(profile.movement,x,y)) {
                movePointer=id; RectF r=bounds(profile.movement); moveX=r.centerX();moveY=r.centerY();
                moveRadius=Math.min(r.width(),r.height())*.42f; updateMove(x,y); return true;
            }
            if(attackPointer==-1&&contains(profile.attack,x,y)) {
                attackPointer=id; if(bridge!=null) bridge.attack(true); invalidate(); return true;
            }
            for(int i=0;i<profile.skills.length;i++) if(contains(profile.skills[i],x,y)) {
                if(bridge!=null) bridge.selectSkill(i); return true;
            }
            return false;
        }
        if(action==MotionEvent.ACTION_MOVE) {
            for(int p=0;p<event.getPointerCount();p++) {
                int pointer=event.getPointerId(p);
                if(pointer==movePointer) updateMove(event.getX(p),event.getY(p));
            }
            return movePointer!=-1||attackPointer!=-1;
        }
        if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_POINTER_UP||action==MotionEvent.ACTION_CANCEL) {
            if(id==movePointer) { movePointer=-1; if(bridge!=null) bridge.move(D2JoystickMath.Vector.zero()); }
            if(id==attackPointer) { attackPointer=-1; if(bridge!=null) bridge.attack(false); }
            invalidate(); return true;
        }
        return true;
    }
    private void updateMove(float x,float y) {
        D2JoystickMath.Vector v=D2JoystickMath.fromTouch(moveX,moveY,x,y,moveRadius,.14f);
        if(bridge!=null) bridge.move(v);
        invalidate();
    }
}

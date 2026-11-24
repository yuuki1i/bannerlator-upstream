package com.winlator.star.d2android;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Independent Diablo II touch overlay. In-game commands are emitted only through a verified bridge.
 * Edit mode supports dragging each control and persists the layout on release.
 */
public final class D2TouchOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final D2ControlProfileStore store;
    private final D2ControlProfile profile;
    private D2CommandBridge bridge;
    private int movePointer = -1, attackPointer = -1, editPointer = -1;
    private float moveX, moveY, moveRadius, editOffsetX, editOffsetY;
    private D2ControlProfile.Element editingElement;
    private boolean editing;

    public D2TouchOverlayView(Context context, D2CommandBridge bridge) {
        super(context);
        this.bridge = bridge;
        store = new D2ControlProfileStore(context);
        profile = store.load();
        setClickable(true);
        setLayerType(View.LAYER_TYPE_HARDWARE, null);
    }

    public D2ControlProfile getProfile() { return profile; }
    public void setBridge(D2CommandBridge bridge) { this.bridge = bridge; }
    public void setEditing(boolean editing) {
        if (this.editing == editing) return;
        releasePointers();
        this.editing = editing;
        invalidate();
    }

    /** Adds this overlay above the game surface; call from the owning game Activity. */
    public void attachTo(FrameLayout gameLayer) {
        if (getParent() instanceof ViewGroup) ((ViewGroup) getParent()).removeView(this);
        gameLayer.addView(this, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private RectF bounds(D2ControlProfile.Element element) {
        float w = getWidth(), h = getHeight();
        float left = element.x * w, top = element.y * h;
        return new RectF(left, top, left + element.width * w, top + element.height * h);
    }

    private boolean contains(D2ControlProfile.Element element, float x, float y) {
        return bounds(element).contains(x, y);
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawElement(canvas, profile.movement, "移动");
        drawElement(canvas, profile.attack, "攻击");
        for (int i = 0; i < profile.skills.length; i++) drawElement(canvas, profile.skills[i], "技" + (i + 1));
        if (movePointer != -1) {
            paint.setColor(0xBFFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawCircle(moveX, moveY, moveRadius, paint);
            paint.setStyle(Paint.Style.FILL);
        }
        if (editingElement != null) {
            RectF selected = bounds(editingElement);
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawRect(selected, paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private void drawElement(Canvas canvas, D2ControlProfile.Element element, String label) {
        RectF rect = bounds(element);
        paint.setColor((Math.round(element.opacity * 255f) << 24) | 0x003B4355);
        paint.setStyle(Paint.Style.FILL);
        if (element.shape == D2ControlProfile.Shape.CIRCLE) canvas.drawOval(rect, paint);
        else if (element.shape == D2ControlProfile.Shape.SQUARE) canvas.drawRect(rect, paint);
        else canvas.drawRoundRect(rect, Math.min(rect.width(), rect.height()) * .18f,
                Math.min(rect.width(), rect.height()) * .18f, paint);
        paint.setColor(0xEFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(Math.max(12f, rect.height() * .22f));
        canvas.drawText(label, rect.centerX(), rect.centerY() + paint.getTextSize() * .35f, paint);
    }

    private D2ControlProfile.Element elementAt(float x, float y) {
        for (int i = profile.skills.length - 1; i >= 0; i--) {
            if (contains(profile.skills[i], x, y)) return profile.skills[i];
        }
        if (contains(profile.attack, x, y)) return profile.attack;
        if (contains(profile.movement, x, y)) return profile.movement;
        return null;
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        int index = event.getActionIndex();
        int id = event.getPointerId(index);
        float x = event.getX(index), y = event.getY(index);

        if (editing) {
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                if (editPointer != -1) return true;
                editingElement = elementAt(x, y);
                if (editingElement != null) {
                    editPointer = id;
                    editOffsetX = x - editingElement.x * getWidth();
                    editOffsetY = y - editingElement.y * getHeight();
                }
                invalidate();
                return true;
            }
            if (action == MotionEvent.ACTION_MOVE && editPointer != -1) {
                for (int p = 0; p < event.getPointerCount(); p++) {
                    if (event.getPointerId(p) == editPointer) {
                        moveEditedElement(event.getX(p), event.getY(p));
                        break;
                    }
                }
                return true;
            }
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP
                    || action == MotionEvent.ACTION_CANCEL) {
                if (action == MotionEvent.ACTION_CANCEL || id == editPointer) {
                    if (editingElement != null) store.save(profile);
                    editPointer = -1;
                    editingElement = null;
                    invalidate();
                }
                return true;
            }
            return true;
        }

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (movePointer == -1 && contains(profile.movement, x, y)) {
                movePointer = id;
                RectF rect = bounds(profile.movement);
                moveX = rect.centerX();
                moveY = rect.centerY();
                moveRadius = Math.min(rect.width(), rect.height()) * .42f;
                updateMove(x, y);
                return true;
            }
            if (attackPointer == -1 && contains(profile.attack, x, y)) {
                attackPointer = id;
                if (bridge != null) bridge.attack(true);
                invalidate();
                return true;
            }
            for (int i = 0; i < profile.skills.length; i++) {
                if (contains(profile.skills[i], x, y)) {
                    if (bridge != null) bridge.selectSkill(i);
                    return true;
                }
            }
            return false;
        }
        if (action == MotionEvent.ACTION_MOVE) {
            for (int p = 0; p < event.getPointerCount(); p++) {
                int pointer = event.getPointerId(p);
                if (pointer == movePointer) updateMove(event.getX(p), event.getY(p));
            }
            return movePointer != -1 || attackPointer != -1;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            releasePointers();
            invalidate();
            return true;
        }
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (id == movePointer) {
                movePointer = -1;
                if (bridge != null) bridge.move(D2JoystickMath.Vector.zero());
            }
            if (id == attackPointer) {
                attackPointer = -1;
                if (bridge != null) bridge.attack(false);
            }
            invalidate();
            return true;
        }
        return true;
    }

    private void moveEditedElement(float x, float y) {
        if (editingElement == null || getWidth() <= 0 || getHeight() <= 0) return;
        float nx = (x - editOffsetX) / getWidth();
        float ny = (y - editOffsetY) / getHeight();
        nx = Math.max(0f, Math.min(1f - editingElement.width, nx));
        ny = Math.max(0f, Math.min(1f - editingElement.height, ny));
        editingElement.set(nx, ny, editingElement.width, editingElement.height,
                editingElement.opacity, editingElement.shape, editingElement.texture);
        invalidate();
    }

    private void updateMove(float x, float y) {
        D2JoystickMath.Vector vector = D2JoystickMath.fromTouch(moveX, moveY, x, y, moveRadius, .14f);
        if (bridge != null) bridge.move(vector);
        invalidate();
    }

    private void releasePointers() {
        movePointer = -1;
        attackPointer = -1;
        editPointer = -1;
        editingElement = null;
        if (bridge != null) {
            bridge.move(D2JoystickMath.Vector.zero());
            bridge.attack(false);
        }
    }
}

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
 * Diablo II touch surface. Movement, attack and skill gestures own independent pointer IDs,
 * so a player can move, hold attack and drag-aim a skill simultaneously.
 *
 * The view deliberately sends semantic commands only. A verified 1.13c game adapter is still
 * required before commands can reach the game process.
 */
public final class D2TouchOverlayView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final D2ControlProfileStore store;
    private final D2ControlProfile profile;
    private D2CommandBridge bridge;

    private int movePointer = -1;
    private int attackPointer = -1;
    private int skillPointer = -1;
    private int editPointer = -1;
    private int activeSkill = -1;
    private int skillPage = 0;
    private boolean menuOpen;

    private float moveX, moveY, moveRadius;
    private float skillX, skillY, skillRadius;
    private float editOffsetX, editOffsetY;
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
    public void setBridge(D2CommandBridge bridge) {
        releasePointers();
        this.bridge = bridge;
    }

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
        for (int i = 0; i < 4; i++) {
            int slot = skillPage * 4 + i;
            drawElement(canvas, profile.skills[slot], "F" + (slot + 1));
        }
        drawElement(canvas, profile.skillPageToggle, skillPage == 0 ? "1/2" : "2/2");
        drawElement(canvas, profile.targetPrev, "目标-");
        drawElement(canvas, profile.targetNext, "目标+");
        drawElement(canvas, profile.menuButton, "菜单");
        if (menuOpen) drawMenu(canvas);
        if (movePointer != -1) drawGestureRing(canvas, moveX, moveY, moveRadius);
        if (skillPointer != -1) drawGestureRing(canvas, skillX, skillY, skillRadius);
        if (editingElement != null) {
            paint.setColor(0xFFFFFFFF);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f);
            canvas.drawRect(bounds(editingElement), paint);
            paint.setStyle(Paint.Style.FILL);
        }
    }

    private static final String[] MENU_LABELS = {
            "角色属性", "技能树", "佣兵", "背包", "大箱子", "拾取附近物品", "与目标对话", "整理背包"
    };

    private RectF menuBounds(int index) {
        float density = getResources().getDisplayMetrics().density;
        float rowHeight = 42f * density, panelWidth = 172f * density;
        float left = Math.max(8f * density, getWidth() - panelWidth - 12f * density);
        float top = 12f * density + index * rowHeight;
        return new RectF(left, top, left + panelWidth, top + rowHeight - 3f * density);
    }

    private void drawMenu(Canvas canvas) {
        float density = getResources().getDisplayMetrics().density;
        RectF panel = new RectF(menuBounds(0).left - 5f * density,
                menuBounds(0).top - 5f * density,
                menuBounds(MENU_LABELS.length - 1).right + 5f * density,
                menuBounds(MENU_LABELS.length - 1).bottom + 5f * density);
        paint.setColor(0xE6222630);
        canvas.drawRoundRect(panel, 12f * density, 12f * density, paint);
        paint.setColor(0xF2FFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTextSize(14f * density);
        for (int i=0;i<MENU_LABELS.length;i++) {
            RectF row = menuBounds(i);
            paint.setColor(i % 2 == 0 ? 0xB0444C5C : 0xB0333B49);
            canvas.drawRoundRect(row, 7f * density, 7f * density, paint);
            paint.setColor(0xF2FFFFFF);
            canvas.drawText(MENU_LABELS[i], row.centerX(), row.centerY() + 5f * density, paint);
        }
    }

    private void activateMenuItem(int index) {
        if (bridge != null) {
            if (index <= 4) bridge.openPanel(index);
            else if (index == 5) bridge.autoPickup();
            else if (index == 6) bridge.talk();
            else if (index == 7) bridge.sortInventory();
        }
        menuOpen = false;
        invalidate();
    }

    private void drawGestureRing(Canvas canvas, float x, float y, float radius) {
        paint.setColor(0xBFFFFFFF);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f);
        canvas.drawCircle(x, y, radius, paint);
        paint.setStyle(Paint.Style.FILL);
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
        if (contains(profile.menuButton, x, y)) return profile.menuButton;
        if (contains(profile.targetPrev, x, y)) return profile.targetPrev;
        if (contains(profile.targetNext, x, y)) return profile.targetNext;
        if (contains(profile.skillPageToggle, x, y)) return profile.skillPageToggle;
        for (int i = 3; i >= 0; i--) {
            int slot = skillPage * 4 + i;
            if (contains(profile.skills[slot], x, y)) return profile.skills[slot];
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

        if (action == MotionEvent.ACTION_CANCEL) {
            if (editing) finishEditing(true);
            else releasePointers();
            invalidate();
            return true;
        }

        if (editing) return handleEditingTouch(event, action, id, x, y);

        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
            if (menuOpen) {
                for (int i=0;i<MENU_LABELS.length;i++) {
                    if (menuBounds(i).contains(x,y)) { activateMenuItem(i); return true; }
                }
                menuOpen = false;
                invalidate();
            }
            if (contains(profile.menuButton, x, y)) {
                menuOpen = !menuOpen;
                invalidate();
                return true;
            }
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
            if (contains(profile.targetPrev, x, y)) {
                if (bridge != null) bridge.cycleTarget(-1);
                return true;
            }
            if (contains(profile.targetNext, x, y)) {
                if (bridge != null) bridge.cycleTarget(1);
                return true;
            }
            if (contains(profile.skillPageToggle, x, y)) {
                skillPage = 1 - skillPage;
                invalidate();
                return true;
            }
            if (skillPointer == -1) {
                for (int i = 0; i < 4; i++) {
                    int slot = skillPage * 4 + i;
                    if (contains(profile.skills[slot], x, y)) {
                        skillPointer = id;
                        activeSkill = slot;
                        RectF rect = bounds(profile.skills[slot]);
                        skillX = rect.centerX();
                        skillY = rect.centerY();
                        skillRadius = Math.max(1f, Math.min(rect.width(), rect.height()) * .58f);
                        if (bridge != null) {
                            bridge.selectSkill(slot);
                            bridge.castSkill(slot, true);
                        }
                        updateAim(x, y);
                        invalidate();
                        return true;
                    }
                }
            }
            return movePointer != -1 || attackPointer != -1 || skillPointer != -1;
        }

        if (action == MotionEvent.ACTION_MOVE) {
            for (int p = 0; p < event.getPointerCount(); p++) {
                int pointer = event.getPointerId(p);
                float px = event.getX(p), py = event.getY(p);
                if (pointer == movePointer) updateMove(px, py);
                if (pointer == skillPointer) updateAim(px, py);
            }
            return movePointer != -1 || attackPointer != -1 || skillPointer != -1;
        }

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            releasePointer(id);
            invalidate();
            return true;
        }
        return true;
    }

    private boolean handleEditingTouch(MotionEvent event, int action, int id, float x, float y) {
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
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            if (id == editPointer) finishEditing(false);
            return true;
        }
        return true;
    }

    private void finishEditing(boolean cancelled) {
        if (!cancelled && editingElement != null) store.save(profile);
        editPointer = -1;
        editingElement = null;
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

    private void updateAim(float x, float y) {
        D2JoystickMath.Vector vector = D2JoystickMath.fromTouch(skillX, skillY, x, y, skillRadius, .08f);
        if (bridge != null) bridge.aim(vector);
        invalidate();
    }

    private void releasePointer(int pointerId) {
        if (pointerId == movePointer) {
            movePointer = -1;
            if (bridge != null) bridge.move(D2JoystickMath.Vector.zero());
        }
        if (pointerId == attackPointer) {
            attackPointer = -1;
            if (bridge != null) bridge.attack(false);
        }
        if (pointerId == skillPointer) {
            int releasedSkill = activeSkill;
            skillPointer = -1;
            activeSkill = -1;
            if (bridge != null && releasedSkill >= 0) bridge.castSkill(releasedSkill, false);
        }
    }

    private void releasePointers() {
        if (bridge != null) {
            if (movePointer != -1) bridge.move(D2JoystickMath.Vector.zero());
            if (attackPointer != -1) bridge.attack(false);
            if (skillPointer != -1 && activeSkill >= 0) bridge.castSkill(activeSkill, false);
        }
        movePointer = -1;
        attackPointer = -1;
        skillPointer = -1;
        activeSkill = -1;
        editPointer = -1;
        editingElement = null;
    }
}

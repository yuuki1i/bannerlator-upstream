package com.winlator.star.d2android;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * UDP transport for the D2Android game-module protocol (D2AC commands / D2AM state).
 * It only reports ready after the in-game module positively reports STATUS_HOOKED.
 */
public final class D2ModUdpAdapter implements D2CommandBridge.GameAdapter, AutoCloseable {
    public static final int APP_PORT = 7958, MOD_PORT = 7959;
    private static final int STATUS_HOOKED = 1;
    private static final long STATE_MAX_AGE_MS = 500;

    public static final class Monster {
        public final long id;
        public final int x, y, hpPercent, flags;
        public final String name;
        Monster(long id, int x, int y, int hpPercent, int flags, String name) {
            this.id=id; this.x=x; this.y=y; this.hpPercent=hpPercent; this.flags=flags; this.name=name;
        }
    }

    public static final class Thing {
        public final long id;
        public final int x, y, kind, extra;
        Thing(long id, int x, int y, int kind, int extra) {
            this.id=id; this.x=x; this.y=y; this.kind=kind; this.extra=extra;
        }
    }

    public static final class GameState {
        public final int width, height, playerX, playerY, hoverType;
        public final long hoverId;
        public final List<Monster> monsters;
        public final List<Thing> items, objects;
        public final int[] hotkeySkills;
        public final boolean[] hotkeyLeft;
        public final long receivedAt;
        GameState(int width, int height, int playerX, int playerY, int hoverType,
                  long hoverId, List<Monster> monsters, List<Thing> items, List<Thing> objects,
                  int[] hotkeySkills, boolean[] hotkeyLeft, long receivedAt) {
            this.width=width; this.height=height; this.playerX=playerX; this.playerY=playerY;
            this.hoverType=hoverType; this.hoverId=hoverId;
            this.monsters=Collections.unmodifiableList(monsters);
            this.items=Collections.unmodifiableList(items);
            this.objects=Collections.unmodifiableList(objects);
            this.hotkeySkills=hotkeySkills; this.hotkeyLeft=hotkeyLeft; this.receivedAt=receivedAt;
        }
    }

    private static final class SkillBinding {
        final int id; final boolean left;
        SkillBinding(int id, boolean left) { this.id=id; this.left=left; }
    }

    private final Object lock = new Object();
    private final SkillBinding[] skills = new SkillBinding[16];
    private final ScheduledExecutorService sender = Executors.newSingleThreadScheduledExecutor();
    private volatile DatagramSocket socket;
    private volatile Thread receiver;
    private volatile int status = -1;
    private volatile String statusText = "not connected";
    private volatile GameState state;
    private volatile float aimX, aimY;
    private volatile boolean walking, attackHeld;
    private volatile int targetType = -1;
    private volatile long targetId = -1;
    private volatile int activeSkillSlot = -1;
    private volatile long activeSkillTargetId = -1;
    private volatile int activeSkillTargetType = -1;
    private ScheduledFuture<?> attackKeepAlive;

    public void start() {
        synchronized (lock) {
            if (receiver != null) return;
            Thread t = new Thread(this::receiveLoop, "D2-mod-udp");
            t.setDaemon(true);
            receiver = t;
            t.start();
        }
    }

    public String getStatusText() { return statusText; }

    public void setSkillBinding(int slot, int skillId, boolean left) {
        if (slot < 0 || slot >= skills.length || skillId < 0 || skillId > 0xFFFF)
            throw new IllegalArgumentException("Invalid skill binding");
        synchronized (lock) { skills[slot] = skillId == 0 ? null : new SkillBinding(skillId, left); }
    }

    @Override public boolean isReadyFor(String buildId) {
        GameState current = state;
        return D2CompatibilityGate.TARGET_BUILD.equals(buildId)
                && status == STATUS_HOOKED && current != null
                && System.currentTimeMillis() - current.receivedAt <= STATE_MAX_AGE_MS;
    }

    @Override public void move(float x, float y, float strength) {
        int dx = Math.round(clamp(x) * 260f);
        int dy = Math.round(clamp(y) * 260f);
        int phase = strength <= 0.001f ? D2CommandPacket.PHASE_UP
                : (walking ? D2CommandPacket.PHASE_HOLD : D2CommandPacket.PHASE_DOWN);
        walking = phase != D2CommandPacket.PHASE_UP;
        send(D2CommandPacket.walk(phase, dx, dy));
    }

    @Override public void aim(float x, float y, float strength) {
        aimX = clamp(x); aimY = clamp(y);
        if (activeSkillSlot < 0 || strength < 0.12f) return;
        GameState current = freshState();
        if (current == null) return;
        List<Monster> candidates = D2TargetSelector.rank(
                current.monsters, current.playerX, current.playerY, aimX, aimY);
        if (candidates.isEmpty()) return;
        Monster chosen = candidates.get(0);
        if (chosen.id == activeSkillTargetId) return;
        SkillBinding binding = skill(activeSkillSlot);
        if (binding == null) return;
        if (activeSkillTargetId >= 0) {
            send(D2CommandPacket.unit(binding.left ? D2CommandPacket.BUTTON_LEFT : D2CommandPacket.BUTTON_RIGHT,
                    D2CommandPacket.PHASE_UP, activeSkillTargetType, activeSkillTargetId));
        }
        activeSkillTargetId = chosen.id;
        activeSkillTargetType = 1;
        send(D2CommandPacket.highlight(1, chosen.id));
        send(D2CommandPacket.unit(binding.left ? D2CommandPacket.BUTTON_LEFT : D2CommandPacket.BUTTON_RIGHT,
                D2CommandPacket.PHASE_DOWN, 1, chosen.id));
    }

    @Override public void attack(boolean pressed) {
        if (pressed) {
            attackHeld = true;
            sendAttack(D2CommandPacket.PHASE_DOWN);
            synchronized (lock) {
                if (attackKeepAlive != null) attackKeepAlive.cancel(false);
                attackKeepAlive = sender.scheduleAtFixedRate(
                        () -> { if (attackHeld) sendAttack(D2CommandPacket.PHASE_HOLD); },
                        180, 180, TimeUnit.MILLISECONDS);
            }
        } else {
            attackHeld = false;
            synchronized (lock) {
                if (attackKeepAlive != null) attackKeepAlive.cancel(false);
                attackKeepAlive = null;
            }
            sendAttack(D2CommandPacket.PHASE_UP);
        }
    }

    private void sendAttack(int phase) {
        GameState current = freshState();
        if (current == null) return;
        if (targetId >= 0) {
            boolean stillVisible = false;
            for (Monster monster : current.monsters) {
                if (monster.id == targetId && monster.hpPercent > 0) { stillVisible = true; break; }
            }
            if (!stillVisible) { targetId = -1; targetType = -1; }
        }
        if (targetId < 0 && (current.hoverId == 0xFFFFFFFFL || current.hoverType < 0)) {
            chooseTarget(current);
        }
        long id = targetId >= 0 ? targetId : current.hoverId;
        int type = targetId >= 0 ? targetType : current.hoverType;
        if (id < 0 || type < 0 || id == 0xFFFFFFFFL) return;
        send(D2CommandPacket.unit(D2CommandPacket.BUTTON_LEFT, phase, type, id));
    }

    @Override public void selectSkill(int slot) {
        SkillBinding binding = skill(slot);
        if (binding != null) send(D2CommandPacket.selectSkill(binding.left, binding.id));
    }

    @Override public void castSkill(int slot, boolean pressed) {
        SkillBinding binding = skill(slot);
        if (binding == null) return;
        if (pressed) {
            activeSkillSlot = slot;
            send(D2CommandPacket.selectSkill(binding.left, binding.id));
            GameState current = freshState();
            if (current == null) return;
            long id = targetId >= 0 ? targetId : current.hoverId;
            int type = targetId >= 0 ? targetType : current.hoverType;
            if (id == 0xFFFFFFFFL || type < 0) {
                chooseTarget(current);
                id = targetId;
                type = targetType;
            }
            if (id >= 0 && id != 0xFFFFFFFFL && type >= 0) {
                activeSkillTargetId = id;
                activeSkillTargetType = type;
                send(D2CommandPacket.unit(binding.left ? D2CommandPacket.BUTTON_LEFT : D2CommandPacket.BUTTON_RIGHT,
                        D2CommandPacket.PHASE_DOWN, type, id));
            }
        } else {
            if (activeSkillTargetId >= 0) {
                send(D2CommandPacket.unit(binding.left ? D2CommandPacket.BUTTON_LEFT : D2CommandPacket.BUTTON_RIGHT,
                        D2CommandPacket.PHASE_UP, activeSkillTargetType, activeSkillTargetId));
            }
            activeSkillSlot = -1;
            activeSkillTargetId = -1;
            activeSkillTargetType = -1;
        }
    }

    @Override public void openPanel(int panel) {
        GameState current = freshState();
        if (panel == 4) {
            if (current != null && current.hoverType == 2 && current.hoverId != 0xFFFFFFFFL)
                send(D2CommandPacket.unit(D2CommandPacket.BUTTON_LEFT, D2CommandPacket.PHASE_CLICK,
                        2, current.hoverId));
            return;
        }
        final int[] keys = {0x43, 0x54, 0x4F, 0x49};
        if (panel >= 0 && panel < keys.length) send(D2CommandPacket.keyPress(keys[panel]));
    }

    @Override public void autoPickup() {
        GameState current = freshState();
        if (current == null || current.items.isEmpty()) return;
        Thing nearest = null;
        double best = 240.0 * 240.0;
        for (Thing item : current.items) {
            double dx=item.x-current.playerX, dy=item.y-current.playerY;
            double distance=dx*dx+dy*dy;
            if (distance < best) { best=distance; nearest=item; }
        }
        if (nearest != null) send(D2CommandPacket.take(4, nearest.id));
    }

    @Override public void talk() {
        GameState current = freshState();
        if (current == null || current.hoverType != 1 || current.hoverId == 0xFFFFFFFFL) return;
        for (Monster monster : current.monsters) {
            if (monster.id == current.hoverId && (monster.flags & 16) != 0) {
                send(D2CommandPacket.unit(D2CommandPacket.BUTTON_LEFT, D2CommandPacket.PHASE_CLICK,
                        1, current.hoverId));
                return;
            }
        }
    }

    @Override public void sortInventory() {
        send(D2CommandPacket.sortInventory(false));
    }

    @Override public void cycleTarget(int direction) {
        GameState current = freshState();
        if (current == null || current.monsters.isEmpty()) return;
        List<Monster> candidates = D2TargetSelector.rank(current.monsters, current.playerX, current.playerY, aimX, aimY);
        if (candidates.isEmpty()) return;
        int currentIndex = -1;
        for (int i=0;i<candidates.size();i++) if (candidates.get(i).id == targetId) currentIndex=i;
        int next = currentIndex < 0
                ? (direction < 0 ? candidates.size() - 1 : 0)
                : Math.floorMod(currentIndex + (direction < 0 ? -1 : 1), candidates.size());
        Monster chosen = candidates.get(next);
        targetId = chosen.id;
        targetType = 1;
        send(D2CommandPacket.highlight(1, chosen.id));
    }

    private void chooseTarget(GameState current) {
        List<Monster> candidates = D2TargetSelector.rank(
                current.monsters, current.playerX, current.playerY, aimX, aimY);
        if (candidates.isEmpty()) return;
        Monster chosen = candidates.get(0);
        targetId = chosen.id;
        targetType = 1;
        send(D2CommandPacket.highlight(1, chosen.id));
    }

    private SkillBinding skill(int slot) {
        if (slot < 0 || slot >= skills.length) return null;
        synchronized (lock) { return skills[slot]; }
    }

    private GameState freshState() {
        GameState current = state;
        return current != null && System.currentTimeMillis() - current.receivedAt <= STATE_MAX_AGE_MS
                ? current : null;
    }

    private void receiveLoop() {
        DatagramSocket mine = null;
        try (DatagramSocket s = new DatagramSocket(null)) {
            mine = s;
            s.setReuseAddress(true);
            s.bind(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), APP_PORT));
            s.setSoTimeout(1000);
            socket = s;
            byte[] bytes = new byte[8192];
            while (receiver == Thread.currentThread()) {
                DatagramPacket packet = new DatagramPacket(bytes, bytes.length);
                try { s.receive(packet); }
                catch (SocketTimeoutException ignored) { continue; }
                handlePacket(packet.getData(), packet.getLength());
            }
        } catch (IOException e) {
            statusText = "UDP " + APP_PORT + ": " + e.getMessage();
        } finally {
            if (socket == mine) socket = null;
        }
    }

    void handlePacket(byte[] data, int length) {
        if (length < 8 || data[0] != 'D' || data[1] != '2' || data[2] != 'A' || data[3] != 'M'
                || (data[4] & 0xFF) != 2) return;
        int type = data[5] & 0xFF;
        if (type == 1 && length >= 10) {
            status = data[8] & 0xFF;
            int n = Math.min(data[9] & 0xFF, length - 10);
            statusText = new String(data, 10, n, StandardCharsets.UTF_8);
        } else if (type == 2) {
            GameState parsed = parseState(data, length);
            if (parsed != null) {
                state = parsed;
                for (int i=0;i<8;i++) {
                    int skillId = parsed.hotkeySkills[i];
                    if (skillId > 0 && skillId < 0xFFFF) setSkillBinding(i, skillId, parsed.hotkeyLeft[i]);
                }
            }
        }
    }

    static GameState parseState(byte[] data, int length) {
        if (length < 59 || data[0] != 'D' || data[1] != '2' || data[2] != 'A' || data[3] != 'M'
                || (data[4] & 0xFF) != 2 || (data[5] & 0xFF) != 2) return null;
        try {
            ByteBuffer b = ByteBuffer.wrap(data, 0, length).order(ByteOrder.LITTLE_ENDIAN);
            b.position(8);
            int width=u16(b), height=u16(b);
            b.getShort(); b.getShort(); // pointer
            b.getInt(); b.getInt(); // UI flags
            b.getShort(); b.get(); b.get(); // level, town, flags
            int playerX=b.getShort(), playerY=b.getShort();
            b.get(); b.get(); // life, mana
            int hoverType=b.get() & 0xFF;
            long hoverId=b.getInt() & 0xFFFFFFFFL;
            b.get(); b.get(); // last drink, command count
            b.position(b.position()+16); // belt
            b.get(); // raw monster count
            int count=b.get() & 0xFF;
            if (count > 40) return null;
            List<Monster> monsters=new ArrayList<>(count);
            for (int i=0;i<count;i++) {
                if (b.remaining()<11) return null;
                long id=b.getInt() & 0xFFFFFFFFL;
                int x=b.getShort(), y=b.getShort(), hp=b.get() & 0xFF, flags=b.get() & 0xFF;
                int nameLength=b.get() & 0xFF;
                if (nameLength>b.remaining()) return null;
                byte[] name=new byte[nameLength]; b.get(name);
                monsters.add(new Monster(id,x,y,hp,flags,new String(name,StandardCharsets.UTF_8)));
            }
            List<Thing> items=parseThings(b);
            List<Thing> objects=parseThings(b);
            int[] hotkeys = new int[16];
            boolean[] left = new boolean[16];
            java.util.Arrays.fill(hotkeys, -1);
            if (b.hasRemaining()) {
                b.get(); // character class
                b.getShort(); b.getShort(); // current left/right skills
                for (int i=0;i<16;i++) {
                    if (b.remaining()<3) return null;
                    int skill = b.getShort() & 0xFFFF;
                    hotkeys[i] = skill == 0xFFFF ? -1 : skill;
                    left[i] = (b.get() & 0xFF) != 0;
                }
            }
            return new GameState(width,height,playerX,playerY,hoverType,hoverId,monsters,items,objects,hotkeys,left,System.currentTimeMillis());
        } catch (RuntimeException e) { return null; }
    }

    private static List<Thing> parseThings(ByteBuffer b) {
        if (!b.hasRemaining()) throw new IllegalArgumentException("Missing unit list");
        int count = b.get() & 0xFF;
        if (count > 128 || b.remaining() < count * 10) throw new IllegalArgumentException("Invalid unit list");
        List<Thing> result = new ArrayList<>(count);
        for (int i=0;i<count;i++) {
            long id=b.getInt() & 0xFFFFFFFFL;
            int x=b.getShort(), y=b.getShort(), kind=b.get() & 0xFF, extra=b.get() & 0xFF;
            result.add(new Thing(id,x,y,kind,extra));
        }
        return result;
    }

    private void send(byte[] bytes) {
        final DatagramSocket s = socket;
        if (s == null || bytes == null) return;
        sender.execute(() -> {
            try { s.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("127.0.0.1"), MOD_PORT)); }
            catch (IOException ignored) {}
        });
    }

    private void sendDirect(byte[] bytes) {
        DatagramSocket s = socket;
        if (s == null || bytes == null) return;
        try { s.send(new DatagramPacket(bytes, bytes.length, InetAddress.getByName("127.0.0.1"), MOD_PORT)); }
        catch (IOException ignored) {}
    }

    private static float clamp(float v) { return Float.isFinite(v) ? Math.max(-1f, Math.min(1f, v)) : 0f; }

    @Override public void close() {
        if (walking) sendDirect(D2CommandPacket.walk(D2CommandPacket.PHASE_UP, 0, 0));
        if (attackHeld) {
            GameState current = freshState();
            if (current != null) {
                long id = targetId >= 0 ? targetId : current.hoverId;
                int type = targetId >= 0 ? targetType : current.hoverType;
                if (id != 0xFFFFFFFFL && type >= 0)
                    sendDirect(D2CommandPacket.unit(D2CommandPacket.BUTTON_LEFT,
                            D2CommandPacket.PHASE_UP, type, id));
            }
        }
        receiver = null;
        attackHeld = false;
        walking = false;
        DatagramSocket s = socket;
        if (s != null) s.close();
        synchronized (lock) {
            if (attackKeepAlive != null) attackKeepAlive.cancel(false);
            attackKeepAlive = null;
        }
        sender.shutdownNow();
    }
}

package com.winlator.star.d2android;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Wire-compatible encoder for the D2Android game-module command datagrams.
 *
 * The packet format is deliberately isolated from UI code. The 1.13c native module is built
 * separately and must report its verified Game.exe/D2Common fingerprints before commands enable.
 */
public final class D2CommandPacket {
    public static final int PROTOCOL_VERSION = 1;
    public static final int CMD_DRINK = 1;
    public static final int CMD_UNIT = 2;
    public static final int CMD_HIGHLIGHT = 3;
    public static final int CMD_WALK = 4;
    public static final int CMD_PORTAL = 7;
    public static final int CMD_NPC = 8;
    public static final int CMD_SELECT_SKILL = 13;
    public static final int CMD_TAKE = 15;
    public static final int CMD_KEY_PRESS = 36; // D2Android extension: synthesize one guest key tap.
    public static final int CMD_GROUND_SKILL = 37; // D2Android extension: directional ground cast.
    public static final int CMD_TAP = 38; // D2Android extension: direct touch-to-click.

    public static final int BUTTON_LEFT = 0;
    public static final int BUTTON_RIGHT = 1;
    public static final int PHASE_DOWN = 0;
    public static final int PHASE_HOLD = 1;
    public static final int PHASE_UP = 2;
    public static final int PHASE_CLICK = 3;

    private D2CommandPacket() {}

    private static ByteBuffer command(int command, int payloadBytes) {
        ByteBuffer out = ByteBuffer.allocate(8 + payloadBytes).order(ByteOrder.LITTLE_ENDIAN);
        out.put((byte) 'D').put((byte) '2').put((byte) 'A').put((byte) 'C');
        out.put((byte) PROTOCOL_VERSION).put((byte) command).putShort((short) 0);
        return out;
    }

    public static byte[] walk(int phase, int dx, int dy) {
        if (phase < PHASE_DOWN || phase > PHASE_UP) throw new IllegalArgumentException("Invalid walk phase");
        return command(CMD_WALK, 6).put((byte) phase).put((byte) 0)
                .putShort((short) clampShort(dx)).putShort((short) clampShort(dy)).array();
    }

    public static byte[] selectSkill(boolean left, int skillId) {
        if (skillId < 0 || skillId > 0xFFFF) throw new IllegalArgumentException("Invalid skill id");
        return command(CMD_SELECT_SKILL, 3).put((byte) (left ? 0 : 1))
                .putShort((short) skillId).array();
    }

    public static byte[] unit(int button, int phase, int unitType, long unitId) {
        if (button != BUTTON_LEFT && button != BUTTON_RIGHT) throw new IllegalArgumentException("Invalid mouse button");
        if (phase < PHASE_DOWN || phase > PHASE_CLICK) throw new IllegalArgumentException("Invalid unit phase");
        return command(CMD_UNIT, 12).put((byte) button).put((byte) phase).put((byte) 0).put((byte) 0)
                .putInt(unitType).putInt((int) unitId).array();
    }

    public static byte[] highlight(int unitType, long unitId) {
        return command(CMD_HIGHLIGHT, 8).putInt(unitType).putInt((int) unitId).array();
    }

    public static byte[] take(int unitType, long unitId) {
        return command(CMD_TAKE, 8).put((byte) unitType).put((byte) 0).put((byte) 0).put((byte) 0)
                .putInt((int) unitId).array();
    }

    public static byte[] sortInventory(boolean charmsFixed) {
        return command(CMD_SORT, 2).put((byte) 0).put((byte) (charmsFixed ? 1 : 0)).array();
    }

    public static byte[] keyPress(int virtualKey) {
        if (virtualKey < 1 || virtualKey > 0xFF) throw new IllegalArgumentException("Invalid virtual key");
        return command(CMD_KEY_PRESS, 1).put((byte) virtualKey).array();
    }

    public static byte[] tap(int button, int phase, int x, int y) {
        if (button < 0 || button > 1 || phase < 0 || phase > 3)
            throw new IllegalArgumentException("Invalid tap action");
        return command(CMD_TAP, 6).put((byte)button).put((byte)phase)
                .putShort((short)x).putShort((short)y).array();
    }

    public static byte[] groundSkill(int button, int phase, int dx, int dy) {
        if (button < 0 || button > 1 || phase < 0 || phase > 3)
            throw new IllegalArgumentException("Invalid ground skill action");
        return command(CMD_GROUND_SKILL, 6).put((byte)button).put((byte)phase)
                .putShort((short)dx).putShort((short)dy).array();
    }

    public static byte[] portal() {
        return command(CMD_PORTAL, 0).array();
    }

    private static int clampShort(int value) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}

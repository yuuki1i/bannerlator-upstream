package com.winlator.star.d2android;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Wire-compatible encoder for the D2Android game-module command datagrams.
 *
 * The packet format is deliberately isolated from UI code. It is not a claim that the
 * upstream 1.13d native module is compatible with 1.13c; the native build gate remains closed
 * until a separately verified 1.13c module is available.
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

    public static byte[] portal() {
        return command(CMD_PORTAL, 0).array();
    }

    private static int clampShort(int value) {
        return Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
    }
}

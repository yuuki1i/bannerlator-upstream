package com.winlator.star.d2android;

import org.junit.Test;
import static org.junit.Assert.*;

public class D2CommandPacketTest {
    @Test public void walkPacketMatchesGameModuleWireLayout() {
        byte[] packet = D2CommandPacket.walk(D2CommandPacket.PHASE_HOLD, 0x1234, -2);
        assertArrayEquals(new byte[] {
                'D','2','A','C',1,4,0,0,
                1,0,0x34,0x12,(byte)0xFE,(byte)0xFF
        }, packet);
    }

    @Test public void skillPacketEncodesLeftAndRightAndLittleEndianId() {
        assertArrayEquals(new byte[] {'D','2','A','C',1,13,0,0,0,0x34,0x12},
                D2CommandPacket.selectSkill(true, 0x1234));
        assertArrayEquals(new byte[] {'D','2','A','C',1,13,0,0,1,0x34,0x12},
                D2CommandPacket.selectSkill(false, 0x1234));
    }

    @Test public void unitPacketCarriesTargetTypeAndId() {
        byte[] packet = D2CommandPacket.unit(D2CommandPacket.BUTTON_RIGHT,
                D2CommandPacket.PHASE_DOWN, 1, 0x12345678L);
        assertArrayEquals(new byte[] {
                'D','2','A','C',1,2,0,0,
                1,0,0,0, 1,0,0,0, 0x78,0x56,0x34,0x12
        }, packet);
    }

    @Test public void pickupSortAndPanelHotkeyPacketsUseExpectedCommands() {
        assertArrayEquals(new byte[] {'D','2','A','C',1,15,0,0,4,0,0,0,0x78,0x56,0x34,0x12},
                D2CommandPacket.take(4, 0x12345678L));
        assertArrayEquals(new byte[] {'D','2','A','C',1,24,0,0,0,1},
                D2CommandPacket.sortInventory(true));
        assertArrayEquals(new byte[] {'D','2','A','C',1,36,0,0,0x49},
                D2CommandPacket.keyPress(0x49));
        assertArrayEquals(new byte[] {'D','2','A','C',1,37,0,0,1,0,100,0,(byte)0x9c,(byte)0xff},
                D2CommandPacket.groundSkill(1,0,100,-100));
    }

    @Test(expected = IllegalArgumentException.class)
    public void invalidWalkPhaseIsRejected() {
        D2CommandPacket.walk(3, 0, 0);
    }

    @Test(expected = IllegalArgumentException.class)
    public void skillIdsCannotOverflowProtocolField() {
        D2CommandPacket.selectSkill(true, 65536);
    }
}

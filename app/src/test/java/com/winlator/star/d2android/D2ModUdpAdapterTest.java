package com.winlator.star.d2android;

import org.junit.Test;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import static org.junit.Assert.*;

public class D2ModUdpAdapterTest {
    private static byte[] statePacket() {
        ByteBuffer b = ByteBuffer.allocate(160).order(ByteOrder.LITTLE_ENDIAN);
        b.put((byte)'D').put((byte)'2').put((byte)'A').put((byte)'M').put((byte)3).put((byte)2).putShort((short)1);
        b.putShort((short)800).putShort((short)600);
        b.putShort((short)0).putShort((short)0);
        b.putInt(0).putInt(0);
        b.putShort((short)1).put((byte)0).put((byte)5);
        b.putShort((short)300).putShort((short)250);
        b.put((byte)90).put((byte)80);
        b.put((byte)1).putInt(0x12345678);
        b.put((byte)0).put((byte)0);
        for (int i=0;i<16;i++) b.put((byte)0);
        b.put((byte)1).put((byte)1); // raw count, monster count
        b.putInt(0x10203040).putShort((short)340).putShort((short)260);
        b.put((byte)75).put((byte)2).put((byte)4).put(new byte[]{'O','r','c','1'});
        b.put((byte)1); // item list
        b.putInt(0x00000100).putShort((short)320).putShort((short)260).putShort((short)4).put((byte)0);
        b.put((byte)1); // object list
        b.putInt(0x00000200).putShort((short)360).putShort((short)260).putShort((short)267).put((byte)0);
        b.put((byte)0).putShort((short)0xFFFF).putShort((short)0xFFFF); // class, left/right skill
        for (int i=0;i<16;i++) b.putShort((short)(i<8 ? 0x0100+i : 0xFFFF)).put((byte)(i%2));
        return Arrays.copyOf(b.array(), b.position());
    }

    @Test public void statusHandshakeExposesVerifiedGameFingerprint() {
        String gameHash = "7baeda5e815fbe7a807d6a102aada579cd7a1b89383dd4a9b5d92f887ef165bf";
        String status = "D2Android-1.13c-native;game=" + gameHash + ";common=verified;client=verified";
        byte[] packet = new byte[10 + status.length()];
        packet[0]='D'; packet[1]='2'; packet[2]='A'; packet[3]='M';
        packet[4]=3; packet[5]=1; packet[8]=1; packet[9]=(byte)status.length();
        System.arraycopy(status.getBytes(java.nio.charset.StandardCharsets.UTF_8),0,packet,10,status.length());
        D2ModUdpAdapter adapter = new D2ModUdpAdapter();
        adapter.handlePacket(packet,packet.length);
        assertTrue(adapter.isModuleHooked());
        assertEquals(gameHash,adapter.getVerifiedGameSha256());
        adapter.close();
    }

    @Test public void parsesStateAndMonsterTarget() {
        byte[] packet = statePacket();
        D2ModUdpAdapter.GameState state = D2ModUdpAdapter.parseState(packet, packet.length);
        assertNotNull(state);
        assertEquals(800, state.width);
        assertEquals(600, state.height);
        assertTrue(state.inGame);
        assertFalse(state.inTown);
        assertEquals(300, state.playerX);
        assertEquals(250, state.playerY);
        assertEquals(1, state.hoverType);
        assertEquals(0x12345678L, state.hoverId);
        assertEquals(1, state.monsters.size());
        assertEquals(0x10203040L, state.monsters.get(0).id);
        assertEquals("Orc1", state.monsters.get(0).name);
        assertEquals(1, state.items.size());
        assertEquals(0x100L, state.items.get(0).id);
        assertEquals(1, state.objects.size());
        assertEquals(267, state.objects.get(0).kind);
        assertEquals(0x0103, state.hotkeySkills[3]);
        assertTrue(state.hotkeyLeft[3]);
        assertEquals(-1, state.hotkeySkills[9]);
    }

    @Test public void rejectsWrongProtocolAndTruncatedState() {
        byte[] packet = statePacket();
        packet[4] = 1;
        assertNull(D2ModUdpAdapter.parseState(packet, packet.length));
        packet[4] = 2;
        assertNull(D2ModUdpAdapter.parseState(packet, 40));
    }

    @Test public void targetRankingPrefersAimDirectionAndSkipsDeadMonsters() {
        D2ModUdpAdapter.Monster ahead = new D2ModUdpAdapter.Monster(1, 350, 250, 80, 0, "ahead");
        D2ModUdpAdapter.Monster behind = new D2ModUdpAdapter.Monster(2, 200, 250, 90, 0, "behind");
        D2ModUdpAdapter.Monster dead = new D2ModUdpAdapter.Monster(3, 310, 250, 0, 0, "dead");
        List<D2ModUdpAdapter.Monster> ranked = D2TargetSelector.rank(
                Arrays.asList(behind, dead, ahead), 300, 250, 1f, 0f);
        assertEquals(1, ranked.size());
        assertEquals(1L, ranked.get(0).id);
    }
}

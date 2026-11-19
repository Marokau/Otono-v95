package kinoko.packet.field;

import kinoko.provider.ItemProvider;
import kinoko.script.party.KerningPQRewards;
import kinoko.server.field.InstanceRewards;
import kinoko.server.header.InHeader;
import kinoko.server.header.OutHeader;
import kinoko.server.netty.ChannelPacketHandler;
import kinoko.server.netty.PacketHandler;
import kinoko.server.packet.NioBufferInPacket;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class PqRewardPacketTest {
    @BeforeAll static void items() { ItemProvider.initialize(); }

    @Test void showWindowContainsCharacterAndExactlySixNormalBoxes() {
        var packet = new NioBufferInPacket(PqRewardPacket.show(42).getData());
        assertEquals(OutHeader.UserShowPQReward.getValue(), packet.decodeShort());
        assertEquals(42, packet.decodeInt());
        assertArrayEquals(new byte[6], packet.decodeArray(6));
        assertEquals(0, packet.getRemaining());
    }

    @Test void revealHasSixIndexedPrizesAndExtraFieldsOnlyForEquipment() {
        var prizes = List.of(new InstanceRewards.Prize(1032009,1), new InstanceRewards.Prize(2000001,1),
                new InstanceRewards.Prize(4010000,1), new InstanceRewards.Prize(1002026,1),
                new InstanceRewards.Prize(2040508,1), new InstanceRewards.Prize(4003000,1));
        var packet = new NioBufferInPacket(PqRewardPacket.received(prizes).getData());
        assertEquals(OutHeader.PartyResult.getValue(),packet.decodeShort());
        assertEquals(42,packet.decodeByte());
        for (int box = 0; box < 6; box++) {
            assertEquals(box,packet.decodeByte());
            assertEquals(prizes.get(box).itemId(),packet.decodeInt());
            if (prizes.get(box).itemId()/1_000_000 == 1) assertArrayEquals(new byte[7],packet.decodeArray(7));
        }
        assertEquals(0,packet.getRemaining());
    }

    @Test void rewardPoolContainsOnlyValidNativeV95ItemsAndBothRequestsAreRegistered() throws Exception {
        for (int itemId : KerningPQRewards.ITEM_IDS) assertTrue(ItemProvider.getItemInfo(itemId).isPresent(), "Missing item " + itemId);
        var handler = new ChannelPacketHandler();
        var field = PacketHandler.class.getDeclaredField("handlerMap");
        field.setAccessible(true);
        Map<?,?> map = (Map<?,?>) field.get(handler);
        assertTrue(map.containsKey(InHeader.UserSelectPQReward));
        assertTrue(map.containsKey(InHeader.UserRequestPQReward));
    }
}

package kinoko.packet.field;

import kinoko.server.field.InstanceRewards;
import kinoko.server.header.OutHeader;
import kinoko.server.packet.OutPacket;
import kinoko.server.party.PartyResultType;
import kinoko.packet.world.PartyPacket;

import java.util.List;

/** v95.1 layouts verified against CUser::OnShowPQReward and the native PQ reward UI. */
public final class PqRewardPacket {
    private PqRewardPacket() {}

    public static OutPacket show(int characterId) {
        var packet = OutPacket.of(OutHeader.UserShowPQReward);
        packet.encodeInt(characterId); // CUserPool resolves the user before dispatching.
        for (int i = 0; i < 6; i++) packet.encodeByte(0); // six normal treasure boxes
        return packet;
    }

    public static OutPacket selected(int characterId, String name, int box) {
        var packet = PartyPacket.of(PartyResultType.SuccessToSelectPQReward);
        packet.encodeInt(characterId);
        packet.encodeString(name);
        packet.encodeByte(box);
        return packet;
    }

    public static OutPacket selectionFailed() {
        var packet = PartyPacket.of(PartyResultType.FailToSelectPQReward);
        packet.encodeByte(1); // reset the pending selection; the box was unavailable
        return packet;
    }

    public static OutPacket claimFailed() {
        return PartyPacket.of(PartyResultType.FailToRequestPQReward);
    }

    public static OutPacket received(List<InstanceRewards.Prize> prizes) {
        if (prizes.size() != 6) throw new IllegalArgumentException("The v95 UI decodes exactly six boxes");
        var packet = PartyPacket.of(PartyResultType.ReceivePQReward);
        for (int box = 0; box < 6; box++) {
            int itemId = prizes.get(box).itemId();
            packet.encodeByte(box);
            packet.encodeInt(itemId);
            if (itemId / 1_000_000 == 1) {
                // Plain, unpotentialled equipment. These are grade/options, not the complete item encoding.
                packet.encodeByte(0);
                packet.encodeShort(0);
                packet.encodeShort(0);
                packet.encodeShort(0);
            }
        }
        return packet;
    }
}

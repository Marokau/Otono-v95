package kinoko.handler.user;

import kinoko.handler.Handler;
import kinoko.script.party.KerningPQRewards;
import kinoko.server.header.InHeader;
import kinoko.server.packet.InPacket;
import kinoko.world.user.User;

public final class PqRewardHandler {
    @Handler(InHeader.UserSelectPQReward)
    public static void select(User user, InPacket packet) { KerningPQRewards.select(user, packet); }

    @Handler(InHeader.UserRequestPQReward)
    public static void claim(User user, InPacket packet) { KerningPQRewards.claim(user, packet); }
}

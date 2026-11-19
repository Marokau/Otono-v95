package kinoko.script.custom;

import kinoko.script.common.Script;
import kinoko.script.common.ScriptHandler;
import kinoko.script.common.ScriptManager;
import kinoko.server.field.InstanceFieldStorage;
import kinoko.script.party.KerningPQ;
import java.util.Map;

/** Small v95 activity guide. Only implemented destinations are listed. */
public final class WhereLevel extends ScriptHandler {
    @Script("otono_wherelevel")
    public static void guide(ScriptManager sm) {
        if (sm.getUser().isAutomated()) return;
        while (true) {
            int main = sm.askMenu("#eOtono WhereLevel#n\r\nChoose an activity guide.\r\n", Map.of(0,"Party Quests",1,"Close"));
            if (main != 0) return;
            while (true) {
                int activity = sm.askMenu("#eParty Quests#n\r\nChoose an activity for its summary and entrance teleport.\r\n",
                        Map.of(0,"Kerning City PQ: Levels 20-200, 3-4 players",1,"Main menu"));
                if (activity == 1) break;
                int choice = sm.askMenu("#eKerning City Party Quest#n\r\n#bLevels:#k 20-200\r\n#bParty size:#k 3-4\r\n#bTime limit:#k 30 minutes\r\n\r\n" +
                        "Work together on Cloto's coupon missions, rope and platform puzzles, then defeat King Slime. " +
                        "Speak with Lakelis in the lobby to enter. Normal entry requirements apply.\r\n\r\n",
                        Map.of(0,"Teleport to the KPQ lobby",1,"Back"));
                if (choice != 0) continue;
                if (sm.getField().getFieldStorage() instanceof InstanceFieldStorage) {
                    sm.sayOk("Please leave your current activity normally before traveling to another entrance.");
                    return;
                }
                if (sm.getUser().getHp() <= 0) {
                    sm.sayOk("Please revive before traveling.");
                    return;
                }
                sm.warp(KerningPQ.LOBBY);
                return;
            }
        }
    }
}

package kinoko.script.party;

import kinoko.script.common.ScriptManager;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class KerningPQTest {
    static final class Run {
        final Map<String, String> state = new HashMap<>();
        final Map<Integer, Integer> items = new HashMap<>();
        final List<String> messages = new ArrayList<>();
        boolean room = true;
        int expAwards;
        int warps;
        int destination;
        int fieldId = KerningPQ.STAGE_1;
        boolean leave = true;
        final ScriptManager sm = (ScriptManager) Proxy.newProxyInstance(
                ScriptManager.class.getClassLoader(), new Class[]{ScriptManager.class}, (proxy, method, args) -> {
                    if (method.isDefault()) return InvocationHandler.invokeDefault(proxy, method, args);
                    return switch (method.getName()) {
                        case "getInstanceVariable" -> state.getOrDefault((String) args[0], "");
                        case "setInstanceVariable" -> { state.put((String) args[0], (String) args[1]); yield null; }
                        case "getInstanceUserCount" -> 3;
                        case "getItemCount" -> items.getOrDefault((int) args[0], 0);
                        case "removeItem" -> {
                            int id = (int) args[0], count = (int) args[1];
                            if (items.getOrDefault(id, 0) < count) yield false;
                            items.merge(id, -count, Integer::sum);
                            yield true;
                        }
                        case "canAddItems" -> room;
                        case "addItems" -> {
                            @SuppressWarnings("unchecked")
                            var rewards = (List<kinoko.util.Tuple<Integer, Integer>>) args[0];
                            for (var reward : rewards) items.merge(reward.getLeft(), reward.getRight(), Integer::sum);
                            yield true;
                        }
                        case "sayOk", "sayNext" -> { messages.add((String) args[0]); yield null; }
                        case "addExpAll" -> { expAwards++; yield null; }
                        case "setObjectState", "broadcastPacket", "broadcastScreenEffect", "broadcastSoundEffect", "broadcastScriptProgressMessage" -> null;
                        case "askYesNo" -> leave;
                        case "getFieldId" -> fieldId;
                        case "warp" -> { warps++; destination = (int) args[0]; yield null; }
                        default -> throw new AssertionError("Unexpected script call: " + method.getName());
                    };
                });
    }

    @Test
    void passIsARealItemAndCannotBeEarnedTwice() {
        Run run = new Run();
        run.state.put("coupon_mission_42", "7");
        run.items.put(KerningPQ.COUPON, 7);
        KerningPQ.stageOne(run.sm, false, 42);
        assertEquals(0, run.items.get(KerningPQ.COUPON));
        assertEquals(1, run.items.get(KerningPQ.PASS));
        assertEquals("clear", run.state.get("coupon_mission_42"));
        run.items.put(KerningPQ.COUPON, 7);
        KerningPQ.stageOne(run.sm, false, 42);
        assertEquals(1, run.items.get(KerningPQ.PASS));
        assertEquals(7, run.items.get(KerningPQ.COUPON));
    }

    @Test
    void wrongCouponCountOrFullInventoryDoesNotConsumeCoupons() {
        for (int count : new int[]{6, 8}) {
            Run run = new Run();
            run.state.put("coupon_mission_42", "7");
            run.items.put(KerningPQ.COUPON, count);
            KerningPQ.stageOne(run.sm, false, 42);
            assertEquals(count, run.items.get(KerningPQ.COUPON));
            assertFalse(run.items.containsKey(KerningPQ.PASS));
        }
        Run run = new Run();
        run.state.put("coupon_mission_42", "7");
        run.items.put(KerningPQ.COUPON, 7);
        run.room = false;
        KerningPQ.stageOne(run.sm, false, 42);
        assertEquals(7, run.items.get(KerningPQ.COUPON));
        assertEquals("7", run.state.get("coupon_mission_42"));
    }

    @Test
    void leaderNeedsPhysicalPassesAndCannotRepeatTheClearReward() {
        Run run = new Run();
        run.state.put("required_passes", "3");
        run.state.put("stage1_intro", "1");
        run.items.put(KerningPQ.PASS, 2);
        KerningPQ.stageOne(run.sm, true, 1);
        assertFalse(run.state.containsKey("stage1_gate"));
        assertEquals(0, run.expAwards);
        run.items.put(KerningPQ.PASS, 3);
        KerningPQ.stageOne(run.sm, true, 1);
        assertEquals("1", run.state.get("stage1_gate"));
        assertEquals(0, run.items.get(KerningPQ.PASS));
        assertEquals(1, run.expAwards);
        KerningPQ.stageOne(run.sm, true, 1);
        assertEquals(1, run.expAwards);
    }

    @Test
    void cancelledMissionDialogueDoesNotRerollItsTarget() {
        Run run = new Run();
        KerningPQ.stageOne(run.sm, false, 42);
        String target = run.state.get("coupon_mission_42");
        assertTrue(Integer.parseInt(target) >= 5 && Integer.parseInt(target) <= 20);
        KerningPQ.stageOne(run.sm, false, 42);
        assertEquals(target, run.state.get("coupon_mission_42"));
    }

    @Test
    void portalRequiresClearedStage() {
        Run run = new Run();
        KerningPQ.party_portal(run.sm);
        assertEquals(0, run.warps);
        run.state.put("stage1_gate", "1");
        KerningPQ.party_portal(run.sm);
        assertEquals(KerningPQ.STAGE_2, run.destination);
    }

    @Test
    void leavingRemovesStageItemsAndExitReturnsToLobby() {
        Run run = new Run();
        run.items.put(KerningPQ.COUPON, 9);
        run.items.put(KerningPQ.PASS, 2);
        run.items.put(KerningPQ.PUPPET, 1);
        run.leave = false;
        KerningPQ.party1_out(run.sm);
        assertEquals(0, run.warps);
        assertEquals(9, run.items.get(KerningPQ.COUPON));
        run.leave = true;
        KerningPQ.party1_out(run.sm);
        assertEquals(KerningPQ.EXIT, run.destination);
        assertTrue(run.items.values().stream().allMatch(count -> count == 0));
        run.fieldId = KerningPQ.EXIT;
        KerningPQ.party1_out(run.sm);
        assertEquals(KerningPQ.LOBBY, run.destination);
    }
}

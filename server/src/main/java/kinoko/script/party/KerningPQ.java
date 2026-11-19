package kinoko.script.party;

import kinoko.packet.world.WvsContext;
import kinoko.bot.KpqCrewService;
import kinoko.packet.world.MessagePacket;
import kinoko.server.field.Instance;
import kinoko.server.field.InstanceFieldStorage;
import kinoko.provider.ItemProvider;
import kinoko.world.field.mob.Mob;
import kinoko.script.UnityPortal;
import kinoko.script.common.Script;
import kinoko.script.common.ScriptHandler;
import kinoko.script.common.ScriptManager;
import kinoko.script.common.ScriptManagerImpl;
import kinoko.util.Util;
import kinoko.world.field.Field;
import kinoko.world.quest.QuestRecordType;
import kinoko.world.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public final class KerningPQ extends ScriptHandler {
    public static final int EXIT = 910340000;
    public static final int STAGE_1 = 910340100;
    public static final int STAGE_2 = 910340200;
    public static final int STAGE_3 = 910340300;
    public static final int STAGE_4 = 910340400;
    public static final int STAGE_5 = 910340500;
    public static final int BONUS = 910340600;

    public static final int COUPON = 4001007;
    public static final int PASS = 4001008;
    public static final int PUPPET = 4001454;
    public static final int LOBBY = 910340700;

    @Script("party_exit")
    public static void party_exit(ScriptManager sm) {
        // Hidden Street : First Time Together Lobby (910340700)
        //   out00 (-259, 158)
        UnityPortal.returnPortal(sm, 103000000, null); // Kerning City : Kerning City
    }

    @Script("party_portal")
    public static void party_portal(ScriptManager sm) {
        // Hidden Street : First Time Together <1st Stage> (910340100)
        //   next000 (716, 106)
        // Hidden Street : First Time Together <2nd Stage> (910340200)
        //   next000 (-218, 91)
        // Hidden Street : First Time Together <3rd Stage> (910340300)
        //   next000 (1331, -122)
        // Hidden Street : First Time Together <4th Stage> (910340400)
        //   next000 (1655, 118)
        switch (sm.getFieldId()) {
            case STAGE_1 -> {
                if (sm.getInstanceVariable("stage1_gate").equals("1")) {
                    sm.warp(STAGE_2, "st00");
                }
            }
            case STAGE_2 -> {
                if (sm.getInstanceVariable("stage2_gate").equals("1")) {
                    sm.warp(STAGE_3, "st00");
                }
            }
            case STAGE_3 -> {
                if (sm.getInstanceVariable("stage3_gate").equals("1")) {
                    sm.warp(STAGE_4, "st00");
                }
            }
            case STAGE_4 -> {
                if (sm.getInstanceVariable("stage4_gate").equals("1")) {
                    sm.warp(STAGE_5, "st00");
                }
            }
        }
    }

    @Script("party1_enter")
    public static void party1_enter(ScriptManager sm) {
        // Lakelis (9020000)
        //   Kerning City : Kerning City (103000000)
        //   Hidden Street : First Time Together Lobby (910340700)
        if (sm.getFieldId() == 100000200 || sm.getFieldId() == 103000000) {
            // Henesys : Henesys Park
            if (sm.askMenu(PqDialogue.KPQ_CITY, Map.of(0, "Go to the First Accompaniment Lobby.")) == 0) {
                sm.setQRValue(QuestRecordType.UnityPortal, "");
                sm.warp(910340700); // Hidden Street : First Time Together Lobby
            }
        } else if (sm.getFieldId() == 910340700) {
            final int answer = sm.askMenu(PqDialogue.KPQ_LOBBY, Map.of(
                    0, "I want to do the Party Quest.",
                    1, "I want to find party members who will join me.",
                    2, "I want to hear the details.",
                    3, "\r\n#eBotPQ Menu#n"
            ));
            if (answer == 3) {
                int crewChoice = sm.askMenu("#eBotPQ Menu#n\r\nAssemble three prepared party members. You will lead the party, check the puzzles and collect your rewards.\r\n",
                        Map.of(0, "Assemble a KPQ bot party for me", 1, "Dismiss my KPQ crew"));
                if (crewChoice == 0) sm.sayOk(KpqCrewService.assemble(sm.getUser()));
                else if (crewChoice == 1) { KpqCrewService.dismiss(sm.getUser()); sm.sayOk("Your KPQ crew has been dismissed."); }
                return;
            }
            if (answer == 0) {
                if (KpqCrewService.hasCrew(sm.getUser()) && !KpqCrewService.stageAutomationReady()) {
                    sm.sayOk("This training crew is not ready to complete KPQ yet. Please inspect it in the lobby for now.");
                    return;
                }
                if (!sm.getUser().isPartyBoss()) {
                    sm.sayOk("If you'd like to enter here, the leader of your party will have to talk to me. Talk to your party leader about this.");
                    return;
                }
                final List<User> members = sm.getField().getUserPool().getPartyMembers(sm.getUser().getPartyId());
                if (members.size() < 3 || members.size() > 4 || !sm.checkParty(3, member -> member.getLevel() >= 20 && member.getLevel() <= 200 && member.getHp() > 0)) {
                    sm.sayOk("Bring 3-4 living party members, all at levels 20-200, to this lobby before entering.");
                    return;
                }
                KpqCrewService.prepareEntry(sm.getUser());
                // Clean every entering member, not just the leader, before creating a fresh run.
                for (User member : members) {
                    final ScriptManager memberScript = new ScriptManagerImpl(member, sm.getField(), member, "party1_enter", 9020000);
                    removeStageItems(memberScript);
                }
                sm.partyWarpInstance(List.of(
                        STAGE_1,
                        STAGE_2,
                        STAGE_3,
                        STAGE_4,
                        STAGE_5
                ), "st00", EXIT, 60 * 30, Map.of("required_passes", String.valueOf(members.size() - 1)));
                final Instance instance = ((InstanceFieldStorage) sm.getUser().getField().getFieldStorage()).getInstance();
                instance.setRestrictedItems(List.of(COUPON, PASS, PUPPET));
                instance.setProtectedDrops(List.of(1072369));
                instance.setVariable("kpq", "1");
                instance.setVariable("party_id", String.valueOf(sm.getUser().getPartyId()));
                instance.setVariable("roster", members.stream().map(member -> String.valueOf(member.getCharacterId())).collect(Collectors.joining(",")));
                instance.setVariable("ready_after", String.valueOf(Instant.now().plusSeconds(5).getEpochSecond()));
                instance.setUpdateHandler(KerningPQ::updateInstance);
                instance.setMobDefeatedHandler(mob -> mobDefeated(instance, mob));
            } else if (answer == 1) {
                sm.sayOk("Invite other adventurers to your party here. Otono also offers prepared companions under the BotPQ Menu.");
            } else if (answer == 2) {
                sm.sayOk("I'm waiting for brave adventurers. Please work together, share your strengths and wisdom to solve the challenges, and defeat the vicious #rKing Slime#k! King Slime will appear when you complete the challenges. You will need to find the right location and collect Passes corresponding to the answer to the quiz.\r\n\r\n#e - Level:#n 20 - 200 #r(Recommended Level: 20 - 29)#k\r\n#e - Time Limit:#n 30 min.\r\n#e - Players:#n 3 - 4\r\n#e - Reward:#n #v1072369# #t1072369# #b(Dropped by King Slime)#k\r\nVarious Use, Etc, and Equip items");
            }
        }
    }

    @Script("party1_play")
    public static void party1_play(ScriptManager sm) {
        // Cloto (9020001)
        //   Hidden Street : First Time Together <1st Stage> (103000800)
        //   Hidden Street : First Time Together <2nd Stage> (103000801)
        //   Hidden Street : First Time Together <3rd Stage> (103000802)
        //   Hidden Street : First Time Together <4th stage> (103000803)
        //   Hidden Street : First Time Together <Last Stage> (103000804)
        //   Hidden Street : First Time Together <1st Stage> (910340100)
        //   Hidden Street : First Time Together <1st Stage> (910340101)
        //   Hidden Street : First Time Together <2nd Stage> (910340200)
        //   Hidden Street : First Time Together <2nd Stage> (910340201)
        //   Hidden Street : First Time Together <3rd Stage> (910340300)
        //   Hidden Street : First Time Together <3rd Stage> (910340301)
        //   Hidden Street : First Time Together <4th Stage> (910340400)
        //   Hidden Street : First Time Together <4th Stage> (910340401)
        //   Hidden Street : First Time Together <Last Stage> (910340500)
        //   Hidden Street : First Time Together <Last Stage> (910340501)
        final User user = sm.getUser();
        final Field field = sm.getField();
        switch (field.getFieldId()) {
            case STAGE_1 -> stageOne(sm, user.isPartyBoss(), user.getCharacterId());
            case STAGE_2 -> {
                if (sm.getInstanceVariable("stage2_gate").equals("1") || !user.isPartyBoss()) {
                    return;
                }
                // Introduction
                final String answer = sm.getInstanceVariable("stage2_answer");
                if (answer.isEmpty()) {
                    final List<String> list = new ArrayList<>(List.of("1", "1", "1", "0"));
                    Collections.shuffle(list);
                    sm.setInstanceVariable("stage2_answer", String.join("", list));
                    sm.sayNext(PqDialogue.KPQ_ROPES);
                    return;
                }
                // Check answer
                if (!answer.equals(sm.getAreaCheck())) {
                    sm.setInstanceVariable("stage2_attempts", String.valueOf(Integer.parseInt(sm.getInstanceVariable("stage2_attempts").isEmpty()?"0":sm.getInstanceVariable("stage2_attempts"))+1));
                    sm.broadcastScreenEffect("quest/party/wrong_kor");
                    sm.broadcastSoundEffect("Party1/Failed");
                    return;
                }
                // Stage clear
                sm.setInstanceVariable("stage2_gate", "1");
                sm.broadcastScreenEffect("quest/party/clear");
                sm.broadcastSoundEffect("Party1/Clear");
                sm.setObjectState("gate", 0);
            }
            case STAGE_3 -> {
                if (sm.getInstanceVariable("stage3_gate").equals("1") || !user.isPartyBoss()) {
                    return;
                }
                // Introduction
                final String answer = sm.getInstanceVariable("stage3_answer");
                if (answer.isEmpty()) {
                    final List<String> list = new ArrayList<>(List.of("1", "1", "1", "0", "0"));
                    Collections.shuffle(list);
                    sm.setInstanceVariable("stage3_answer", String.join("", list));
                    sm.sayNext(PqDialogue.KPQ_PLATFORMS);
                    sm.sayBoth(PqDialogue.KPQ_PUPPET);
                    return;
                }
                // Check answer
                final String current = sm.getAreaCheck();
                if (!answer.equals(current)) {
                    sm.setInstanceVariable("stage3_attempts", String.valueOf(Integer.parseInt(sm.getInstanceVariable("stage3_attempts").isEmpty()?"0":sm.getInstanceVariable("stage3_attempts"))+1));
                    int count = 0;
                    for (int i = 0; i < answer.length(); i++) {
                        if (answer.charAt(i) == '1' && current.charAt(i) == '1') {
                            count++;
                        }
                    }
                    if (!sm.getInstanceVariable("stage3_hint").equals("1")) {
                        final String hint = "hint" + (answer.indexOf('1') + 1);
                        sm.setObjectState(hint, 0);
                        sm.setInstanceVariable("stage3_hint", "1");
                    }
                    sm.broadcastMessage(String.format("Currently, you've selected %d answer platforms", count));
                    sm.broadcastScriptProgressMessage(String.format("Currently, you've selected %d answer platforms", count));
                    sm.broadcastScreenEffect("quest/party/wrong_kor");
                    sm.broadcastSoundEffect("Party1/Failed");
                    return;
                }
                // Stage clear
                sm.setInstanceVariable("stage3_gate", "1");
                sm.broadcastScreenEffect("quest/party/clear");
                sm.broadcastSoundEffect("Party1/Clear");
                sm.setObjectState("gate", 0);
            }
            case STAGE_4 -> {
                if (sm.getInstanceVariable("stage4_gate").equals("1")) {
                    sm.sayOk("Stage Four is clear. Use the portal to reach King Slime.");
                    return;
                }
                if (!user.isPartyBoss()) {
                    sm.sayOk("Defeat all Curse Eyes. Their passes are not needed; the portal will open when the room is clear.");
                    return;
                }
                // Introduction
                if (!sm.getInstanceVariable("stage4_intro").equals("1")) {
                    sm.setInstanceVariable("stage4_intro", "1");
                    sm.sayNext("Defeat every Curse Eye in this room. Their passes are not needed; the portal will open when the room is clear.");
                    return;
                }
                // Check mob count
                if (field.getMobPool().getByTemplateId(9300002).isPresent()) {
                    sm.sayNext("There are still Curse Eyes in this room. Defeat them all before asking me to check again.");
                    return;
                }
                // Stage clear
                sm.setInstanceVariable("stage4_gate", "1");
                sm.addExpAll(100);
                sm.broadcastScreenEffect("quest/party/clear");
                sm.broadcastSoundEffect("Party1/Clear");
                sm.setObjectState("gate", 0);
                sm.broadcastScriptProgressMessage("Stage Four is clear. Proceed through the portal to King Slime.");
                sm.sayNext("All Curse Eyes have been defeated. The portal to King Slime is open!");
            }
            case STAGE_5 -> {
                if (!sm.getInstanceVariable("boss_defeated").equals("1")) {
                    sm.sayNext(PqDialogue.KPQ_BOSS);
                    return;
                }
                final Instance instance = ((InstanceFieldStorage) field.getFieldStorage()).getInstance();
                if (instance.getRewards().orElseThrow().hasClaimed(user.getCharacterId())) {
                    sm.sayNext(PqDialogue.KPQ_FINISHED);
                    sm.warp(EXIT);
                } else {
                    sm.sayOk("King Slime has been defeated! Choose a treasure box in the reward window, receive your prize, then speak to me again to leave.");
                    KerningPQRewards.show(user);
                }
            }
        }
    }

    /** Physical coupon/pass handoff follows the supplied Hidden Street Stage One guide. */
    static void stageOne(ScriptManager sm, boolean leader, int characterId) {
        if (sm.getInstanceVariable("stage1_gate").equals("1")) {
            sm.sayOk("Stage One is clear. Use the portal to continue.");
            return;
        }
        final String configured = sm.getInstanceVariable("required_passes");
        final int required = Util.isInteger(configured) ? Integer.parseInt(configured) : Math.max(sm.getInstanceUserCount() - 1, 2);
        if (leader) {
            if (!sm.getInstanceVariable("stage1_intro").equals("1")) {
                sm.setInstanceVariable("stage1_intro", "1");
                sm.sayNext(PqDialogue.KPQ_FIRST);
                return;
            }
            if (sm.getItemCount(PASS) < required || !sm.removeItem(PASS, required)) {
                sm.sayNext(PqDialogue.kpqShortPasses(required));
                return;
            }
            // Persist before showing dialogue: cancelling a conversation must not consume passes twice.
            sm.setInstanceVariable("stage1_gate", "1");
            sm.addExpAll(100);
            sm.broadcastScreenEffect("quest/party/clear");
            sm.broadcastSoundEffect("Party1/Clear");
            sm.setObjectState("gate", 0);
            sm.broadcastScriptProgressMessage("Stage One is clear. Proceed through the portal.");
            sm.sayNext(PqDialogue.kpqPassesClear(required));
            return;
        }
        final String key = "coupon_mission_" + characterId;
        final String mission = sm.getInstanceVariable(key);
        if (mission.equals("clear")) {
            sm.sayOk("You have earned your pass. Give it to your party leader and help the others finish their missions.");
            return;
        }
        if (!Util.isInteger(mission)) {
            // Retain Kinoko's existing range until a period source establishes the exact distribution.
            final int coupons = Util.getRandom(5, 20);
            sm.setInstanceVariable(key, String.valueOf(coupons));
            sm.sayOk("Please collect exactly " + coupons + " coupons from Ligators and bring them to me for a yellow pass.");
            return;
        }
        final int coupons = Integer.parseInt(mission);
        if (sm.getItemCount(COUPON) != coupons) {
            sm.sayOk("I need exactly " + coupons + " coupons. Check your inventory and talk to me again.");
            return;
        }
        if (!sm.canAddItem(PASS, 1)) {
            sm.sayOk("Please make room in your ETC inventory for your yellow pass. Keep your coupons and talk to me again.");
            return;
        }
        if (!sm.removeItem(COUPON, coupons)) {
            return;
        }
        if (!sm.addItem(PASS, 1)) {
            sm.addItem(COUPON, coupons);
            return;
        }
        sm.setInstanceVariable(key, "clear");
        sm.sayOk("Here is your yellow pass. Give it to your party leader, then help the remaining party members.");
    }

    static void buyPuppet(ScriptManager sm, int price) {
        if (price < 1 || !sm.canAddItem(PUPPET, 1)) {
            sm.sayOk("Please make room in your ETC inventory first.");
            return;
        }
        if (!sm.addMoney(-price)) {
            sm.sayOk("You do not have enough mesos.");
            return;
        }
        if (!sm.addItem(PUPPET, 1)) {
            sm.addMoney(price);
            return;
        }
        sm.sayOk("Drop the puppet at the center of a platform. It will count as one occupied platform.");
    }

    private static void updateInstance(Instance instance) {
        if (!instance.getVariable("boss_defeated").orElse("").equals("1") &&
                !instance.getVariable("failed").orElse("").equals("1") &&
                Instant.now().getEpochSecond() >= Long.parseLong(instance.getVariable("ready_after").orElse("0"))) {
            final int partyId = Integer.parseInt(instance.getVariable("party_id").orElse("0"));
            final Set<String> roster = Set.of(instance.getVariable("roster").orElse("").split(","));
            final Set<Integer> present = instance.getUsers().stream()
                    .filter(user -> roster.contains(String.valueOf(user.getCharacterId())) && user.getPartyId() == partyId && user.getHp() > 0)
                    .map(User::getCharacterId).collect(Collectors.toSet());
            if (present.size() >= 3) {
                if (!instance.getVariable("stage1_gate").orElse("").equals("1")) {
                    instance.setVariable("required_passes", String.valueOf(present.size() - 1));
                }
            } else {
                instance.setVariable("failed", "1");
                final Field exit = instance.getChannelServerNode().getFieldById(EXIT).orElseThrow();
                for (User user : instance.getUsers()) {
                    user.write(WvsContext.scriptProgressMessage("KPQ ended because fewer than three living party members remain in the run."));
                    user.warp(exit, exit.getRandomStartPoint().orElseThrow(), false, false);
                }
                instance.getChannelServerNode().removeInstance(instance);
            }
        }
    }

    private static void mobDefeated(Instance instance, Mob mob) {
        if (mob.getField().getFieldId() == STAGE_4 && mob.getTemplateId() == 9300002 &&
                mob.getField().getMobPool().getByTemplateId(9300002).isEmpty() &&
                !instance.getVariable("stage4_gate").orElse("").equals("1")) {
            instance.setVariable("stage4_gate", "1");
            mob.getField().setObjectState("gate", 0);
            mob.getField().broadcastPacket(kinoko.packet.field.FieldEffectPacket.screen("quest/party/clear"));
            mob.getField().broadcastPacket(kinoko.packet.field.FieldEffectPacket.sound("Party1/Clear"));
            for (User member : instance.getUsers()) {
                member.addExp(100);
                member.write(MessagePacket.incExp(100, 0, true, true));
                member.write(WvsContext.scriptProgressMessage("Stage Four is clear. Proceed through the portal to King Slime."));
            }
        }
        if (mob.getField().getFieldId() == STAGE_5 && mob.getTemplateId() == 9300003) {
            if (instance.getVariable("boss_defeated").orElse("").equals("1")) return;
            mob.getField().broadcastPacket(kinoko.packet.field.FieldEffectPacket.screen("quest/party/clear"));
            mob.getField().broadcastPacket(kinoko.packet.field.FieldEffectPacket.sound("Party1/Clear"));
            instance.setVariable("boss_defeated", "1");
            instance.setVariable("stage5_gate", "1");
            KerningPQRewards.prepare(instance);
            for (User member : instance.getUsers()) {
                member.write(WvsContext.scriptProgressMessage("King Slime is defeated. Choose your treasure box, then return to Cloto."));
                KerningPQRewards.show(member);
            }
        }
    }

    private static void removeStageItems(ScriptManager sm) {
        sm.removeItem(COUPON);
        sm.removeItem(PASS);
        sm.removeItem(PUPPET);
    }

    @Script("party1_out")
    public static void party1_out(ScriptManager sm) {
        // Nella (9020002)
        //   Hidden Street : First Time Together <1st Stage> (103000800)
        //   Hidden Street : First Time Together <2nd Stage> (103000801)
        //   Hidden Street : First Time Together <3rd Stage> (103000802)
        //   Hidden Street : First Time Together <4th stage> (103000803)
        //   Hidden Street : First Time Together <Last Stage> (103000804)
        //   Hidden Street : First Time Together <Bonus> (103000805)
        //   Hidden Street : First Time Together <Exit> (910340000)
        //   Hidden Street : First Time Together <1st Stage> (910340100)
        //   Hidden Street : First Time Together <1st Stage> (910340101)
        //   Hidden Street : First Time Together <2nd Stage> (910340200)
        //   Hidden Street : First Time Together <2nd Stage> (910340201)
        //   Hidden Street : First Time Together <3rd Stage> (910340300)
        //   Hidden Street : First Time Together <3rd Stage> (910340301)
        //   Hidden Street : First Time Together <4th Stage> (910340400)
        //   Hidden Street : First Time Together <4th Stage> (910340401)
        //   Hidden Street : First Time Together <Last Stage> (910340500)
        //   Hidden Street : First Time Together <Last Stage> (910340501)
        //   Hidden Street : First Time Together <Bonus> (910340600)
        //   Hidden Street : First Time Together <Bonus> (910340601)
        if (sm.getFieldId() == EXIT) {
            removeStageItems(sm);
            sm.warp(LOBBY);
            return;
        }
        if (sm.getFieldId() == STAGE_3) {
            final int price = Math.max(1, ItemProvider.getItemInfo(PUPPET).orElseThrow().getPrice());
            final int choice = sm.askMenu("A Platform Puppet can stand on a platform while you talk to Cloto.", Map.of(
                    0, "Buy one Platform Puppet (" + price + " mesos)", 1, "Leave the party quest", 2, "Stay here"));
            if (choice == 2) return;
            if (choice == 0) {
                buyPuppet(sm, price);
                return;
            }
        }
        if (sm.askYesNo("Would you like to leave First Time Together? Your coupons, passes and puppets will be removed. You cannot return to this attempt.")) {
            removeStageItems(sm);
            sm.warp(EXIT);
        }
    }

    @Script("StageMsg_together")
    public static void StageMsg_together(ScriptManager sm) {
        // Hidden Street : First Time Together <1st Stage> (103000800)
        // Hidden Street : First Time Together <2nd Stage> (103000801)
        // Hidden Street : First Time Together <3rd Stage> (103000802)
        // Hidden Street : First Time Together <4th stage> (103000803)
        // Hidden Street : First Time Together <Last Stage> (103000804)
        // Hidden Street : First Time Together <1st Stage> (910340100)
        // Hidden Street : First Time Together <1st Stage> (910340101)
        // Hidden Street : First Time Together <2nd Stage> (910340200)
        // Hidden Street : First Time Together <2nd Stage> (910340201)
        // Hidden Street : First Time Together <3rd Stage> (910340300)
        // Hidden Street : First Time Together <3rd Stage> (910340301)
        // Hidden Street : First Time Together <4th Stage> (910340400)
        // Hidden Street : First Time Together <4th Stage> (910340401)
        // Hidden Street : First Time Together <Last Stage> (910340500)
        // Hidden Street : First Time Together <Last Stage> (910340501)
        final Field field = sm.getField();
        switch (field.getFieldId()) {
            case STAGE_1 -> {
                field.blowWeather(5120017, PqDialogue.kpqBubble(STAGE_1), 20);
            }
            case STAGE_2 -> {
                field.blowWeather(5120017, PqDialogue.kpqBubble(STAGE_2), 20);
            }
            case STAGE_3 -> {
                field.blowWeather(5120017, PqDialogue.kpqBubble(STAGE_3), 20);
            }
            case STAGE_4 -> {
                field.keepInitialMobsOnly();
                field.blowWeather(5120017, PqDialogue.kpqBubble(STAGE_4), 20);
            }
            case STAGE_5 -> {
                field.keepInitialMobsOnly();
                field.blowWeather(5120017, PqDialogue.kpqBubble(STAGE_5), 20);
            }
        }
    }
}

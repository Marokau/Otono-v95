package kinoko.script.party;

import kinoko.packet.field.PqRewardPacket;
import kinoko.packet.world.WvsContext;
import kinoko.provider.ItemProvider;
import kinoko.script.common.ScriptManagerImpl;
import kinoko.server.field.Instance;
import kinoko.server.field.InstanceFieldStorage;
import kinoko.server.field.InstanceRewards;
import kinoko.server.packet.InPacket;
import kinoko.util.Util;
import kinoko.world.user.User;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public final class KerningPQRewards {
    // Hidden Street's supplied Rewards PDF. Stack quantities and exact historical weights are unconfirmed;
    // each selection currently grants one item, with equal weights, rather than borrowing a later PQ pool.
    public static final List<Integer> ITEM_IDS = List.of(
            2040505, 2040508, 2040502, 2040002, 2040602, 2040402, 2040801,
            1032009, 1032004, 1032005, 1032006, 1032007, 1032010, 1032002,
            1002026, 1002090, 1002089,
            2000003, 2000001, 2000002, 2000006, 2022003, 2022000, 2000004, 4003000,
            4010002, 4010001, 4010006, 4010003, 4010005, 4010004, 4010000,
            4020001, 4020002, 4020008, 4020006, 4020003, 4020000, 4020004, 4020005, 4020007);

    private KerningPQRewards() {}

    public static void prepare(Instance instance) {
        if (instance.getRewards().isPresent()) return;
        Set<Integer> participants = Set.of(instance.getVariable("roster").orElseThrow().split(",")).stream()
                .map(Integer::parseInt).filter(id -> instance.getUsers().stream().anyMatch(user -> user.getCharacterId()==id))
                .collect(Collectors.toSet());
        var prizes = new ArrayList<InstanceRewards.Prize>();
        for (int box = 0; box < 6; box++) {
            int itemId = Util.getRandomFromCollection(ITEM_IDS).orElseThrow();
            if (ItemProvider.getItemInfo(itemId).isEmpty()) throw new IllegalStateException("Missing PQ prize " + itemId);
            prizes.add(new InstanceRewards.Prize(itemId, 1));
        }
        instance.setRewards(new InstanceRewards(participants, prizes));
    }

    private static Optional<Instance> eligibleInstance(User user) {
        if (!(user.getField().getFieldStorage() instanceof InstanceFieldStorage storage)) return Optional.empty();
        Instance instance = storage.getInstance();
        boolean kpq=instance.getVariable("kpq").orElse("").equals("1") && user.getFieldId()==KerningPQ.STAGE_5;
        if (!kpq || user.getHp()<=0 ||
                !instance.getVariable("boss_defeated").orElse("").equals("1") ||
                instance.getVariable("failed").orElse("").equals("1") || Instant.now().isAfter(instance.getExpireTime()) ||
                user.getPartyId() != Integer.parseInt(instance.getVariable("party_id").orElse("0"))) return Optional.empty();
        return Optional.of(instance);
    }

    public static void show(User user) {
        eligibleInstance(user).ifPresent(instance -> {
            user.write(PqRewardPacket.show(user.getCharacterId()));
            instance.getRewards().ifPresent(rewards -> {
                for (User member : instance.getUsers()) {
                    int selected = rewards.selectedBox(member.getCharacterId());
                    if (selected >= 0) user.write(PqRewardPacket.selected(member.getCharacterId(), member.getCharacterName(), selected));
                }
            });
        });
    }

    public static void select(User user, InPacket packet) {
        if (packet.getRemaining() != 1) { user.write(PqRewardPacket.selectionFailed()); return; }
        int box = Byte.toUnsignedInt(packet.decodeByte()); // UI button IDs 1000-1005 are encoded modulo 256 after adding 24.
        var instanceResult = eligibleInstance(user);
        if (instanceResult.isEmpty()) { user.write(PqRewardPacket.selectionFailed()); return; }
        Instance instance = instanceResult.get();
        InstanceRewards rewards = instance.getRewards().orElseThrow();
        var result = rewards.select(user.getCharacterId(), box);
        if (result == InstanceRewards.Selection.SELECTED) {
            for (User member : instance.getUsers()) member.write(PqRewardPacket.selected(user.getCharacterId(), user.getCharacterName(), box));
        } else if (result == InstanceRewards.Selection.ALREADY_SELECTED) {
            user.write(PqRewardPacket.selected(user.getCharacterId(), user.getCharacterName(), rewards.selectedBox(user.getCharacterId())));
        } else user.write(PqRewardPacket.selectionFailed());
    }

    public static void claim(User user, InPacket packet) {
        var instanceResult = eligibleInstance(user);
        if (packet.getRemaining() != 0 || instanceResult.isEmpty()) { user.write(PqRewardPacket.claimFailed()); return; }
        Instance instance = instanceResult.get();
        InstanceRewards rewards = instance.getRewards().orElseThrow();
        // Do not reveal all six prizes while another present participant can still choose a box.
        if (instance.getUsers().stream().anyMatch(member -> rewards.isParticipant(member.getCharacterId()) &&
                rewards.selectedBox(member.getCharacterId()) < 0)) {
            user.write(PqRewardPacket.claimFailed()); // the native window retries after its normal delay
            return;
        }
        var sm = new ScriptManagerImpl(user, user.getField(), user, "party1_play", 9020001);
        var result = rewards.claim(user.getCharacterId(), sm::addItem);
        if (result == InstanceRewards.Claim.GRANTED) {
            sm.addExp(100); // Final award calibration remains provisional.
            user.write(PqRewardPacket.received(rewards.prizes()));
        } else if (result == InstanceRewards.Claim.ALREADY_CLAIMED) {
            user.write(PqRewardPacket.received(rewards.prizes())); // replay UI result, never the inventory grant
        } else {
            user.write(PqRewardPacket.claimFailed());
            user.write(WvsContext.scriptProgressMessage("Make room for your reward, then request it again. Your selected prize is saved."));
        }
    }
}

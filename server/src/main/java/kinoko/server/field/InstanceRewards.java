package kinoko.server.field;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiPredicate;

/** One shared set of hidden prizes, with at most one selection and claim per participant. */
public final class InstanceRewards {
    public record Prize(int itemId, int quantity) {
        public Prize {
            if (itemId <= 0 || quantity <= 0) throw new IllegalArgumentException("Invalid PQ prize");
        }
    }
    public enum Selection { SELECTED, ALREADY_SELECTED, TAKEN, INVALID, INELIGIBLE }
    public enum Claim { GRANTED, FULL_INVENTORY, ALREADY_CLAIMED, NOT_SELECTED, INELIGIBLE }

    private final Set<Integer> participants;
    private final List<Prize> prizes;
    private final Map<Integer, Integer> selections = new HashMap<>();
    private final Set<Integer> claimed = new HashSet<>();

    public InstanceRewards(Set<Integer> participants, List<Prize> prizes) {
        if (participants.isEmpty() || prizes.size() < participants.size()) {
            throw new IllegalArgumentException("Each participant needs an available reward box");
        }
        this.participants = Set.copyOf(participants);
        this.prizes = List.copyOf(prizes);
    }

    public synchronized Selection select(int characterId, int box) {
        if (!participants.contains(characterId)) return Selection.INELIGIBLE;
        if (box < 0 || box >= prizes.size()) return Selection.INVALID;
        if (selections.containsKey(characterId)) return Selection.ALREADY_SELECTED;
        if (selections.containsValue(box)) return Selection.TAKEN;
        selections.put(characterId, box);
        return Selection.SELECTED;
    }

    public synchronized Claim claim(int characterId, BiPredicate<Integer, Integer> grant) {
        if (!participants.contains(characterId)) return Claim.INELIGIBLE;
        if (claimed.contains(characterId)) return Claim.ALREADY_CLAIMED;
        Integer box = selections.get(characterId);
        if (box == null) return Claim.NOT_SELECTED;
        Prize prize = prizes.get(box);
        if (!grant.test(prize.itemId(), prize.quantity())) return Claim.FULL_INVENTORY;
        claimed.add(characterId);
        return Claim.GRANTED;
    }

    public synchronized Optional<Prize> selectedPrize(int characterId) {
        Integer box = selections.get(characterId);
        return box == null ? Optional.empty() : Optional.of(prizes.get(box));
    }

    public synchronized int selectedBox(int characterId) { return selections.getOrDefault(characterId, -1); }
    public synchronized boolean hasClaimed(int characterId) { return claimed.contains(characterId); }
    public synchronized boolean isTaken(int box) { return selections.containsValue(box); }
    public int boxCount() { return prizes.size(); }
    public List<Prize> prizes() { return prizes; }
    public boolean isParticipant(int characterId) { return participants.contains(characterId); }
}

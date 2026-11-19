package kinoko.server.field;

import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class InstanceRewardsTest {
    private InstanceRewards rewards() {
        return new InstanceRewards(Set.of(1,2,3), List.of(
                new InstanceRewards.Prize(2000001, 1), new InstanceRewards.Prize(1032009, 1),
                new InstanceRewards.Prize(4010000, 1), new InstanceRewards.Prize(1002026, 1)));
    }

    @Test void strangersInvalidBoxesAndDuplicateSelectionsCannotClaimAnything() {
        var rewards = rewards();
        assertEquals(InstanceRewards.Selection.INELIGIBLE, rewards.select(99, 0));
        assertEquals(InstanceRewards.Selection.INVALID, rewards.select(1, -1));
        assertEquals(InstanceRewards.Selection.INVALID, rewards.select(1, 4));
        assertEquals(InstanceRewards.Claim.NOT_SELECTED, rewards.claim(1, (id, count) -> fail("No selection")));
        assertEquals(InstanceRewards.Selection.SELECTED, rewards.select(1, 0));
        assertEquals(InstanceRewards.Selection.TAKEN, rewards.select(2, 0));
        assertEquals(InstanceRewards.Selection.ALREADY_SELECTED, rewards.select(1, 1));
        assertEquals(0, rewards.selectedBox(1));
    }

    @Test void fullInventoryPreservesTheChosenPrizeAndClaimIsExactlyOnce() {
        var rewards = rewards();
        rewards.select(1, 1);
        assertEquals(InstanceRewards.Claim.FULL_INVENTORY, rewards.claim(1, (id, count) -> false));
        assertFalse(rewards.hasClaimed(1));
        assertEquals(1032009, rewards.selectedPrize(1).orElseThrow().itemId());
        var grants = new AtomicInteger();
        assertEquals(InstanceRewards.Claim.GRANTED, rewards.claim(1, (id, count) -> {
            assertEquals(1032009, id); assertEquals(1, count); grants.incrementAndGet(); return true;
        }));
        assertEquals(InstanceRewards.Claim.ALREADY_CLAIMED, rewards.claim(1, (id,count) -> { grants.incrementAndGet(); return true; }));
        assertEquals(1, grants.get());
    }

    @Test void concurrentClaimsCannotDuplicateAReward() throws InterruptedException {
        var rewards = rewards();
        rewards.select(1, 2);
        var grants = new AtomicInteger();
        Runnable attempt = () -> rewards.claim(1, (id,count) -> { grants.incrementAndGet(); return true; });
        Thread first = new Thread(attempt), second = new Thread(attempt);
        first.start(); second.start(); first.join(); second.join();
        assertEquals(1, grants.get());
    }
}

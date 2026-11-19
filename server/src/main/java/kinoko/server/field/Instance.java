package kinoko.server.field;

import kinoko.packet.field.FieldPacket;
import kinoko.packet.world.WvsContext;
import kinoko.server.node.ChannelServerNode;
import kinoko.world.user.User;
import kinoko.world.field.mob.Mob;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class Instance {
    private final int instanceId;
    private final int returnMap;
    private final ConcurrentHashMap<Integer, User> userMap;
    private final ConcurrentHashMap<String, String> variables;
    private final ChannelServerNode channelServerNode;
    private final Instant expireTime;

    private InstanceFieldStorage fieldStorage;
    private List<Integer> restrictedItems = List.of();
    private List<Integer> protectedDrops = List.of();

    public void setProtectedDrops(List<Integer> itemIds) { protectedDrops = List.copyOf(itemIds); }
    private Consumer<Instance> updateHandler = instance -> {};
    private Instant nextUpdate = Instant.MIN;
    private InstanceRewards rewards;

    public Optional<InstanceRewards> getRewards() { return Optional.ofNullable(rewards); }
    public void setRewards(InstanceRewards rewards) { this.rewards = rewards; }
    private Consumer<Mob> mobDefeatedHandler = mob -> {};

    public void setMobDefeatedHandler(Consumer<Mob> handler) {
        mobDefeatedHandler = handler;
    }

    public void mobDefeated(Mob mob) {
        mobDefeatedHandler.accept(mob);
    }

    public void setRestrictedItems(List<Integer> itemIds) {
        restrictedItems = List.copyOf(itemIds);
    }

    public boolean protectsDrop(int itemId) {
        return restrictedItems.contains(itemId) || protectedDrops.contains(itemId);
    }

    /** Apply only when leaving the whole instance, not when moving between its stages. */
    public void clearRestrictedItems(User user) {
        for (int itemId : restrictedItems) {
            final int quantity = user.getInventoryManager().getItemCount(itemId);
            if (quantity > 0) {
                user.getInventoryManager().removeItem(itemId, quantity)
                        .ifPresent(operations -> user.write(WvsContext.inventoryOperation(operations, false)));
            }
        }
    }

    public void setUpdateHandler(Consumer<Instance> handler) {
        updateHandler = handler;
    }

    /** All fields of an instance share one game executor. Run its rules once per second. */
    public void update(Instant now) {
        if (now.isBefore(nextUpdate)) return;
        nextUpdate = now.plusSeconds(1);
        updateHandler.accept(this);
    }

    public Instance(int instanceId, int returnMap, ChannelServerNode channelServerNode, Instant expireTime) {
        this.instanceId = instanceId;
        this.returnMap = returnMap;
        this.userMap = new ConcurrentHashMap<>();
        this.variables = new ConcurrentHashMap<>();
        this.channelServerNode = channelServerNode;
        this.expireTime = expireTime;
    }

    public ChannelServerNode getChannelServerNode() {
        return channelServerNode;
    }

    public int getInstanceId() {
        return instanceId;
    }

    public int getReturnMap() {
        return returnMap;
    }

    public Instant getExpireTime() {
        return expireTime;
    }

    public InstanceFieldStorage getFieldStorage() {
        return fieldStorage;
    }

    public void setFieldStorage(InstanceFieldStorage fieldStorage) {
        this.fieldStorage = fieldStorage;
    }

    public void addUser(User user) {
        userMap.put(user.getId(), user);
        // Show clock
        final int remain = (int) (getExpireTime().getEpochSecond() - Instant.now().getEpochSecond());
        user.write(FieldPacket.clock(remain));
    }

    public void removeUser(User user) {
        userMap.remove(user.getId(), user);
    }

    public Set<User> getUsers() {
        return userMap.values().stream().collect(Collectors.toUnmodifiableSet());
    }

    public Optional<String> getVariable(String key) {
        return Optional.ofNullable(variables.get(key));
    }

    public void setVariable(String key, String value) {
        variables.put(key, value);
    }
}

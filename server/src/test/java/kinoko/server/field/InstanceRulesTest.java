package kinoko.server.field;

import kinoko.provider.ItemProvider;
import kinoko.server.node.Client;
import kinoko.world.item.Inventory;
import kinoko.world.item.InventoryManager;
import kinoko.world.user.CharacterData;
import kinoko.world.user.User;
import io.netty.channel.Channel;
import io.netty.channel.DefaultChannelPromise;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.concurrent.ImmediateEventExecutor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class InstanceRulesTest {
    @BeforeAll static void items() { ItemProvider.initialize(); }

    @Test void rulesAreOptInAndRunOncePerSecondAcrossMultipleStageTicks() {
        var instance = new Instance(1, 910340000, null, Instant.now().plusSeconds(1800));
        assertFalse(instance.protectsDrop(4001008));
        var updates = new AtomicInteger();
        instance.setUpdateHandler(ignored -> updates.incrementAndGet());
        Instant tick = Instant.parse("2026-11-13T12:00:00Z");
        instance.update(tick);
        instance.update(tick.plusMillis(100));
        instance.update(tick.plusMillis(950));
        assertEquals(1, updates.get());
        instance.update(tick.plusSeconds(1));
        assertEquals(2, updates.get());
    }

    @Test void exitCleanupRemovesOnlyConfiguredProgressionItems() {
        var packets = new ArrayList<Object>();
        SocketChannel socket = (SocketChannel) Proxy.newProxyInstance(SocketChannel.class.getClassLoader(),
                new Class[]{SocketChannel.class}, (proxy, method, args) -> {
                    if (method.getName().equals("writeAndFlush")) {
                        packets.add(args[0]);
                        return new DefaultChannelPromise((Channel) proxy, ImmediateEventExecutor.INSTANCE).setSuccess();
                    }
                    throw new AssertionError("Unexpected socket operation: " + method.getName());
                });
        var data = new CharacterData(1);
        var inventory = new InventoryManager();
        inventory.setEtcInventory(new Inventory(24));
        inventory.getEtcInventory().putItem(1, ItemProvider.getItemInfo(4001008).orElseThrow().createItem(1, 3));
        inventory.getEtcInventory().putItem(2, ItemProvider.getItemInfo(4000000).orElseThrow().createItem(2, 5));
        data.setInventoryManager(inventory);
        var user = new User(new Client(null, socket), data);
        var instance = new Instance(1, 910340000, null, Instant.now().plusSeconds(1800));
        instance.setRestrictedItems(List.of(4001008));
        assertTrue(instance.protectsDrop(4001008));
        instance.clearRestrictedItems(user);
        assertEquals(0, inventory.getItemCount(4001008));
        assertEquals(5, inventory.getItemCount(4000000));
        assertEquals(1, packets.size());
        instance.clearRestrictedItems(user);
        assertEquals(1, packets.size());
    }
}

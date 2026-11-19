package kinoko.script.custom;

import kinoko.packet.field.FieldPacket;
import kinoko.provider.ItemProvider;
import kinoko.provider.NpcProvider;
import kinoko.provider.StringProvider;
import kinoko.provider.item.ItemInfo;
import kinoko.script.common.Script;
import kinoko.script.common.ScriptError;
import kinoko.script.common.ScriptHandler;
import kinoko.script.common.ScriptManager;
import kinoko.server.dialog.shop.ShopDialog;
import kinoko.server.dialog.shop.ShopItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.IntPredicate;

public final class AioShop extends ScriptHandler {
    private static final int NPC_ID = 9000000;
    private static final int MAX_RESULTS = 200;

    @Script("aio_shop")
    public static void aioShop(ScriptManager sm) {
        final Map<Integer, String> options = new LinkedHashMap<>();
        options.put(0, "Search by item name");
        options.put(1, "Weapons");
        options.put(2, "Mechanic equipment");
        options.put(100, "Hats");
        options.put(101, "Face accessories");
        options.put(102, "Eye accessories");
        options.put(103, "Earrings");
        options.put(104, "Tops");
        options.put(105, "Overalls");
        options.put(106, "Bottoms");
        options.put(107, "Shoes");
        options.put(108, "Gloves");
        options.put(109, "Shields");
        options.put(110, "Capes");
        options.put(111, "Rings");
        options.put(112, "Pendants");
        options.put(113, "Belts");
        options.put(114, "Medals");
        options.put(204, "Scrolls");
        options.put(200, "Potions");
        options.put(206, "Arrows");
        options.put(207, "Throwing stars");
        options.put(233, "Bullets");
        options.put(400, "ETC items, including summoning and magic rocks");
        final int selected = sm.askMenu("#eOtono All In One Shop#n\r\nBrowse or search non-cash items. Items cost 1 meso each.", options);
        if (selected == 0) {
            final String query = sm.askText("Enter part of an item name.", "", 1, 40).trim().toLowerCase(Locale.ROOT);
            if (query.isEmpty()) {
                sm.sayOk("Please enter an item name.");
                return;
            }
            openShop(sm, id -> StringProvider.getItemName(id).toLowerCase(Locale.ROOT).contains(query));
        } else if (selected == 1) {
            openWeaponMenu(sm);
        } else if (selected == 2) {
            final int type = sm.askMenu("Choose a Mechanic equipment type.", Map.of(
                    161, "Engines", 162, "Machine arms", 163, "Machine legs", 164, "Machine bodies", 165, "Transistors"));
            openShop(sm, id -> id / 10000 == type);
        } else {
            openShop(sm, id -> id / 10000 == selected);
        }
    }

    private static void openWeaponMenu(ScriptManager sm) {
        final Map<Integer, String> types = new LinkedHashMap<>();
        types.put(130, "One-handed swords");
        types.put(131, "One-handed axes");
        types.put(132, "One-handed blunt weapons");
        types.put(133, "Daggers");
        types.put(134, "Katara");
        types.put(137, "Wands");
        types.put(138, "Staves");
        types.put(140, "Two-handed swords");
        types.put(141, "Two-handed axes");
        types.put(142, "Two-handed blunt weapons");
        types.put(143, "Spears");
        types.put(144, "Polearms");
        types.put(145, "Bows");
        types.put(146, "Crossbows");
        types.put(147, "Claws");
        types.put(148, "Knuckles");
        types.put(149, "Guns");
        final int selected = sm.askMenu("Choose a weapon type.", types);
        openShop(sm, id -> id / 10000 == selected);
    }

    private static void openShop(ScriptManager sm, IntPredicate filter) {
        final List<Integer> matches = StringProvider.getItemNames().keySet().stream()
                .filter(filter::test)
                .filter(id -> ItemProvider.getItemInfo(id).map(info -> !info.isCash()).orElse(false))
                .sorted().toList();
        if (matches.isEmpty()) {
            sm.sayOk("No non-cash items matched. Try another category or search.");
            return;
        }
        if (matches.size() > MAX_RESULTS) {
            sm.message("Showing the first " + MAX_RESULTS + " matches. Search a more specific item name to narrow the results.");
        }
        final List<ShopItem> items = matches.stream().limit(MAX_RESULTS).map(id -> {
            final ItemInfo info = ItemProvider.getItemInfo(id).orElseThrow();
            return new ShopItem(id, 1, 1, Math.max(1, info.getSlotMax()), 0, 0, info.getUnitPrice());
        }).toList();
        final ShopDialog shop = new ShopDialog(NpcProvider.getNpcTemplate(NPC_ID)
                .orElseThrow(() -> new ScriptError("Could not resolve AIO shop NPC %d", NPC_ID)), items);
        sm.getUser().setDialog(shop);
        sm.getUser().write(FieldPacket.openShopDlg(sm.getUser(), shop));
    }
}

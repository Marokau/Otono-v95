package kinoko.server;

import kinoko.world.GameConstants;

public final class ServerConfig {
    public static final int WORLD_ID = Settings.get("WORLD_ID", 0);
    public static final String WORLD_NAME = Settings.get("WORLD_NAME", "Kinoko");
    public static final int CHANNELS_PER_WORLD = Settings.get("CHANNEL_COUNT", 5);
    public static final int CENTRAL_REQUEST_TTL = 5;
    public static final int FIELD_TICK_INTERVAL = 100; // 100 ms tick
    public static final int SHUTDOWN_TIMEOUT = 30;

    public static final boolean AUTO_CREATE_ACCOUNT = Settings.get("AUTO_CREATE_ACCOUNT", true);
    public static final boolean REQUIRE_SECONDARY_PASSWORD = Settings.get("REQUIRE_SECONDARY_PASSWORD", true);
    public static final int EXP_RATE = Settings.range("EXP_RATE", 1, 1, Integer.MAX_VALUE);
    public static final String WZ_DIRECTORY = Settings.get("WZ_DIRECTORY", "wz");
    public static final String DATA_DIRECTORY = Settings.get("DATA_DIRECTORY", "data");

    public static final int CHARACTER_BASE_SLOTS = Settings.get("CHARACTER_BASE_SLOTS", 3);
    public static final int INVENTORY_BASE_SLOTS = inventorySlots();
    public static final int INVENTORY_CASH_SLOTS = GameConstants.INVENTORY_SLOT_MAX;
    public static final int TRUNK_BASE_SLOTS = Settings.get("TRUNK_BASE_SLOTS", 4);
    public static final int FRIEND_MAX_BASE = 20;
    public static final int CYGNUS_LEVEL_MAX = 120;
    public static final int ITEM_EXPIRE_INTERVAL = 60; // 180 seconds in BMS
    public static final int WORLD_SPEAKER_COOLTIME = 60;

    public static final String COMMAND_PREFIX = Settings.get("COMMAND_PREFIX", "!");
    public static final boolean DEBUG_MODE = Settings.get("DEBUG_MODE", true);
    public static final boolean PLAIN_TRAFFIC = Settings.get("PLAIN_TRAFFIC", false);
    private static int inventorySlots() {
        final int slots = Settings.range("INVENTORY_BASE_SLOTS", 24, 4, GameConstants.INVENTORY_SLOT_MAX);
        if (slots % 4 != 0) {
            throw new IllegalArgumentException("INVENTORY_BASE_SLOTS must be a multiple of 4");
        }
        return slots;
    }

    public static int applyExpRate(int exp) {
        return scaleExp(exp, EXP_RATE);
    }

    static int scaleExp(int exp, int rate) {
        return exp <= 0 ? exp : (int) Math.min((long) exp * rate, Integer.MAX_VALUE);
    }}

package kinoko.server;

import org.snakeyaml.engine.v2.api.Load;
import org.snakeyaml.engine.v2.api.LoadSettings;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/** File-backed preferences, with the inherited environment overrides preserved. */
public final class Settings {
    private Settings() {}

    private static final class FileValues {
        private static final Map<String, String> VALUES = read(Path.of(
                System.getenv().getOrDefault("OTONO_CONFIG", "../config.yaml")));
    }

    static Map<String, String> read(Path path) {
        if (!Files.exists(path)) {
            return Map.of();
        }
        try (InputStream input = Files.newInputStream(path)) {
            final Object document = new Load(LoadSettings.builder().setAllowDuplicateKeys(false).build())
                    .loadFromInputStream(input);
            if (document == null) {
                return Map.of();
            }
            if (!(document instanceof Map<?, ?> map)) {
                throw new IllegalArgumentException("Expected a settings mapping in " + path);
            }
            final Map<String, String> values = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!(entry.getKey() instanceof String key) ||
                        !(entry.getValue() instanceof String || entry.getValue() instanceof Number ||
                                entry.getValue() instanceof Boolean)) {
                    throw new IllegalArgumentException("Settings must have text keys and scalar values: " + path);
                }
                values.put(key, entry.getValue().toString());
            }
            return Map.copyOf(values);
        } catch (IOException e) {
            throw new IllegalArgumentException("Could not read settings: " + path, e);
        }
    }

    static String resolve(String name, String fallback, Map<String, String> file, Map<String, String> environment) {
        return environment.getOrDefault(name, file.getOrDefault(name, fallback));
    }

    public static String get(String name, String fallback) {
        return resolve(name, fallback, FileValues.VALUES, System.getenv());
    }

    public static int get(String name, int fallback) {
        return Integer.parseInt(get(name, Integer.toString(fallback)));
    }

    public static boolean get(String name, boolean fallback) {
        final String value = get(name, Boolean.toString(fallback));
        if (!value.equalsIgnoreCase("true") && !value.equalsIgnoreCase("false")) {
            throw new IllegalArgumentException(name + " must be true or false");
        }
        return Boolean.parseBoolean(value);
    }

    public static int range(String name, int fallback, int min, int max) {
        final int value = get(name, fallback);
        if (value < min || value > max) {
            throw new IllegalArgumentException(name + " must be between " + min + " and " + max);
        }
        return value;
    }
}

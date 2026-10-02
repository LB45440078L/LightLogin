package dev.lightlogin.core.support;

import dev.lightlogin.core.config.ConfigSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** A map-backed {@link ConfigSource} for tests, using dotted paths as flat keys. */
public final class MapConfigSource implements ConfigSource {

    private final Map<String, Object> values = new HashMap<>();

    public MapConfigSource put(String path, Object value) {
        values.put(path, value);
        return this;
    }

    public static MapConfigSource empty() {
        return new MapConfigSource();
    }

    @Override
    public String getString(String path, String fallback) {
        Object value = values.get(path);
        return value instanceof String s ? s : fallback;
    }

    @Override
    public int getInt(String path, int fallback) {
        Object value = values.get(path);
        return value instanceof Number n ? n.intValue() : fallback;
    }

    @Override
    public long getLong(String path, long fallback) {
        Object value = values.get(path);
        return value instanceof Number n ? n.longValue() : fallback;
    }

    @Override
    public double getDouble(String path, double fallback) {
        Object value = values.get(path);
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    @Override
    public boolean getBoolean(String path, boolean fallback) {
        Object value = values.get(path);
        return value instanceof Boolean b ? b : fallback;
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getStringList(String path) {
        Object value = values.get(path);
        return value instanceof List<?> list ? (List<String>) list : List.of();
    }

    @Override
    public Set<String> getKeys(String path) {
        String prefix = path + ".";
        return values.keySet().stream().filter(k -> k.startsWith(prefix)).collect(java.util.stream.Collectors.toSet());
    }

    @Override
    public boolean contains(String path) {
        return values.containsKey(path);
    }
}
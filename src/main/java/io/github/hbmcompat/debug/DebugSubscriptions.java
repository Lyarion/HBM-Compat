package io.github.hbmcompat.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only session state. Each subscriber has independent diagnostic history. */
public final class DebugSubscriptions {
    private final Map<UUID, Map<String, String>> subscribers = new HashMap<UUID, Map<String, String>>();

    public boolean toggle(UUID player) {
        if (subscribers.remove(player) != null) return false;
        subscribers.put(player, new HashMap<String, String>());
        return true;
    }

    public boolean active() { return !subscribers.isEmpty(); }
    public void remove(UUID player) { subscribers.remove(player); }
    public void clear() { subscribers.clear(); }

    public boolean shouldSend(UUID player, int playerDimension, int sourceDimension, String source, String reason) {
        Map<String, String> history = subscribers.get(player);
        if (history == null || playerDimension != sourceDimension) return false;
        String key = sourceDimension + ":" + source;
        return !reason.equals(history.put(key, reason));
    }

    public void reset(int dimension, String source) {
        for (Map<String, String> history : subscribers.values()) history.remove(dimension + ":" + source);
    }
}

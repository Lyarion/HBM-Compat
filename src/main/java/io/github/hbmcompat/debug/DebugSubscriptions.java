package io.github.hbmcompat.debug;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Server-thread-only session state. Each subscriber has independent diagnostic history. */
public final class DebugSubscriptions {
    private final Map<UUID, Subscription> subscribers = new HashMap<UUID, Subscription>();

    private static final class Subscription {
        final DebugFilter filter;
        final Map<String, String> history = new HashMap<String, String>();
        Subscription(DebugFilter filter) { this.filter = filter; }
    }

    public void enable(UUID player, DebugFilter filter) {
        subscribers.put(player, new Subscription(filter));
    }

    public boolean toggle(UUID player) {
        if (subscribers.remove(player) != null) return false;
        enable(player, DebugFilter.ALL);
        return true;
    }

    public boolean active() { return !subscribers.isEmpty(); }
    public boolean active(DebugFilter category) {
        for (Subscription subscription : subscribers.values()) {
            if (subscription.filter.accepts(category)) return true;
        }
        return false;
    }

    public void remove(UUID player) { subscribers.remove(player); }
    public void clear() { subscribers.clear(); }

    public boolean shouldSend(UUID player, int playerDimension, int sourceDimension, DebugFilter category, String source, String reason) {
        Subscription subscription = subscribers.get(player);
        if (subscription == null || playerDimension != sourceDimension || !subscription.filter.accepts(category)) return false;
        String key = sourceDimension + ":" + source;
        return !reason.equals(subscription.history.put(key, reason));
    }

    public void reset(int dimension, String source) {
        for (Subscription subscription : subscribers.values()) subscription.history.remove(dimension + ":" + source);
    }
}

package io.github.hbmcompat.debug;

import static org.junit.Assert.*;
import java.util.UUID;
import org.junit.Test;

public class DebugSubscriptionsTest {
    @Test public void subscriptionsAndHistoryAreIndependent() {
        DebugSubscriptions state = new DebugSubscriptions();
        UUID first = UUID.randomUUID(), second = UUID.randomUUID();
        assertFalse(state.active());
        assertFalse(state.shouldSend(first, 0, 0, "push@1,2,3", "blocked"));
        assertTrue(state.toggle(first));
        assertTrue(state.shouldSend(first, 0, 0, "push@1,2,3", "blocked"));
        assertFalse(state.shouldSend(first, 0, 0, "push@1,2,3", "blocked"));
        assertTrue(state.toggle(second));
        assertTrue(state.shouldSend(second, 0, 0, "push@1,2,3", "blocked"));
        assertFalse(state.toggle(first));
        assertFalse(state.shouldSend(first, 0, 0, "push@1,2,3", "new reason"));
        assertTrue(state.shouldSend(second, 0, 0, "push@1,2,3", "new reason"));
        assertTrue(state.toggle(first));
        assertTrue(state.shouldSend(first, 0, 0, "push@1,2,3", "blocked"));
    }

    @Test public void dimensionsAndSourcesDoNotShareHistory() {
        DebugSubscriptions state = new DebugSubscriptions();
        UUID player = UUID.randomUUID();
        state.toggle(player);
        assertFalse(state.shouldSend(player, 0, 1, "push@1,2,3", "blocked"));
        assertTrue(state.shouldSend(player, 1, 1, "push@1,2,3", "blocked"));
        assertTrue(state.shouldSend(player, 0, 0, "push@1,2,3", "blocked"));
        assertTrue(state.shouldSend(player, 0, 0, "bus@1,2,3", "blocked"));
        state.reset(0, "push@1,2,3");
        assertTrue(state.shouldSend(player, 0, 0, "push@1,2,3", "blocked"));
        assertFalse(state.shouldSend(player, 1, 1, "push@1,2,3", "blocked"));
        assertFalse(state.shouldSend(player, 0, 0, "bus@1,2,3", "blocked"));
    }

    @Test public void logoutAndServerStopDiscardSessionHistory() {
        DebugSubscriptions state = new DebugSubscriptions();
        UUID player = UUID.randomUUID();
        state.toggle(player);
        state.shouldSend(player, 0, 0, "push", "blocked");
        state.remove(player);
        assertFalse(state.active());
        assertFalse(state.shouldSend(player, 0, 0, "push", "blocked"));
        assertTrue(state.toggle(player));
        assertTrue(state.shouldSend(player, 0, 0, "push", "blocked"));
        state.clear();
        assertFalse(state.active());
        assertFalse(state.shouldSend(player, 0, 0, "push", "blocked"));
    }
}

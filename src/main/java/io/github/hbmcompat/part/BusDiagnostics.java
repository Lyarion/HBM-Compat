package io.github.hbmcompat.part;

import net.minecraft.tileentity.TileEntity;

import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.debug.DiagnosticMessage;
import io.github.hbmcompat.debug.DebugChat;

/**
 * Per-gate fluid-bus diagnostics. Each bus instance holds one of these and calls
 * {@link #report(TileEntity, String)} at every early-return / work outcome in its
 * tick loop. To avoid flooding the log at bus tick rate, a line is only emitted
 * when the reason string changes from the previous tick for that same bus.
 *
 * Enabled by the JVM log flag or a player chat subscription.
 */
public final class BusDiagnostics {

    private final String label;
    private String lastReason = null;

    public BusDiagnostics(String label) {
        this.label = label;
    }

    public static boolean enabled() {
        return HbmCompat.DEBUG_BUS || DebugChat.SUBSCRIPTIONS.active();
    }

    /**
     * @param self   the bus host tile (for coordinates); may be null
     * @param message localized description of the current gate/outcome
     */
    public void report(TileEntity self, DiagnosticMessage message) {
        DebugChat.report(self, label + ":" + System.identityHashCode(this), "bus:" + label, message);
        if (!HbmCompat.DEBUG_BUS) {
            return;
        }
        String reason = message.toString();
        if (reason.equals(lastReason)) {
            return;
        }
        lastReason = reason;
        String where = self == null ? "?" : (self.xCoord + "," + self.yCoord + "," + self.zCoord);
        HbmCompat.LOG.info("[bus:{} @ {}] {}", label, where, reason);
    }
}

package io.github.hbmcompat.part;

import net.minecraft.tileentity.TileEntity;

import io.github.hbmcompat.HbmCompat;

/**
 * Per-gate fluid-bus diagnostics. Each bus instance holds one of these and calls
 * {@link #report(TileEntity, String)} at every early-return / work outcome in its
 * tick loop. To avoid flooding the log at bus tick rate, a line is only emitted
 * when the reason string changes from the previous tick for that same bus.
 *
 * Entirely inert unless {@code -Dhbmcompat.debugBus=true} is on the JVM args.
 */
public final class BusDiagnostics {

    private final String label;
    private String lastReason = null;

    public BusDiagnostics(String label) {
        this.label = label;
    }

    /**
     * @param self   the bus host tile (for coordinates); may be null
     * @param reason short description of the current gate/outcome
     */
    public void report(TileEntity self, String reason) {
        if (!HbmCompat.DEBUG_BUS) {
            return;
        }
        if (reason.equals(lastReason)) {
            return;
        }
        lastReason = reason;
        String where = self == null ? "?" : (self.xCoord + "," + self.yCoord + "," + self.zCoord);
        HbmCompat.LOG.info("[bus:{} @ {}] {}", label, where, reason);
    }
}

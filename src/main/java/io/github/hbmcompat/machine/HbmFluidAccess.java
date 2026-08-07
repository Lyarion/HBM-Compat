package io.github.hbmcompat.machine;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.fluid.tank.FluidTank;

import api.hbm.fluidmk2.IFluidStandardReceiverMK2;
import api.hbm.fluidmk2.IFluidStandardSenderMK2;

/**
 * Uniform fluid-tank resolution for the fluid buses, covering both of the two
 * kinds of target we support:
 *
 * <ol>
 * <li>The four recipe-selection machines, via their {@link IHbmMachineDriver}
 * (input tanks = {@link IHbmMachineDriver#getInputTanks}, output tanks =
 * {@link IHbmMachineDriver#getOutputTanks}). The driver is always checked first,
 * so machine behaviour is unchanged.</li>
 * <li>Any HBM fluid <em>storage</em> block (Fluid Tank, Big-Ass Tank, BAT-9000,
 * Barrel, UF6/PuF6 tanks, drums, ...). These do not have a driver; instead they
 * implement HBM's generic {@code api.hbm.fluidmk2} interfaces. We key off the
 * interface rather than the concrete class, so every present and future MK2
 * fluid storage tile is covered automatically.</li>
 * </ol>
 *
 * <p>Storage direction is <b>mode-respecting</b>: {@link #sourceTanks} reads the
 * tile's {@code getSendingTanks()} and {@link #sinkTanks} its
 * {@code getReceivingTanks()}. Those accessors already return an empty array when
 * the player has the tank in a mode that forbids that direction (e.g. a barrel in
 * receive-only mode yields no sending tanks), so a bus will simply idle rather
 * than fight the tank's own GUI setting.</p>
 *
 * <h3>Two different questions, deliberately kept apart</h3>
 *
 * <p>{@link #sourceTanks}/{@link #sinkTanks} answer "what can I move <em>right
 * now</em>". Mode, fill level and recipe-driven tank typing all feed into them, so
 * the answer changes without anything happening to the block itself.
 *
 * <p>{@link #canEverSource}/{@link #canEverSink} answer the <em>structural</em>
 * question "could this target ever serve this direction at all", which changes only
 * when the block does.
 *
 * <p>Keeping those apart is not cosmetic. AE2 feeds a bus's {@code getTarget()}
 * into its sleep gate ({@code PartSharedItemBus.isSleeping()} is
 * {@code getTarget() == null || super.isSleeping()}), and a slept device is woken
 * only by {@code wakeDevice}/{@code alertDevice} — that is, by a neighbour block
 * change, a setting/upgrade change, or the node rejoining the grid. HBM fires none
 * of those when a tank's mode is switched or its fill changes. So gating sleep on
 * the dynamic answer lets a bus fall asleep next to a momentarily idle tank and
 * never wake up again, even after the player switches that tank to a usable mode
 * and fills it. Sleep must be gated on the structural answer only; the dynamic
 * answer belongs in the transfer path, which backs off with {@code SLOWER} (a
 * bounded slowdown, capped by {@code TickRates}) rather than sleeping.
 */
public final class HbmFluidAccess {

    private static final FluidTank[] EMPTY = new FluidTank[0];

    private HbmFluidAccess() {}

    /**
     * Tanks a bus may import <em>from</em> (tank &rarr; ME): machine output tanks,
     * or a storage tile's sending tanks.
     */
    public static FluidTank[] sourceTanks(TileEntity tile) {
        if (tile == null) {
            return EMPTY;
        }
        IHbmMachineDriver driver = HbmMachineDrivers.forTile(tile);
        if (driver != null) {
            FluidTank[] tanks = driver.getOutputTanks(tile);
            return tanks == null ? EMPTY : tanks;
        }
        if (tile instanceof IFluidStandardSenderMK2) {
            FluidTank[] tanks = ((IFluidStandardSenderMK2) tile).getSendingTanks();
            return tanks == null ? EMPTY : tanks;
        }
        return EMPTY;
    }

    /**
     * Tanks a bus may export <em>into</em> (ME &rarr; tank): machine input tanks,
     * or a storage tile's receiving tanks.
     */
    public static FluidTank[] sinkTanks(TileEntity tile) {
        if (tile == null) {
            return EMPTY;
        }
        IHbmMachineDriver driver = HbmMachineDrivers.forTile(tile);
        if (driver != null) {
            FluidTank[] tanks = driver.getInputTanks(tile);
            return tanks == null ? EMPTY : tanks;
        }
        if (tile instanceof IFluidStandardReceiverMK2) {
            FluidTank[] tanks = ((IFluidStandardReceiverMK2) tile).getReceivingTanks();
            return tanks == null ? EMPTY : tanks;
        }
        return EMPTY;
    }

    /** True if the tile offers at least one fluid source tank right now. */
    public static boolean hasSource(TileEntity tile) {
        return sourceTanks(tile).length > 0;
    }

    /** True if the tile offers at least one fluid sink tank right now. */
    public static boolean hasSink(TileEntity tile) {
        return sinkTanks(tile).length > 0;
    }

    /**
     * Whether this target could <em>ever</em> be imported from (tank &rarr; ME),
     * ignoring its current mode, fill and tank typing. Use this — never
     * {@link #hasSource} — wherever the answer feeds AE2's sleep gate; see the class
     * comment for why.
     *
     * <p>For a driven machine the tank layout is a fixed property of the machine
     * type, so the driver's array length is already the structural answer: an arc
     * welder or soldering station has no output tank and never will, and sleeping
     * next to one is correct. For an undriven MK2 storage tile the arrays are
     * mode-dependent, so the structural answer is merely "does it implement the
     * sending interface" — the mode itself is re-checked on every transfer attempt.
     */
    public static boolean canEverSource(TileEntity tile) {
        if (tile == null) {
            return false;
        }
        IHbmMachineDriver driver = HbmMachineDrivers.forTile(tile);
        if (driver != null) {
            FluidTank[] tanks = driver.getOutputTanks(tile);
            return tanks != null && tanks.length > 0;
        }
        return tile instanceof IFluidStandardSenderMK2;
    }

    /**
     * Whether this target could <em>ever</em> be exported into (ME &rarr; tank),
     * ignoring its current mode, fill and tank typing. The counterpart of
     * {@link #canEverSource}; the same sleep-gate reasoning applies.
     */
    public static boolean canEverSink(TileEntity tile) {
        if (tile == null) {
            return false;
        }
        IHbmMachineDriver driver = HbmMachineDrivers.forTile(tile);
        if (driver != null) {
            FluidTank[] tanks = driver.getInputTanks(tile);
            return tanks != null && tanks.length > 0;
        }
        return tile instanceof IFluidStandardReceiverMK2;
    }
}

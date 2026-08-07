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
            return driver.getOutputTanks(tile);
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
            return driver.getInputTanks(tile);
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
}

package io.github.hbmcompat.machine;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;
import java.util.function.Function;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.Fluid;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;

/** One bounded sweep of explicit machine outputs; the sink returns the accepted quantity. */
public final class OutputRecovery {
    public interface Sink {
        int insertItem(ItemStack offered);
        int insertFluid(Fluid fluid, int offered);
    }

    private OutputRecovery() {}

    public static boolean recover(Iterable<TileEntity> targets, Function<TileEntity, IHbmMachineDriver> drivers,
            Function<FluidType, Fluid> fluids, Sink sink) {
        Set<TileEntity> seen = Collections.newSetFromMap(new IdentityHashMap<TileEntity, Boolean>());
        boolean worked = false;
        for (TileEntity tile : targets) {
            if (tile == null || tile.isInvalid() || !seen.add(tile)) continue;
            IHbmMachineDriver driver = drivers.apply(tile);
            if (driver == null) continue;
            worked |= recover(tile, driver, fluids, sink);
        }
        return worked;
    }

    private static boolean recover(TileEntity tile, IHbmMachineDriver driver,
            Function<FluidType, Fluid> fluids, Sink sink) {
        boolean worked = false;
        if (tile instanceof IInventory) {
            IInventory inventory = (IInventory) tile;
            int[] slots = driver.getOutputSlots(tile);
            if (slots != null) for (int slot : slots) {
                if (slot < 0 || slot >= inventory.getSizeInventory()) continue;
                ItemStack stack = inventory.getStackInSlot(slot);
                if (stack == null || stack.stackSize <= 0) continue;
                int accepted = sink.insertItem(stack.copy());
                if (accepted > 0) {
                    inventory.decrStackSize(slot, accepted);
                    tile.markDirty();
                    worked = true;
                }
            }
        }
        FluidTank[] tanks = driver.getOutputTanks(tile);
        if (tanks != null) for (FluidTank tank : tanks) {
            if (tank == null || tank.getFill() <= 0 || tank.getPressure() != 0
                    || tank.getTankType() == null || tank.getTankType() == Fluids.NONE) continue;
            Fluid fluid = fluids.apply(tank.getTankType());
            if (fluid == null) continue;
            int accepted = sink.insertFluid(fluid, tank.getFill());
            if (accepted > 0) {
                tank.setFill(tank.getFill() - accepted);
                tile.markDirty();
                worked = true;
            }
        }
        return worked;
    }
}

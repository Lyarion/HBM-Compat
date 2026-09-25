package io.github.hbmcompat.machine;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public interface IHbmMachineDriver {

    String getMachineId();

    boolean supports(TileEntity tile);

    HbmRecipeMatch match(ICraftingPatternDetails details);

    boolean isBusy(TileEntity tile);

    boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs);

    default boolean isFactory() { return false; }

    default boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks inputs, FactoryAllocationMode mode) {
        return push(tile, match, inputs);
    }

    com.hbm.inventory.fluid.tank.FluidTank[] getInputTanks(TileEntity tile);

    int[] getOutputSlots(TileEntity tile);

    com.hbm.inventory.fluid.tank.FluidTank[] getOutputTanks(TileEntity tile);
}

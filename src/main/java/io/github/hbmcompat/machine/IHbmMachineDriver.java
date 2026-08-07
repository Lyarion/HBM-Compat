package io.github.hbmcompat.machine;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public interface IHbmMachineDriver {

    String getMachineId();

    boolean supports(TileEntity tile);

    HbmRecipeMatch match(ICraftingPatternDetails details);

    boolean isBusy(TileEntity tile);

    /**
     * True if the machine is not fully drained: it is processing, or holds any items
     * in input/output slots, or any fluid in input/output tanks. Distinct from
     * {@link #isBusy}, which only reports physical input-acceptance readiness. This is
     * the gate AE2 blocking mode uses ("don't push until the target is empty").
     */
    boolean hasContents(TileEntity tile);

    boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs);

    com.hbm.inventory.fluid.tank.FluidTank[] getInputTanks(TileEntity tile);

    int[] getOutputSlots(TileEntity tile);

    com.hbm.inventory.fluid.tank.FluidTank[] getOutputTanks(TileEntity tile);
}

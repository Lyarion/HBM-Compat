package io.github.hbmcompat.part;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.parts.PartFluidImportBus;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;

import appeng.api.config.Actionable;
import appeng.api.config.FuzzyMode;
import appeng.api.networking.energy.IEnergySource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.util.item.AEFluidStack;
import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.client.CompatTextures;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;
import io.github.hbmcompat.machine.HbmFluidAccess;
import io.github.hbmcompat.machine.HbmTargets;

public final class PartHbmFluidImportBus extends PartFluidImportBus {

    private final BusDiagnostics diag = new BusDiagnostics("fluid-import");

    public PartHbmFluidImportBus(ItemStack stack) {
        super(stack);
    }

    @Override
    public IIcon getFaceIcon() {
        return CompatTextures.HBM_FLUID_IMPORT.getIcon();
    }

    // Thin diagnostic wrapper over the AE2 base doBusWork(). Reports which gate the
    // tick reached (inactive / chunk-unloaded / structurally unusable target / no tank
    // available right now / attempting) only when -Dhbmcompat.debugBus=true. The base
    // loop then calls our overridden getTarget()/importStuff() below to do the actual
    // transfer.
    @Override
    protected TickRateModulation doBusWork() {
        TileEntity self = getHost() == null ? null : getHost().getTile();

        // Back off rather than let the base class sleep us. PartBaseImportBus.doBusWork
        // returns SLEEP when getTarget() == null, and a slept AE2 device is only woken by
        // wakeDevice/alertDevice -- a neighbour or setting change, or the node rejoining
        // the grid. HBM raises none of those when a tank's mode is switched or it is
        // filled, so sleeping on a tank that merely has nothing to give right now would
        // strand this bus permanently: the player switches the tank to send mode, fills
        // it, and nothing ever happens until the chunk reloads or the bus is replaced.
        // SLOWER is bounded by TickRates.ImportBus (max 40 ticks), and this early-out is
        // only a driver lookup plus an array-length check, so idling here is cheap.
        if (getProxy().isActive() && canDoBusWork() && !HbmFluidAccess.hasSource(resolveTarget())) {
            if (HbmCompat.DEBUG_BUS) {
                diag.report(self, "no fluid source tank available right now "
                        + "(storage tank not in send/both mode, or all output tanks empty/pressurized); polling");
            }
            return TickRateModulation.SLOWER;
        }

        if (HbmCompat.DEBUG_BUS) {
            if (!getProxy().isActive()) {
                diag.report(self, "gate: proxy inactive (no channel/power?)");
            } else if (!canDoBusWork()) {
                diag.report(self, "gate: target chunk not loaded");
            } else if (getTarget() == null) {
                diag.report(self, "gate: target can never be a fluid source "
                        + "(not an HBM machine/storage core, core unresolved, or machine w/o output tank "
                        + "e.g. arc welder/soldering station)");
            } else {
                diag.report(self, "ticking: source ok, attempting import");
            }
        }
        return super.doBusWork();
    }

    // Structural check only: "could this ever be a source", not "does it have fluid to
    // give right now". AE2 routes this through PartSharedItemBus.isSleeping(), which is
    // recomputed only on neighbour/setting changes -- so a dynamic answer here would let
    // the device sleep forever once an HBM tank went idle. The live availability check
    // lives in doBusWork()/importStuff() instead. See HbmFluidAccess for the full
    // reasoning.
    @Override
    protected Object getTarget() {
        TileEntity target = resolveTarget();
        return HbmFluidAccess.canEverSource(target) ? target : null;
    }

    private TileEntity resolveTarget() {
        TileEntity self = getHost().getTile();
        return HbmTargets.resolveCore(
                self.getWorldObj(),
                self.xCoord + getSide().offsetX,
                self.yCoord + getSide().offsetY,
                self.zCoord + getSide().offsetZ);
    }

    @Override
    protected boolean importStuff(
            Object targetObject,
            IAEFluidStack whatToImport,
            IMEMonitor<IAEFluidStack> network,
            IEnergySource energy,
            FuzzyMode fuzzyMode) {
        if (!(targetObject instanceof TileEntity)) {
            return true;
        }

        TileEntity target = (TileEntity) targetObject;

        for (FluidTank tank : HbmFluidAccess.sourceTanks(target)) {
            if (tank == null || tank.getFill() <= 0 || tank.getPressure() != 0 || tank.getTankType() == Fluids.NONE) {
                continue;
            }
            Fluid forgeFluid = HbmForgeFluidRegistry.getForgeFluid(tank.getTankType());
            if (forgeFluid == null) {
                continue;
            }
            if (whatToImport != null && !whatToImport.getFluidStack().isFluidEqual(new FluidStack(forgeFluid, 1))) {
                continue;
            }

            int amount = (int) Math.min((long) tank.getFill(), itemToSend);
            IAEFluidStack offered = AEFluidStack.create(new FluidStack(forgeFluid, amount));
            IAEFluidStack leftover = network.injectItems(offered, Actionable.MODULATE, mySrc);
            long remaining = leftover == null ? 0L : leftover.getStackSize();
            int accepted = amount - (int) remaining;
            if (accepted > 0) {
                tank.setFill(tank.getFill() - accepted);
                itemToSend -= accepted;
                worked = true;
                target.markDirty();
            }
            if (itemToSend <= 0) {
                break;
            }
        }
        return true;
    }
}

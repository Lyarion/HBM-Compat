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
    // tick reached (inactive / chunk-unloaded / no output-tank target / no fluid moved /
    // moved) only when -Dhbmcompat.debugBus=true. The base loop then calls our
    // overridden getTarget()/importStuff() below to do the actual transfer.
    @Override
    protected TickRateModulation doBusWork() {
        if (HbmCompat.DEBUG_BUS) {
            TileEntity self = getHost() == null ? null : getHost().getTile();
            if (!getProxy().isActive()) {
                diag.report(self, "gate: proxy inactive (no channel/power?)");
            } else if (!canDoBusWork()) {
                diag.report(self, "gate: target chunk not loaded");
            } else if (getTarget() == null) {
                diag.report(self, "gate: no fluid source tank at target "
                        + "(machine w/o output tank e.g. arc welder/soldering, or storage tank not in send/both mode)");
            } else {
                diag.report(self, "ticking: source ok, attempting import");
            }
        }
        return super.doBusWork();
    }

    @Override
    protected Object getTarget() {
        TileEntity target = resolveTarget();
        return HbmFluidAccess.hasSource(target) ? target : null;
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

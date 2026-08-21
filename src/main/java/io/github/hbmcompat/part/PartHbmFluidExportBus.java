package io.github.hbmcompat.part;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.IIcon;
import net.minecraftforge.fluids.FluidStack;

import com.glodblock.github.common.parts.PartFluidExportBus;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;

import appeng.api.config.Actionable;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.StorageName;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEStack;
import appeng.me.GridAccessException;
import appeng.tile.inventory.IAEStackInventory;
import appeng.util.Platform;
import io.github.hbmcompat.client.CompatTextures;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;
import io.github.hbmcompat.machine.HbmFluidAccess;
import io.github.hbmcompat.machine.HbmTargets;

public final class PartHbmFluidExportBus extends PartFluidExportBus {

    private final BusDiagnostics diag = new BusDiagnostics("export");

    public PartHbmFluidExportBus(ItemStack stack) {
        super(stack);
    }

    @Override
    public IIcon getFaceIcon() {
        return CompatTextures.HBM_FLUID_EXPORT.getIcon();
    }

    @Override
    protected TickRateModulation doBusWork() {
        TileEntity self = getHost().getTile();
        if (!getProxy().isActive() || !canDoBusWork()) {
            diag.report(self, "idle: proxy inactive or target chunk unloaded");
            return TickRateModulation.IDLE;
        }

        TileEntity target = getHbmTarget();
        FluidTank[] sinks = HbmFluidAccess.sinkTanks(target);
        if (sinks.length == 0) {
            // Keep polling rather than sleeping: a SLEEP here can trap the device asleep
            // until an external wake (neighbour/redstone change), which HBM tanks never fire.
            // Note this guards only the ticking path -- the sleep gate in isSleeping() reaches
            // the same trap via getTarget(), which is why that override is structural-only.
            // SLOWER is bounded by TickRates.ExportBus (max 60 ticks).
            diag.report(self, "no fluid sink tank at target "
                    + "(not an HBM machine/storage core, core unresolved, or storage tank not in receive/both mode)");
            return TickRateModulation.SLOWER;
        }

        try {
            IMEMonitor<IAEFluidStack> network = getMonitor();
            IEnergyGrid energy = getProxy().getEnergy();
            IAEStackInventory config = getAEInventoryByName(StorageName.CONFIG);
            long remainingBudget = calculateAmountToSend();
            boolean moved = false;
            boolean anyConfigured = false;
            String lastSkip = null;

            for (int slot = 0; slot < availableSlots() && remainingBudget > 0; slot++) {
                IAEFluidStack configured = (IAEFluidStack) config.getAEStackInSlot(slot);
                if (configured == null || configured.getFluidStack() == null) {
                    continue;
                }
                anyConfigured = true;
                FluidType hbmFluid = HbmForgeFluidRegistry.getHbmFluid(configured.getFluidStack().getFluid());
                if (hbmFluid == Fluids.NONE) {
                    lastSkip = "configured fluid '" + configured.getFluidStack().getFluid().getName()
                            + "' is not an HBM fluid (no mapping)";
                    continue;
                }

                FluidTank tank = findTank(sinks, hbmFluid);
                if (tank == null) {
                    lastSkip = "no input tank already set to " + hbmFluid.getName()
                            + " (tank full, untyped, typed for another fluid, or pressurized)"
                            + "; the bus never changes a tank's fluid type, so set it via the"
                            + " machine's recipe or the tank's own GUI first";
                    continue;
                }
                int free = tank.getMaxFill() - tank.getFill();
                int requestedAmount = (int) Math.min(remainingBudget, (long) free);
                if (requestedAmount <= 0) {
                    lastSkip = "input tank for " + hbmFluid.getName() + " is full";
                    continue;
                }

                IAEFluidStack request = configured.copy();
                request.setStackSize(requestedAmount);
                IAEFluidStack extracted = Platform.poweredExtraction(energy, network, request, mySrc);
                if (extracted == null || extracted.getStackSize() <= 0) {
                    lastSkip = "ME network has no " + hbmFluid.getName() + " to extract (or no power)";
                    continue;
                }

                int inserted = (int) extracted.getStackSize();
                // Fill only. We never write the tank type: findTank has already guaranteed the
                // tank is typed for exactly this fluid, so there is nothing to set.
                tank.setFill(tank.getFill() + inserted);
                remainingBudget -= inserted;
                moved = true;
            }

            if (moved) {
                diag.report(self, "OK: exporting fluid into machine input tank");
                target.markDirty();
                return TickRateModulation.FASTER;
            }
            if (!anyConfigured) {
                diag.report(self, "no fluid configured in bus GUI (nothing to export)");
            } else if (lastSkip != null) {
                diag.report(self, "no move: " + lastSkip);
            } else {
                diag.report(self, "no move: nothing to do");
            }
        } catch (GridAccessException ignored) {
            diag.report(self, "idle: grid access exception");
            return TickRateModulation.IDLE;
        }
        return TickRateModulation.SLOWER;
    }

    // AE2's PartSharedItemBus.isSleeping() is `getTarget() == null || super.isSleeping()`,
    // and the base getTarget() resolves an InventoryAdaptor against the *immediate* neighbour
    // block. For an HBM BlockDummyable multiblock that neighbour is frequently a dummy segment,
    // so the base path yields a null/unstable adaptor and the device registers as permanently
    // asleep -> tickingRequest()/doBusWork() never fire. Override it to resolve the multiblock
    // core (matching doBusWork) so the wake/sleep gate is correct.
    //
    // Structural check only: "could this ever be a sink", not "does it have room right now".
    // Returning the dynamic answer here would reintroduce the very trap doBusWork() avoids by
    // returning SLOWER instead of SLEEP -- just through the other door. isSleeping() is
    // consulted by updateState() (on neighbour, setting and upgrade changes) and by
    // getTickingRequest(), and it calls sleepDevice() whenever it is true. A tank that is
    // merely full, untyped or in the wrong mode at that instant would therefore still put this
    // bus to sleep, and HBM never fires a neighbour update to wake it again. Live availability
    // is re-checked on every tick in doBusWork(). See HbmFluidAccess for the full reasoning.
    @Override
    protected Object getTarget() {
        TileEntity target = getHbmTarget();
        return HbmFluidAccess.canEverSink(target) ? target : null;
    }

    // getTarget() above breaks an assumption the base class makes about itself: PartBaseExportBus
    // treats getTarget() as always yielding an InventoryAdaptor, and injectCraftedItems() acts on
    // that directly. An HBM machine core is a plain TileEntity, so on AE2 before rv3-beta-1035 the
    // base method reached `throw new IllegalStateException("Target is not a InventoryAdaptor")`;
    // 83cb720ef softened that to `return items`. Since the declared floor is rv3-beta-1024, the
    // throwing versions are inside the supported range, so pin the safe answer here rather than
    // depending on which AE2 the player happens to run.
    //
    // Returning `items` unchanged means "accepted none of it", which is the honest answer: this bus
    // has no item inventory to place a crafting result into, and pushing fluid into an HBM tank
    // requires the type-matching that doBusWork()/findTank() does. Rejected output stays in the ME
    // network, where the normal export loop picks it up on a later tick, so nothing is lost.
    //
    // Currently defensive rather than load-bearing: the doBusWork() override never calls
    // craftingTracker.handleCrafting(), so this bus requests no crafting jobs and AE2 holds no link
    // to inject against. That also means a Crafting Card in this bus does nothing - a separate gap,
    // not addressed here. The override exists so re-enabling that path cannot resurrect the throw.
    @Override
    public IAEStack<?> injectCraftedItems(final ICraftingLink link, final IAEStack<?> items,
            final Actionable mode) {
        return items;
    }

    private TileEntity getHbmTarget() {
        TileEntity self = getHost().getTile();
        return HbmTargets.resolveCore(
                self.getWorldObj(),
                self.xCoord + getSide().offsetX,
                self.yCoord + getSide().offsetY,
                self.zCoord + getSide().offsetZ);
    }

    // A tank is a valid destination only if its type ALREADY equals the fluid we are exporting.
    // The bus never assigns a tank's type -- not even an untyped (NONE) one.
    //
    // Tank type is owned by the target, never by us. On a machine input tank HBM's module sets it
    // from the active recipe (ModuleMachineBase.setupTanks -> FluidTank.conform/resetTank); on a
    // storage tank the player sets it via the tank's own GUI. Writing it from here is unsafe in
    // both directions: FluidTank.setTankType() zeroes the fill whenever the type actually changes,
    // so we would either fight the machine (it re-conforms the tank on the next
    // setRecipe/setupTanks, voiding whatever we just pushed, so we extract and void again in a
    // loop) or silently overwrite the player's configuration.
    //
    // Claiming a NONE tank is excluded too: on a multi-tank machine "first NONE tank" is not
    // necessarily the index the recipe wants (HBM pairs inputFluid[i] with inputTanks[i]), so
    // claiming one can put the fluid in the wrong slot and stall the recipe. Waiting for the
    // target to declare its own type has no such failure mode.
    //
    // This mirrors HBM's own contract: IFluidStandardReceiverMK2.getDemand/transferFluid only
    // ever consider tanks whose getTankType() already equals the incoming type, and never call
    // setTankType themselves.
    //
    // Iteration stays in array order so the lowest matching index wins.
    private FluidTank findTank(FluidTank[] tanks, FluidType type) {
        for (FluidTank tank : tanks) {
            if (tank == null || tank.getPressure() != 0 || tank.getFill() >= tank.getMaxFill()) {
                continue;
            }
            if (tank.getTankType() == type) {
                return tank;
            }
        }
        return null;
    }
}

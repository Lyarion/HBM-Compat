package io.github.hbmcompat.ae2;

import io.github.hbmcompat.debug.DiagnosticMessage;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.config.LockCraftingMode;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.energy.IEnergyGrid;
import appeng.api.networking.security.MachineSource;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.item.AEFluidStack;
import appeng.util.item.AEItemStack;

import io.github.hbmcompat.content.ItemAutoExtractCard;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;
import io.github.hbmcompat.machine.AdapterDiagnostics;
import io.github.hbmcompat.machine.FactoryAllocationMode;
import io.github.hbmcompat.machine.FeedingMode;
import io.github.hbmcompat.machine.HbmMachineDrivers;
import io.github.hbmcompat.machine.HbmRecipeMatch;
import io.github.hbmcompat.machine.HbmTargets;
import io.github.hbmcompat.machine.IHbmMachineDriver;
import io.github.hbmcompat.machine.OutputRecovery;
import io.github.hbmcompat.machine.PatternStacks;
import io.github.hbmcompat.pattern.HbmPatternMetadata;

public final class HbmDuality extends DualityInterface {

    private final IInterfaceHost host;
    private FactoryAllocationMode allocationMode = FactoryAllocationMode.PARALLEL_FIRST;
    private FeedingMode feedingMode = FeedingMode.SINGLE_BATCH;

    public FeedingMode getFeedingMode() { return feedingMode; }

    public void setFeedingMode(FeedingMode mode) {
        feedingMode = mode;
        getTile().markDirty();
        try {
            gridProxy.getTick().alertDevice(gridProxy.getNode());
        } catch (GridAccessException ignored) {}
    }

    public boolean hasMachineTarget() {
        for (ForgeDirection direction : host.getTargets()) {
            if (HbmMachineDrivers.forTile(target(direction)) != null) return true;
        }
        return false;
    }

    public boolean hasAutoExtractCard() {
        return ItemAutoExtractCard.isInstalled(getUpgrades());
    }

    @Override
    protected boolean hasWorkToDo() {
        // HBM does not notify AE2 when output slots/tanks fill. Poll even with no pending crafting task.
        // This also covers the parent's explicit sleep decisions in readConfig/onChangeInventory.
        return hasAutoExtractCard() || super.hasWorkToDo();
    }

    @Override
    public void onChangeInventory(IInventory inventory, int slot, InvOperation operation,
            ItemStack removed, ItemStack added) {
        super.onChangeInventory(inventory, slot, operation, removed, added);
        if (inventory == getUpgrades()) {
            try {
                gridProxy.getTick().alertDevice(gridProxy.getNode());
            } catch (GridAccessException ignored) {
                // Initial load or disconnected grid: getTickingRequest supplies the state on rejoin.
            }
        }
    }

    @Override
    public TickRateModulation tickingRequest(IGridNode node, int ticksSinceLastCall) {
        TickRateModulation original = super.tickingRequest(node, ticksSinceLastCall);
        if (!hasAutoExtractCard() || !gridProxy.isActive()) return original;
        try {
            final IEnergyGrid energy = gridProxy.getEnergy();
            final IMEMonitor<IAEItemStack> items = gridProxy.getStorage().getItemInventory();
            final IMEMonitor<IAEFluidStack> fluids = gridProxy.getStorage().getFluidInventory();
            final MachineSource source = new MachineSource(host);
            List<TileEntity> targets = new ArrayList<TileEntity>();
            for (ForgeDirection direction : host.getTargets()) targets.add(target(direction));
            boolean worked = OutputRecovery.recover(targets, HbmMachineDrivers::forTile,
                    HbmForgeFluidRegistry::getForgeFluid, new OutputRecovery.Sink() {
                        @Override public int insertItem(ItemStack offered) {
                            if (items == null || !gridProxy.isActive()) return 0;
                            IAEItemStack remainder = Platform.poweredInsert(energy, items,
                                    AEItemStack.create(offered), source);
                            return offered.stackSize - (remainder == null ? 0 : (int) remainder.getStackSize());
                        }
                        @Override public int insertFluid(Fluid fluid, int offered) {
                            if (fluids == null || !gridProxy.isActive()) return 0;
                            IAEFluidStack remainder = Platform.poweredInsert(energy, fluids,
                                    AEFluidStack.create(new FluidStack(fluid, offered)), source);
                            return offered - (remainder == null ? 0 : (int) remainder.getStackSize());
                        }
                    });
            if (worked) return TickRateModulation.FASTER;
        } catch (GridAccessException ignored) {
            // Nothing has been removed from a machine before acquiring the grid services.
        }
        return original == TickRateModulation.SLEEP ? TickRateModulation.SLOWER : original;
    }

    public FactoryAllocationMode getAllocationMode() { return allocationMode; }

    public void setAllocationMode(FactoryAllocationMode mode) {
        allocationMode = mode;
        getTile().markDirty();
        try {
            gridProxy.getTick().alertDevice(gridProxy.getNode());
        } catch (GridAccessException ignored) {}
    }

    public boolean hasFactoryTarget() {
        for (ForgeDirection direction : host.getTargets()) {
            IHbmMachineDriver driver = HbmMachineDrivers.forTile(target(direction));
            if (driver != null && driver.isFactory()) return true;
        }
        return false;
    }

    @Override
    public void writeToNBT(NBTTagCompound data) {
        super.writeToNBT(data);
        allocationMode.write(data);
        feedingMode.write(data);
    }

    @Override
    public void readFromNBT(NBTTagCompound data) {
        super.readFromNBT(data);
        allocationMode = FactoryAllocationMode.read(data);
        feedingMode = FeedingMode.read(data);
    }

    public HbmDuality(AENetworkProxy networkProxy, IInterfaceHost host) {
        super(networkProxy, host);
        this.host = host;
    }

    @Override
    protected void addToCraftingList(int slot) {
        ItemStack pattern = getPatterns().getStackInSlot(slot);
        if (pattern != null && pattern.getItem() instanceof ICraftingPatternItem) {
            ICraftingPatternDetails details = ((ICraftingPatternItem) pattern.getItem())
                    .getPatternForItem(pattern, getTile().getWorldObj());
            HbmRecipeMatch match = matchForCurrentTarget(details);
            if (match == null) {
                match = HbmMachineDrivers.uniqueMatch(details);
            }
            if (match != null) {
                HbmPatternMetadata.write(pattern, match);
            }
        }
        super.addToCraftingList(slot);
    }

    @Override
    public boolean pushPattern(ICraftingPatternDetails patternDetails, InventoryCrafting table) {
        if (hasItemsToSend() || !gridProxy.isActive() || craftingList == null || !craftingList.contains(patternDetails)
                || getCraftingLockedReason() != appeng.api.config.LockCraftingMode.NONE) {
            if (AdapterDiagnostics.enabled()) {
                AdapterDiagnostics.report(getTile(), preflightReason(patternDetails));
            }
            return false;
        }

        PatternStacks suppliedInputs = PatternStacks.fromInventory(table);
        if (!suppliedInputs.isValid()) {
            AdapterDiagnostics.report(getTile(), DiagnosticMessage.of(
                    "unreadable_inputs", "AE2 handed over inputs this adapter cannot read (an unsupported stack type, or a non-ME "
                    + "crafting table). Nothing to push."));
            return false;
        }

        // pushPattern runs at craft-tick rate, so none of the reason strings below are built unless
        // the debug flag is on.
        final boolean diagnose = AdapterDiagnostics.enabled();

        // Why the scan found no home for this pattern. Only the last direction's reason is kept:
        // with a fixed orientation there is just one target anyway, and reporting every side of an
        // unoriented adapter would bury the useful line.
        DiagnosticMessage rejection = diagnose ? DiagnosticMessage.of(
                "no_machine", "no machine adjacent to this adapter: the targeted side holds no supported HBM machine, or "
                + "its multiblock core could not be resolved (try breaking and replacing the machine)") : null;

        ItemStack encodedPattern = patternDetails.getPattern();
        for (ForgeDirection direction : host.getTargets()) {
            TileEntity target = target(direction);
            IHbmMachineDriver driver = HbmMachineDrivers.forTile(target);
            if (driver == null) {
                continue;
            }
            if (driver.isBusy(target, feedingMode)) {
                if (diagnose) {
                    rejection = DiagnosticMessage.of(
                            "machine_busy", "machine on the %s side is busy: it is mid-cycle, or its input slots still hold un-consumed items", direction);
                }
                continue;
            }

            HbmRecipeMatch match = driver.match(patternDetails);
            if (match == null) {
                if (diagnose) {
                    rejection = DiagnosticMessage.of(
                            "recipe_unmatched", "no unique HBM recipe matches this pattern on the %s. Inputs must match exactly; "
                            + "pressurised fluids are unsupported. Mixer patterns must also declare the exact fluid output and amount. "
                            + "For assembly and chemical recipes with identical inputs, outputs must select one candidate.", driver.getMachineId());
                }
                continue;
            }
            if (!HbmPatternMetadata.agrees(encodedPattern, match)) {
                if (diagnose) {
                    // agrees() only returns false when the pattern carries our tag, so it is present.
                    NBTTagCompound tag = encodedPattern.getTagCompound();
                    rejection = DiagnosticMessage.of(
                            "pattern_changed", "this pattern is stamped for %s/%s but now matches %s/%s. Re-encode the pattern.", tag.getString(HbmPatternMetadata.MACHINE_KEY), tag.getString(HbmPatternMetadata.RECIPE_KEY), match.getDriver().getMachineId(), match.getRecipeName());
                }
                continue;
            }
            if (driver.push(target, match, suppliedInputs, allocationMode, feedingMode)) {
                HbmPatternMetadata.write(encodedPattern, match);
                resetCraftingLock();
                AdapterDiagnostics.reset(getTile());
                try {
                    gridProxy.getTick().alertDevice(gridProxy.getNode());
                } catch (GridAccessException ignored) {
                    // The inputs are already committed to the machine; nothing more to do.
                    // An installed extraction card will poll again when the grid becomes available.
                }
                return true;
            }
            // driver.push() reported its own reason against the machine tile.
            return false;
        }
        if (diagnose) {
            AdapterDiagnostics.report(getTile(), rejection);
        }
        return false;
    }

    /** Which of the four combined pre-flight conditions rejected the push. */
    private DiagnosticMessage preflightReason(ICraftingPatternDetails patternDetails) {
        if (hasItemsToSend()) {
            return DiagnosticMessage.of(
                    "pending_items", "the adapter still has items waiting to be sent back into the network");
        }
        if (!gridProxy.isActive()) {
            return DiagnosticMessage.of(
                    "adapter_inactive", "the adapter is not active: no channel or no power. Note that the side the adapter points at "
                    + "cannot carry the ME cable — run the cable to another face.");
        }
        if (craftingList == null || !craftingList.contains(patternDetails)) {
            return DiagnosticMessage.of(
                    "pattern_unregistered", "this pattern is not registered on this adapter (its pattern slots do not hold it)");
        }
        return DiagnosticMessage.of(
                "crafting_locked", "crafting is locked on this adapter: %s", getCraftingLockedReason());
    }

    @Override
    public boolean isBusy() {
        // A crafting lock (e.g. LOCK_UNTIL_RESULT) always wins, and pending sends
        // mean the parent still has work in flight. Beyond that we compute our own
        // busy state instead of delegating to super.isBusy(): the parent's blocking
        // scan builds an InventoryAdaptor against the *immediate* neighbour tile,
        // which for a BlockDummyable multiblock is often a dummy segment -> the scan
        // is unreliable and would wedge the adapter busy forever.
        if (getCraftingLockedReason() != LockCraftingMode.NONE || hasItemsToSend()) {
            return true;
        }

        // Not busy iff at least one target can take a job right now, i.e. it is not
        // mid-cycle and its inputs are not still pending. Output backlog is
        // deliberately NOT considered: extraction is optional, and external buses/pipes
        // may also drain outputs. AE2's BLOCK/SMART_BLOCK settings remain ignored;
        // see HbmBlockingButtonHider for the GUI side.
        for (ForgeDirection direction : host.getTargets()) {
            TileEntity target = target(direction);
            IHbmMachineDriver driver = HbmMachineDrivers.forTile(target);
            if (driver == null) {
                continue;
            }
            if (!driver.isBusy(target, feedingMode)) {
                return false;
            }
        }
        return true;
    }

    private HbmRecipeMatch matchForCurrentTarget(ICraftingPatternDetails details) {
        HbmRecipeMatch result = null;
        for (ForgeDirection direction : host.getTargets()) {
            IHbmMachineDriver driver = HbmMachineDrivers.forTile(target(direction));
            if (driver == null) {
                continue;
            }
            HbmRecipeMatch match = driver.match(details);
            if (match != null) {
                if (result != null) {
                    return null;
                }
                result = match;
            }
        }
        return result;
    }

    private TileEntity target(ForgeDirection direction) {
        TileEntity tile = getTile();
        return HbmTargets.resolveCore(
                tile.getWorldObj(),
                tile.xCoord + direction.offsetX,
                tile.yCoord + direction.offsetY,
                tile.zCoord + direction.offsetZ);
    }
}

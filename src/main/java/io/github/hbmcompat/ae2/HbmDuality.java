package io.github.hbmcompat.ae2;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.config.LockCraftingMode;
import appeng.api.config.Settings;
import appeng.api.config.YesNo;
import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.me.GridAccessException;
import appeng.me.helpers.AENetworkProxy;
import io.github.hbmcompat.machine.AdapterDiagnostics;
import io.github.hbmcompat.machine.HbmMachineDrivers;
import io.github.hbmcompat.machine.HbmRecipeMatch;
import io.github.hbmcompat.machine.HbmTargets;
import io.github.hbmcompat.machine.IHbmMachineDriver;
import io.github.hbmcompat.machine.PatternStacks;
import io.github.hbmcompat.pattern.HbmPatternMetadata;

public final class HbmDuality extends DualityInterface {

    private final IInterfaceHost host;

    /**
     * Hash of the last pattern successfully pushed, for SMART_BLOCK. Mirrors the
     * parent's {@code lastInputHash}: under smart blocking a machine that still
     * holds contents may accept a new job iff it is the SAME recipe as last time.
     */
    private int lastInputHash = 0;

    public HbmDuality(AENetworkProxy networkProxy, IInterfaceHost host) {
        super(networkProxy, host);
        this.host = host;
    }

    /** AE2 GUI toggle: Settings.BLOCK == YES. */
    private boolean isBlocking() {
        return getConfigManager().getSetting(Settings.BLOCK) == YesNo.YES;
    }

    /** AE2 GUI toggle: Settings.SMART_BLOCK == YES (only meaningful with BLOCK on). */
    private boolean isSmartBlocking() {
        return getConfigManager().getSetting(Settings.SMART_BLOCK) == YesNo.YES;
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
            AdapterDiagnostics.report(
                    getTile(),
                    "AE2 handed over inputs this adapter cannot read (an unsupported stack type, or a"
                            + " non-ME crafting table). Nothing to push.");
            return false;
        }

        boolean blocking = isBlocking();
        boolean smartBlocking = blocking && isSmartBlocking();
        boolean sameRecipeAsLast = smartBlocking && lastInputHash == patternDetails.hashCode();

        // pushPattern runs at craft-tick rate, so none of the reason strings below are built unless
        // the debug flag is on.
        final boolean diagnose = AdapterDiagnostics.enabled();

        // Why the scan found no home for this pattern. Only the last direction's reason is kept:
        // with a fixed orientation there is just one target anyway, and reporting every side of an
        // unoriented adapter would bury the useful line.
        String rejection = diagnose ? "no machine adjacent to this adapter: the targeted side holds no supported"
                + " HBM machine, or its multiblock core could not be resolved (try breaking and"
                + " replacing the machine)" : null;

        ItemStack encodedPattern = patternDetails.getPattern();
        for (ForgeDirection direction : host.getTargets()) {
            TileEntity target = target(direction);
            IHbmMachineDriver driver = HbmMachineDrivers.forTile(target);
            if (driver == null) {
                continue;
            }
            if (driver.isBusy(target)) {
                if (diagnose) {
                    rejection = "machine on the " + direction + " side is busy: it is mid-cycle, or its input"
                            + " slots still hold un-consumed items";
                }
                continue;
            }

            // AE2 blocking mode: refuse a new job while the machine still holds anything
            // (input/output slots, tanks, or active progress). Smart blocking makes one
            // exception — the SAME recipe as last time may be re-pushed once its inputs
            // are consumed, so identical batches can pipeline without waiting for the
            // output to be fully drained.
            if (blocking && !sameRecipeAsLast && driver.hasContents(target)) {
                if (diagnose) {
                    rejection = "blocking mode is on and the machine on the " + direction + " side is not fully"
                            + " drained (progress, an input/output slot, or a tank still holds something)."
                            + " Pull the outputs away, or turn blocking off in this adapter's GUI.";
                }
                continue;
            }

            HbmRecipeMatch match = driver.match(patternDetails);
            if (match == null) {
                if (diagnose) {
                    rejection = "no unique HBM recipe matches this pattern on the " + driver.getMachineId()
                            + ". Either nothing matches (amounts must equal the recipe exactly; chance/multi-output"
                            + " recipes and pressurised fluids are not supported), or two recipes match and the"
                            + " choice is ambiguous.";
                }
                continue;
            }
            if (!HbmPatternMetadata.agrees(encodedPattern, match)) {
                if (diagnose) {
                    // agrees() only returns false when the pattern carries our tag, so it is present.
                    NBTTagCompound tag = encodedPattern.getTagCompound();
                    rejection = "this pattern is stamped for " + tag.getString(HbmPatternMetadata.MACHINE_KEY) + "/"
                            + tag.getString(HbmPatternMetadata.RECIPE_KEY) + " but now matches "
                            + match.getDriver().getMachineId() + "/" + match.getRecipeName()
                            + ". Re-encode the pattern.";
                }
                continue;
            }
            if (driver.push(target, match, suppliedInputs)) {
                HbmPatternMetadata.write(encodedPattern, match);
                if (smartBlocking) {
                    lastInputHash = patternDetails.hashCode();
                }
                resetCraftingLock();
                AdapterDiagnostics.reset(getTile());
                try {
                    gridProxy.getTick().alertDevice(gridProxy.getNode());
                } catch (GridAccessException ignored) {
                    // The inputs are already committed to the machine; nothing more to do.
                    // Outputs are pulled back by external buses/pipes, not by this adapter.
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
    private String preflightReason(ICraftingPatternDetails patternDetails) {
        if (hasItemsToSend()) {
            return "the adapter still has items waiting to be sent back into the network";
        }
        if (!gridProxy.isActive()) {
            return "the adapter is not active: no channel or no power. Note that the side the adapter"
                    + " points at cannot carry the ME cable — run the cable to another face.";
        }
        if (craftingList == null || !craftingList.contains(patternDetails)) {
            return "this pattern is not registered on this adapter (its pattern slots do not hold it)";
        }
        return "crafting is locked on this adapter: " + getCraftingLockedReason();
    }

    @Override
    public boolean isBusy() {
        // A crafting lock (e.g. LOCK_UNTIL_RESULT) always wins, and pending sends
        // mean the parent still has work in flight. Beyond that we compute our own
        // busy state instead of delegating to super.isBusy(): the parent's blocking
        // scan builds an InventoryAdaptor against the *immediate* neighbour tile,
        // which for a BlockDummyable multiblock is often a dummy segment -> the scan
        // is unreliable and would wedge the adapter busy forever under BLOCK=YES.
        if (getCraftingLockedReason() != LockCraftingMode.NONE || hasItemsToSend()) {
            return true;
        }

        boolean blocking = isBlocking();
        // Under smart blocking, "busy" cannot be decided globally here: a machine
        // that still holds contents may yet accept the SAME recipe. Defer the real
        // decision to pushPattern (mirrors the parent, which returns false here).
        boolean smartBlocking = blocking && isSmartBlocking();

        // Not busy iff at least one target can take a job right now. In plain
        // blocking mode "can take a job" means fully drained (hasContents == false);
        // otherwise it means the machine is not mid-cycle / inputs not pending
        // (driver.isBusy == false).
        for (ForgeDirection direction : host.getTargets()) {
            TileEntity target = target(direction);
            IHbmMachineDriver driver = HbmMachineDrivers.forTile(target);
            if (driver == null) {
                continue;
            }
            if (blocking && !smartBlocking) {
                if (!driver.hasContents(target)) {
                    return false;
                }
            } else if (!driver.isBusy(target)) {
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

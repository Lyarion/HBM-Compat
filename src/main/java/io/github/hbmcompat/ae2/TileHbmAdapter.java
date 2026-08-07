/*
 * This file is part of Applied Energistics 2. Copyright (c) 2013 - 2015, AlgorithmX2, All rights reserved. Applied
 * Energistics 2 is free software: you can redistribute it and/or modify it under the terms of the GNU Lesser General
 * Public License as published by the Free Software Foundation, either version 3 of the License, or (at your option) any
 * later version. Applied Energistics 2 is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser General
 * Public License for more details. You should have received a copy of the GNU Lesser General Public License along with
 * Applied Energistics 2. If not, see <http://www.gnu.org/licenses/lgpl>.
 */

package io.github.hbmcompat.ae2;

import static appeng.util.item.AEFluidStackType.FLUID_STACK_TYPE;
import static appeng.util.item.AEItemStackType.ITEM_STACK_TYPE;

import java.util.EnumSet;
import java.util.List;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import com.google.common.collect.ImmutableSet;

import appeng.api.AEApi;
import appeng.api.config.Actionable;
import appeng.api.config.Upgrades;
import appeng.api.implementations.IPowerChannelState;
import appeng.api.implementations.tiles.ITileStorageMonitorable;
import appeng.api.networking.IGridNode;
import appeng.api.networking.crafting.ICraftingLink;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.networking.crafting.ICraftingProviderHelper;
import appeng.api.networking.events.MENetworkChannelsChanged;
import appeng.api.networking.events.MENetworkCraftingPushedPattern;
import appeng.api.networking.events.MENetworkEventSubscribe;
import appeng.api.networking.events.MENetworkPowerStatusChange;
import appeng.api.networking.security.BaseActionSource;
import appeng.api.networking.ticking.IGridTickable;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.networking.ticking.TickingRequest;
import appeng.api.storage.IMEMonitor;
import appeng.api.storage.IStorageMonitorable;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.api.storage.data.IAEStackType;
import appeng.api.util.AECableType;
import appeng.api.util.DimensionalCoord;
import appeng.api.util.IConfigManager;
import appeng.helpers.DualityInterface;
import appeng.helpers.IInterfaceHost;
import appeng.helpers.IPrimaryGuiIconProvider;
import appeng.helpers.IPriorityHost;
import appeng.me.GridAccessException;
import appeng.tile.TileEvent;
import appeng.tile.events.TileEventType;
import appeng.tile.grid.AENetworkInvTile;
import appeng.tile.inventory.InvOperation;
import appeng.util.Platform;
import appeng.util.inv.IInventoryDestination;
import io.github.hbmcompat.content.BlockHbmProcessingAdapter;
import io.github.hbmcompat.content.ModContent;
import io.netty.buffer.ByteBuf;

public class TileHbmAdapter extends AENetworkInvTile
        implements IGridTickable, ITileStorageMonitorable, IStorageMonitorable, IInventoryDestination, IInterfaceHost,
        IPriorityHost, IPowerChannelState, IPrimaryGuiIconProvider {

    private final DualityInterface duality = new HbmDuality(this.getProxy(), this);
    private ForgeDirection pointAt = ForgeDirection.UNKNOWN;

    private static final int POWERED_FLAG = 1;
    private static final int CHANNEL_FLAG = 2;
    private static final int BOOTING_FLAG = 4;
    private static final int STUCK_FLAG = 8;
    private int clientFlags = 0; // sent as byte.

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkChannelsChanged c) {
        this.duality.notifyNeighbors();
        markForUpdate();
    }

    @MENetworkEventSubscribe
    public void stateChange(final MENetworkPowerStatusChange c) {
        this.duality.notifyNeighbors();
        markForUpdate();
    }

    @MENetworkEventSubscribe
    public void pushedPattern(final MENetworkCraftingPushedPattern c) {
        this.duality.notifyPushedPattern(c.host);
    }

    public void setSide(final ForgeDirection axis) {
        if (Platform.isClient()) {
            return;
        }

        if (this.pointAt == axis.getOpposite()) {
            this.pointAt = axis;
        } else if (this.pointAt == axis || this.pointAt == axis.getOpposite()) {
            this.pointAt = ForgeDirection.UNKNOWN;
        } else if (this.pointAt == ForgeDirection.UNKNOWN) {
            this.pointAt = axis.getOpposite();
        } else {
            this.pointAt = Platform.rotateAround(this.pointAt, axis);
        }

        if (ForgeDirection.UNKNOWN == this.pointAt) {
            this.setOrientation(this.pointAt, this.pointAt);
        } else {
            this.setOrientation(
                    this.pointAt.offsetY != 0 ? ForgeDirection.SOUTH : ForgeDirection.UP,
                    this.pointAt.getOpposite());
        }

        this.markForUpdate();
        this.markDirty();
    }

    @Override
    public void setOrientation(ForgeDirection Forward, ForgeDirection Up) {
        super.setOrientation(Forward, Up);
        pointAt = Up.getOpposite();
        this.getProxy().setValidSides(EnumSet.complementOf(EnumSet.of(pointAt)));
    }

    @Override
    public void markDirty() {
        this.duality.markDirty();
    }

    @Override
    public void getDrops(final World w, final int x, final int y, final int z, final List<ItemStack> drops) {
        this.duality.addDrops(drops);
    }

    @Override
    public void gridChanged() {
        this.duality.gridChanged();
    }

    @Override
    public void onReady() {
        this.getProxy().setValidSides(EnumSet.complementOf(EnumSet.of(this.pointAt)));
        super.onReady();
        this.duality.initialize();
    }

    @TileEvent(TileEventType.WORLD_NBT_WRITE)
    public void writeToNBT_TileInterface(final NBTTagCompound data) {
        data.setInteger("pointAt", this.pointAt.ordinal());
        this.duality.writeToNBT(data);
    }

    @TileEvent(TileEventType.WORLD_NBT_READ)
    public void readFromNBT_TileInterface(final NBTTagCompound data) {
        final int val = data.getInteger("pointAt");
        this.pointAt = ForgeDirection.getOrientation(val);
        this.duality.readFromNBT(data);
    }

    @Override
    public AECableType getCableConnectionType(final ForgeDirection dir) {
        return this.duality.getCableConnectionType(dir);
    }

    @Override
    public DimensionalCoord getLocation() {
        return this.duality.getLocation();
    }

    @Override
    public boolean canInsert(final ItemStack stack) {
        return this.duality.canInsert(stack);
    }

    @Override
    public IMEMonitor<IAEItemStack> getItemInventory() {
        return this.duality.getItemInventory();
    }

    @Override
    public IMEMonitor<IAEFluidStack> getFluidInventory() {
        return this.duality.getFluidInventory();
    }

    @Override
    public @Nullable IMEMonitor<?> getMEMonitor(@Nonnull IAEStackType<?> type) {
        return this.duality.getMEMonitor(type);
    }

    @Override
    public IInventory getInventoryByName(final String name) {
        return this.duality.getInventoryByName(name);
    }

    @Override
    public TickingRequest getTickingRequest(final IGridNode node) {
        return this.duality.getTickingRequest(node);
    }

    @Override
    public TickRateModulation tickingRequest(final IGridNode node, final int ticksSinceLastCall) {
        return this.duality.tickingRequest(node, ticksSinceLastCall);
    }

    @Override
    public IInventory getInternalInventory() {
        return this.duality.getInternalInventory();
    }

    @Override
    public void onChangeInventory(final IInventory inv, final int slot, final InvOperation mc, final ItemStack removed,
            final ItemStack added) {
        this.duality.onChangeInventory(inv, slot, mc, removed, added);
    }

    @Override
    public int[] getAccessibleSlotsBySide(final ForgeDirection side) {
        return this.duality.getAccessibleSlotsFromSide(side.ordinal());
    }

    @Override
    public DualityInterface getInterfaceDuality() {
        return this.duality;
    }

    /**
     * DualityInterface only advertises fluids for AE2FC dual hosts (IDualHost). This adapter handles HBM recipes whose
     * inputs mix items and fluids, so it must advertise both types itself, otherwise the ME Interface Terminal paints a
     * type-mismatch overlay over every pattern carrying a fluid input.
     */
    @Override
    public IAEStackType<?>[] getSupportedStackTypes() {
        return new IAEStackType<?>[] { ITEM_STACK_TYPE, FLUID_STACK_TYPE };
    }

    @Override
    public EnumSet<ForgeDirection> getTargets() {
        if (this.pointAt == null || this.pointAt == ForgeDirection.UNKNOWN) {
            return EnumSet.complementOf(EnumSet.of(ForgeDirection.UNKNOWN));
        }
        return EnumSet.of(this.pointAt);
    }

    @Override
    public TileEntity getTileEntity() {
        return this;
    }

    @Override
    public IStorageMonitorable getMonitorable(final ForgeDirection side, final BaseActionSource src) {
        return this.duality.getMonitorable(side, src, this);
    }

    @Override
    public IConfigManager getConfigManager() {
        return this.duality.getConfigManager();
    }

    @Override
    public boolean pushPattern(final ICraftingPatternDetails patternDetails, final InventoryCrafting table) {
        return this.duality.pushPattern(patternDetails, table);
    }

    @Override
    public boolean isBusy() {
        return this.duality.isBusy();
    }

    @Override
    public void provideCrafting(final ICraftingProviderHelper craftingTracker) {
        this.duality.provideCrafting(craftingTracker);
    }

    @Override
    public int getInstalledUpgrades(final Upgrades u) {
        return this.duality.getInstalledUpgrades(u);
    }

    @Override
    public ImmutableSet<ICraftingLink> getRequestedJobs() {
        return this.duality.getRequestedJobs();
    }

    @Override
    public IAEStack<?> injectCraftedItems(final ICraftingLink link, final IAEStack<?> items, final Actionable mode) {
        return this.duality.injectCraftedItems(link, items, mode);
    }

    @Override
    public void jobStateChange(final ICraftingLink link) {
        this.duality.jobStateChange(link);
    }

    @Override
    public int getPriority() {
        return this.duality.getPriority();
    }

    @Override
    public void setPriority(final int newValue) {
        this.duality.setPriority(newValue);
    }

    @TileEvent(TileEventType.NETWORK_READ)
    public boolean readFromStream_TileInterface(final ByteBuf data) {
        final boolean oldStuck = isStuck();
        final int newState = data.readByte();
        boolean changed = false;
        if (newState != clientFlags) {
            clientFlags = newState;
            this.markForUpdate();
            changed = true;
        }

        if (oldStuck != isStuck()) {
            this.markForUpdate();
            changed = true;
        }

        return changed;
    }

    @TileEvent(TileEventType.NETWORK_WRITE)
    public void writeToStream_TileInterface(final ByteBuf data) {
        clientFlags = 0;
        try {
            if (this.getProxy().getEnergy().isNetworkPowered()) clientFlags |= POWERED_FLAG;

            if (this.getProxy().getNode().meetsChannelRequirements()) clientFlags |= CHANNEL_FLAG;

            if (this.getProxy().getPath().isNetworkBooting()) clientFlags |= BOOTING_FLAG;
        } catch (final GridAccessException e) {
            // meh
        }
        if (duality.somethingStuck) clientFlags |= STUCK_FLAG;
        data.writeByte(clientFlags);
    }

    @Override
    public boolean isPowered() {
        return (clientFlags & POWERED_FLAG) == POWERED_FLAG;
    }

    @Override
    public boolean isActive() {
        return (clientFlags & CHANNEL_FLAG) == CHANNEL_FLAG;
    }

    @Override
    public boolean isBooting() {
        return (clientFlags & BOOTING_FLAG) == BOOTING_FLAG;
    }

    public boolean isStuck() {
        return (clientFlags & STUCK_FLAG) == STUCK_FLAG;
    }

    /**
     * The adapter block as an item, or null before {@link ModContent} has registered it.
     */
    private static ItemStack adapterStack() {
        return ModContent.processingAdapter == null ? null : new ItemStack(ModContent.processingAdapter);
    }

    /**
     * AEBaseTile.getItemFromTile() resolves through a registry that only AE2's own tiles are entered into, so it
     * returns null here. Return the adapter block directly instead, otherwise the ME Interface Terminal has no icon
     * for this entry and falls back to the neighbouring machine's - or to nothing at all.
     */
    @Override
    public ItemStack getSelfRep() {
        final ItemStack self = adapterStack();
        return self != null ? self : this.getItemFromTile(this);
    }

    /**
     * IInterfaceHost defaults this to DualityInterface.getCrafterIcon(), which scans the adjacent block and hands back
     * whatever it finds (an ME Interface, if one happens to be next door). This adapter is its own machine, so it
     * represents itself.
     */
    @Override
    public ItemStack getDisplayRep() {
        final ItemStack self = adapterStack();
        return self != null ? self : this.getInterfaceDuality().getCrafterIcon();
    }

    /**
     * Reproduces DualityInterface.getRawTermName() - the adjacent machine's name, as AE2 lists every interface - but
     * without its opening hasCustomName() shortcut, which hasCustomName() below forces down the custom-name path for
     * the GUI title's sake. Only a name the player set by hand should short-circuit this. getCrafterIcon() stamps a
     * display name onto the stack it returns when a custom name is reported, but that writes the display.Name tag and
     * leaves getUnlocalizedName() alone, so the listed name is unaffected.
     */
    @Override
    public String getRawName() {
        if (super.hasCustomName()) {
            return super.getCustomName();
        }
        final ItemStack icon = this.getInterfaceDuality().getCrafterIcon();
        return icon != null ? icon.getUnlocalizedName() : "Nothing";
    }

    /**
     * AE2's stock interface GUI hardcodes its title to GuiText.Interface ("ME 接口") and only deviates when the
     * container carries a custom name, which AEBaseContainer takes from the tile. Reporting a name unconditionally is
     * the only hook AE2 offers for retitling that GUI without replacing it wholesale.
     */
    @Override
    public boolean hasCustomName() {
        return true;
    }

    /**
     * AEBaseContainer.sendCustomName() runs server-side and the client renders the string verbatim, so this has to be
     * resolved here. A dedicated server has no mod language table, hence the English literal as a last resort - better
     * than a raw translation key in the GUI title. Single-player and LAN hosts do have the table and localise properly.
     */
    @Override
    public String getCustomName() {
        if (super.hasCustomName()) {
            return super.getCustomName();
        }
        final String key = "tile." + BlockHbmProcessingAdapter.REGISTRY_NAME + ".name";
        return StatCollector.canTranslate(key) ? StatCollector.translateToLocal(key) : "ME-HBM Processing Adapter";
    }

    @Override
    public ItemStack getPrimaryGuiIcon() {
        final ItemStack self = adapterStack();
        return self != null ? self : AEApi.instance().definitions().blocks().iface().maybeStack(1).orNull();
    }

}

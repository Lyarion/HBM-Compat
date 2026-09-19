package io.github.hbmcompat.fluid;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.api.IStackStringifyHandler;

public final class HbmFluidStackStringifyHandler implements IStackStringifyHandler {

    public static final String VIRTUAL_STACK_MARKER = "neeHbmCompatFluid";
    private static final String SERIALIZED_STACK_MARKER = "neeHbmCompatSerializedFluid";

    /**
     * Newer NEI/NEE checks this before calling getFluid during recipe transfer.
     * Intentionally no @Override: the minimum supported NEI predates this default
     * interface method, but the matching signature overrides it on newer versions.
     * Real containers and identifiers remain items, not recipe display carriers.
     */
    public boolean isFluidDisplayItem(ItemStack stack) {
        return getIconFluid(stack) != null;
    }

    @Override
    public NBTTagCompound convertItemStackToNBT(ItemStack stack, boolean saveStackSize) {
        // Deliberately icon-only, NOT getFluid(). Identifiers are type markers, not
        // fluid carriers: claiming them here would make NEI report their amount as a
        // bucket and route them through our round-trip, changing bookmarks and GUIDs
        // for an ordinary item. AE2's marking paths call getFluid() directly.
        FluidStack fluidStack = getIconFluid(stack);
        if (fluidStack == null) {
            return null;
        }

        NBTTagCompound result = new NBTTagCompound();
        result.setString("gtFluidName", fluidStack.getFluid().getName());
        result.setInteger("Count", saveStackSize ? fluidStack.amount : 1);
        result.setBoolean(SERIALIZED_STACK_MARKER, true);
        result.setString("strId", Item.itemRegistry.getNameForObject(stack.getItem()));
        result.setInteger("Damage", stack.getItemDamage());
        if (stack.hasTagCompound()) {
            result.setTag("tag", stack.getTagCompound().copy());
        }
        return result;
    }

    @Override
    public ItemStack convertNBTToItemStack(NBTTagCompound nbtTag) {
        if (nbtTag == null || !nbtTag.getBoolean(SERIALIZED_STACK_MARKER)) {
            return null;
        }

        Object registeredItem = Item.itemRegistry.getObject(nbtTag.getString("strId"));
        if (!(registeredItem instanceof Item)) {
            return null;
        }

        ItemStack stack = new ItemStack(
                (Item) registeredItem,
                Math.max(0, nbtTag.getInteger("Count")),
                nbtTag.getInteger("Damage"));
        if (nbtTag.hasKey("tag")) {
            stack.setTagCompound((NBTTagCompound) nbtTag.getCompoundTag("tag").copy());
        }
        return stack;
    }

    @Override
    public FluidStack getFluid(ItemStack stack) {
        FluidStack fromIcon = getIconFluid(stack);
        return fromIcon != null ? fromIcon : getIdentifierFluid(stack);
    }

    /** HBM fluid-icon display stack -> Forge fluid, carrying the icon's own mB. */
    private static FluidStack getIconFluid(ItemStack stack) {
        if (!HbmFluidAccess.isFluidIcon(stack) || HbmFluidAccess.getPressure(stack) != 0) {
            return null;
        }

        com.hbm.inventory.fluid.FluidType hbmFluid = HbmFluidAccess.getFluidType(stack);
        if (hbmFluid == null) {
            return null;
        }

        Fluid forgeFluid = HbmForgeFluidRegistry.getForgeFluid(hbmFluid);
        if (forgeFluid == null) {
            return null;
        }

        int amount = isVirtualStack(stack) ? stack.stackSize : HbmFluidAccess.getQuantity(stack);
        return amount > 0 ? new FluidStack(forgeFluid, amount) : null;
    }

    /**
     * HBM fluid identifier ("流体识别码") -> Forge fluid, so it can be dropped into any
     * AE2 fluid config slot to mark that fluid.
     *
     * <p>Three separate call sites converge here, which is why this belongs in the
     * StackInfo handler rather than in any one of them: AE2's
     * {@code AEFluidStackType.convertStackFromItem} (phantom config slots, monitor
     * parts), AE2's {@code FluidUtils.getFluidFromContainer}, and AE2FC's
     * {@code Util.getFluidFromItem} (fluid bus and storage-bus config). All three
     * first rule out real containers and then fall through to
     * {@code StackInfo.getFluid}.
     *
     * <p>An identifier carries a type but no volume, so it reports one bucket —
     * matching {@code AEFluidStackType.getAmountPerUnit()}. This handler alone
     * never makes the identifier a container. Other mods (notably
     * Bob) may independently register it in {@code FluidContainerRegistry}; that
     * registration and its fill/drain behavior are outside this handler's control.
     */
    private static FluidStack getIdentifierFluid(ItemStack stack) {
        com.hbm.inventory.fluid.FluidType hbmFluid = HbmFluidAccess.getIdentifierFluidType(stack);
        if (hbmFluid == null) {
            return null;
        }

        Fluid forgeFluid = HbmForgeFluidRegistry.getForgeFluid(hbmFluid);
        if (forgeFluid == null) {
            return null;
        }

        return new FluidStack(forgeFluid, FluidContainerRegistry.BUCKET_VOLUME);
    }

    public static boolean isVirtualStack(ItemStack stack) {
        return stack.hasTagCompound() && stack.getTagCompound().getBoolean(VIRTUAL_STACK_MARKER);
    }

    public static void markVirtualStack(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setBoolean(VIRTUAL_STACK_MARKER, true);
    }
}

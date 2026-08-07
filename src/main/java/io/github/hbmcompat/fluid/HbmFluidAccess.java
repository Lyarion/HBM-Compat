package io.github.hbmcompat.fluid;

import net.minecraft.item.ItemStack;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.items.machine.ItemFluidIcon;

/**
 * Thin, direct-typed accessor for HBM's fluid-icon items and fluid registry.
 *
 * <p>Previously reflection-based (HBM was not on the classpath). Now that HBM is
 * a compile dependency, these are plain typed calls. Kept as a facade so the rest
 * of the mod does not touch HBM item internals directly.
 */
public final class HbmFluidAccess {

    private HbmFluidAccess() {}

    /** True if the stack is an HBM {@link ItemFluidIcon} display carrier. */
    public static boolean isFluidIcon(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemFluidIcon;
    }

    /** The "fill" (mB) tag on an HBM fluid icon, or 0. */
    public static int getQuantity(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound().getInteger("fill") : 0;
    }

    /** The "pressure" tag on an HBM fluid icon, or 0. Non-zero pressure is unsupported. */
    public static int getPressure(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound().getInteger("pressure") : 0;
    }

    /** The {@link FluidType} an HBM fluid-icon stack represents, or {@code null}. */
    public static FluidType getFluidType(ItemStack stack) {
        if (!isFluidIcon(stack)) {
            return null;
        }
        FluidType type = Fluids.fromID(stack.getItemDamage());
        return type == Fluids.NONE ? null : type;
    }

    /** True if the stack is an HBM fluid identifier ("流体识别码"). */
    public static boolean isFluidIdentifier(ItemStack stack) {
        return stack != null && stack.getItem() instanceof IItemFluidIdentifier;
    }

    /**
     * The {@link FluidType} an HBM fluid identifier is currently set to, or
     * {@code null} if it is unset or not an identifier at all.
     *
     * <p>Read through {@link IItemFluidIdentifier} rather than off item damage:
     * the multi-type identifier keeps the authoritative selection in NBT and only
     * mirrors it into damage on the next edit, so damage can lag behind. The
     * interface contract explicitly allows a null world and a dummy position when
     * the identifier is inspected from a GUI rather than in-world, which is the
     * case for every AE2 config slot.
     */
    public static FluidType getIdentifierFluidType(ItemStack stack) {
        if (!isFluidIdentifier(stack)) {
            return null;
        }
        FluidType type = ((IItemFluidIdentifier) stack.getItem()).getType(null, 0, 0, 0, stack);
        return type == null || type == Fluids.NONE ? null : type;
    }

    /** Stable HBM name of a fluid type, or "unknown". */
    public static String getFluidName(FluidType fluidType) {
        return fluidType == null ? "unknown" : fluidType.getName();
    }

    /** All HBM fluid types (including NONE, as HBM returns them). */
    public static FluidType[] getAllFluidTypes() {
        return Fluids.getAll();
    }

    /** Build an HBM fluid-icon display stack, or {@code null}. */
    public static ItemStack makeFluidIcon(FluidType fluidType, int amount) {
        if (fluidType == null || amount <= 0) {
            return null;
        }
        return ItemFluidIcon.make(fluidType, amount);
    }
}

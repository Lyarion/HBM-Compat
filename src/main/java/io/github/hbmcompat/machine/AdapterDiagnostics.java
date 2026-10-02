package io.github.hbmcompat.machine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.RecipesCommon.AStack;

import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.debug.DiagnosticMessage;
import io.github.hbmcompat.debug.DebugChat;
import io.github.hbmcompat.debug.DebugFilter;

/**
 * Per-adapter push diagnostics. {@link io.github.hbmcompat.ae2.HbmDuality#pushPattern} and the
 * machine drivers have a dozen silent {@code return false} gates between "AE2 asked us to craft"
 * and "inputs are in the machine"; when a pattern will not run there is otherwise nothing in the
 * log to say which gate rejected it.
 *
 * <p>Mirrors {@link io.github.hbmcompat.part.BusDiagnostics}: a line is only emitted when the
 * reason changes for that same adapter position, so a permanently stuck adapter logs once rather
 * than at tick rate. Dedup state is keyed by position because the drivers in
 * {@link HbmMachineDrivers} are shared singletons and cannot hold per-adapter state. Server ticks
 * are single-threaded, so the plain HashMap is safe here.
 *
 * <p>Enabled by the JVM log flag or a player chat subscription.
 */
public final class AdapterDiagnostics {

    private static final Map<String, String> LAST_REASON = new HashMap<String, String>();

    private AdapterDiagnostics() {}

    /** True when the debug flag is on. Guard expensive reason-string building with this. */
    public static boolean enabled() {
        return HbmCompat.DEBUG_ADAPTER || DebugChat.SUBSCRIPTIONS.active(DebugFilter.ADAPTER);
    }

    /**
     * @param self   the adapter tile (for coordinates); may be null
     * @param message localized description of the gate that rejected the push
     */
    public static void report(TileEntity self, DiagnosticMessage message) {
        DebugChat.report(self, "push", "push", message);
        if (!HbmCompat.DEBUG_ADAPTER) {
            return;
        }
        String where = logKey(self);
        String reason = message.toString();
        if (reason.equals(LAST_REASON.get(where))) {
            return;
        }
        LAST_REASON.put(where, reason);
        HbmCompat.LOG.info("[push @ {}] {}", where, reason);
    }

    /**
     * Clears the dedup memory for one adapter, so the next rejection is logged even if it repeats
     * the previous reason. Called on a successful push: the next failure after a good run is news.
     */
    public static void reset(TileEntity self) {
        DebugChat.reset(self, "push");
        if (!HbmCompat.DEBUG_ADAPTER || self == null) {
            return;
        }
        LAST_REASON.remove(logKey(self));
    }

    public static void clear() { LAST_REASON.clear(); }

    private static String logKey(TileEntity self) {
        return (self == null || self.getWorldObj() == null ? "?" : self.getWorldObj().provider.dimensionId)
                + ":" + DebugChat.position(self);
    }

    /** "4xitem.foo, 1xitem.bar" — the items AE2 actually handed us. */
    public static String describeItems(List<ItemStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "(none)";
        }
        StringBuilder out = new StringBuilder();
        for (ItemStack stack : stacks) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(stack.stackSize).append('x').append(name(stack));
        }
        return out.toString();
    }

    /** "4xitem.foo" — what the HBM recipe asked for. */
    public static String describeIngredients(AStack[] expected) {
        if (expected == null || expected.length == 0) {
            return "(none)";
        }
        StringBuilder out = new StringBuilder();
        for (AStack ingredient : expected) {
            if (out.length() > 0) {
                out.append(", ");
            }
            if (ingredient == null) {
                out.append("(null)");
                continue;
            }
            out.append(ingredient.stacksize).append('x').append(describeIngredient(ingredient));
        }
        return out.toString();
    }

    /** "1000xhbm:water" — HBM-side fluid expectations, with pressure when non-zero. */
    public static String describeHbmFluids(com.hbm.inventory.FluidStack[] expected) {
        if (expected == null || expected.length == 0) {
            return "(none)";
        }
        StringBuilder out = new StringBuilder();
        for (com.hbm.inventory.FluidStack stack : expected) {
            if (out.length() > 0) {
                out.append(", ");
            }
            if (stack == null) {
                out.append("(null)");
                continue;
            }
            out.append(stack.fill).append('x').append(stack.type == null ? "?" : stack.type.getName());
            if (stack.pressure != 0) {
                out.append(" @pressure ").append(stack.pressure);
            }
        }
        return out.toString();
    }

    /** "1000xwater" — the Forge fluids AE2 handed us. */
    public static String describeFluids(List<FluidStack> stacks) {
        if (stacks == null || stacks.isEmpty()) {
            return "(none)";
        }
        StringBuilder out = new StringBuilder();
        for (FluidStack stack : stacks) {
            if (out.length() > 0) {
                out.append(", ");
            }
            out.append(stack.amount).append('x')
                    .append(stack.getFluid() == null ? "?" : stack.getFluid().getName());
        }
        return out.toString();
    }

    private static String describeIngredient(AStack ingredient) {
        // AStack has no name accessor; its NEI expansion is the cheapest readable handle on what
        // it accepts. An OreDictStack expands to every member of the ore name, so cap the list.
        List<ItemStack> candidates = ingredient.extractForNEI();
        if (candidates == null || candidates.isEmpty()) {
            return ingredient.toString();
        }
        String first = name(candidates.get(0));
        return candidates.size() == 1 ? first : (first + " (+" + (candidates.size() - 1) + " alt)");
    }

    private static String name(ItemStack stack) {
        if (stack == null || stack.getItem() == null) {
            return "(empty)";
        }
        try {
            return stack.getUnlocalizedName() + "@" + stack.getItemDamage();
        } catch (RuntimeException e) {
            // Some modded items throw out of getUnlocalizedName() for odd metadata.
            return stack.getItem().getClass().getSimpleName() + "@" + stack.getItemDamage();
        }
    }
}

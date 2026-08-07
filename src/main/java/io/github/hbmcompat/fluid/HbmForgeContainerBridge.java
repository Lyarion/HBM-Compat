package io.github.hbmcompat.fluid;

import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidContainerRegistry;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.FluidContainer;
import com.hbm.inventory.fluid.Fluids;

import io.github.hbmcompat.HbmCompat;

/**
 * Mirrors HBM's fluid-container registry into Forge's, so HBM's canisters, gas
 * tanks, fluid tanks, fluid barrels and cells can be filled and emptied by
 * anything that speaks Forge's fluid-container API — most importantly the AE2
 * ME Terminal.
 *
 * <p><b>Why this is needed.</b> AE2 (GTNH) routes every container fill/drain in
 * the terminal through {@code appeng.util.FluidUtils} and
 * {@code appeng.util.item.AEFluidStackType}, and both only ever ask two
 * questions: is the item a {@link net.minecraftforge.fluids.IFluidContainerItem},
 * or is it registered in Forge's {@link FluidContainerRegistry}? AE2FC's
 * {@code Util.FluidUtil} does the same. HBM's container items
 * ({@code ItemFluidTank}, {@code ItemCanister}, …) are plain {@code Item}s that
 * implement neither — HBM does fill/empty through its own static swap registry
 * instead. So the terminal simply does not see them as containers.
 *
 * <p><b>Why a straight mirror works.</b> HBM's {@link FluidContainer} already has
 * the exact shape of Forge's {@code FluidContainerData}: a full stack, an empty
 * stack, a fluid type and an amount in mB. Registering each HBM entry under the
 * matching Forge fluid (see {@link HbmForgeFluidRegistry}) gives both directions
 * at once — Forge's {@code fillFluidContainer} keys on (empty stack, fluid) and
 * {@code drainFluidContainer} keys on the full stack. Forge's keys compare item
 * and metadata only, and HBM encodes the fluid in the full item's metadata, so
 * every fluid gets its own distinct key.
 *
 * <p><b>Two entries are deliberately skipped.</b>
 * <ul>
 *   <li>Entries with no empty container — {@code ore_oil}, {@code ingot_mercury},
 *       {@code ore_gneiss_gas}. Forge's null-empty path substitutes
 *       {@code NULL_EMPTYCONTAINER}, which is a <i>bucket</i>, so draining an oil
 *       ore block would hand back a bucket. That is item duplication, so these
 *       one-way HBM entries stay out.</li>
 *   <li>Entries Forge already knows — the water and lava buckets and the water
 *       potion, which HBM re-declares. {@code registerFluidContainer} returns
 *       {@code false} for an already-registered full container, leaving the
 *       existing mapping untouched.</li>
 * </ul>
 *
 * <p>Must run after HBM has populated its own registry, which it does in
 * {@code MainRegistry.PostLoad} (FMLPostInitialization). Safe to call again: Forge
 * rejects duplicates, so a repeat call only picks up entries added since.
 */
public final class HbmForgeContainerBridge {

    private HbmForgeContainerBridge() {}

    /**
     * Registers every round-trippable HBM fluid container with Forge.
     *
     * <p>No-op when disabled via {@code -Dhbmcompat.noContainerBridge=true}.
     */
    public static synchronized void registerHbmContainersInForge() {
        if (HbmCompat.NO_CONTAINER_BRIDGE) {
            HbmCompat.LOG.info("HBM fluid-container bridge disabled by -Dhbmcompat.noContainerBridge");
            return;
        }

        // Two same-named classes are in play here: the imported FluidContainerRegistry
        // is Forge's, HBM's is fully qualified.
        List<FluidContainer> hbmContainers = com.hbm.inventory.FluidContainerRegistry.allContainers;
        if (hbmContainers == null || hbmContainers.isEmpty()) {
            HbmCompat.LOG.warn(
                    "HBM's fluid-container registry is empty; no containers bridged to Forge. "
                            + "Expected it to be populated by MainRegistry.PostLoad.");
            return;
        }

        int bridged = 0;
        int skippedOneWay = 0;
        int skippedUnmapped = 0;
        int skippedAlreadyKnown = 0;

        for (FluidContainer container : hbmContainers) {
            if (container == null || container.content <= 0) {
                continue;
            }
            if (container.fullContainer == null || container.fullContainer.getItem() == null) {
                continue;
            }
            // One-way entries would drain into a bucket under Forge's null-empty path.
            if (container.emptyContainer == null || container.emptyContainer.getItem() == null) {
                skippedOneWay++;
                continue;
            }
            if (container.type == null || container.type == Fluids.NONE) {
                skippedUnmapped++;
                continue;
            }

            Fluid forgeFluid = HbmForgeFluidRegistry.getForgeFluid(container.type);
            if (forgeFluid == null) {
                skippedUnmapped++;
                continue;
            }

            // Forge hands back copies of whatever is registered, so normalise the
            // stack sizes rather than trusting HBM's originals.
            ItemStack full = container.fullContainer.copy();
            full.stackSize = 1;
            ItemStack empty = container.emptyContainer.copy();
            empty.stackSize = 1;

            if (FluidContainerRegistry
                    .registerFluidContainer(new FluidStack(forgeFluid, container.content), full, empty)) {
                bridged++;
            } else {
                skippedAlreadyKnown++;
            }
        }

        HbmCompat.LOG.info(
                "Bridged {} HBM fluid containers to Forge ({} already known, {} one-way, {} unmapped fluid)",
                bridged,
                skippedAlreadyKnown,
                skippedOneWay,
                skippedUnmapped);
    }
}

package io.github.hbmcompat.fluid;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import io.github.hbmcompat.HbmCompat;

/**
 * Self-registers every HBM fluid as a Forge fluid.
 *
 * <p>This replaces the old runtime dependency on NTM Fluid Converters
 * ({@code com.justus0405.ntmfluidconverters.FluidConverter}). The registration
 * logic — Forge name = {@code hbmFluid.getName().toLowerCase(Locale.ROOT)}, a
 * {@link HbmBackedFluid} that borrows HBM's own {@code hbmfluid.<name>}
 * localization key, and an {@code isFluidRegistered} guard — is a deliberate
 * byte-for-byte match of ntm-fc so that:
 * <ul>
 *   <li>old saves that stored HBM fluids under the ntm-fc Forge names migrate
 *       seamlessly (same names resolve to the same fluids), and</li>
 *   <li>both mods can coexist: whichever registers a given fluid first wins, and
 *       the {@code FORGE_TO_HBM} reverse map is always populated regardless.</li>
 * </ul>
 *
 * <p>Pressurized HBM fluids are NOT special-cased here — the pressure boundary is
 * enforced at the transfer layer (Forge {@link net.minecraftforge.fluids.FluidStack}
 * has no pressure field), matching the existing compat behaviour.
 */
public final class HbmForgeFluidRegistry {

    /** A Forge fluid that delegates its display-name localization to HBM. */
    private static final class HbmBackedFluid extends Fluid {

        private final String hbmUnlocalizedName;

        private HbmBackedFluid(String fluidName, String hbmUnlocalizedName) {
            super(fluidName);
            this.hbmUnlocalizedName = hbmUnlocalizedName;
        }

        @Override
        public String getUnlocalizedName() {
            return hbmUnlocalizedName;
        }
    }

    /** Forge names of the fluids WE registered (for the texture stitch handler). */
    private static final Set<String> OUR_FLUIDS = new HashSet<String>();

    /** Reverse lookup: Forge fluid name -> HBM FluidType. Always fully populated. */
    private static final HashMap<String, FluidType> FORGE_TO_HBM = new HashMap<String, FluidType>();

    private static boolean registered;

    private HbmForgeFluidRegistry() {}

    /** Forge names of fluids this mod registered; unmodifiable. */
    public static Set<String> getOurFluids() {
        return Collections.unmodifiableSet(OUR_FLUIDS);
    }

    /** Forge name ntm-fc/we assign to an HBM fluid. Kept public for migration parity. */
    public static String forgeName(FluidType hbmFluid) {
        return hbmFluid.getName().toLowerCase(Locale.ROOT);
    }

    /**
     * HBM FluidType -> the mapped Forge fluid, or {@code null} for NONE/unknown.
     * Mirrors {@code FluidConverter.getForgeFluid}.
     */
    public static Fluid getForgeFluid(FluidType hbmFluid) {
        if (hbmFluid == null || hbmFluid == Fluids.NONE) {
            return null;
        }
        return FluidRegistry.getFluid(forgeName(hbmFluid));
    }

    /**
     * Forge fluid -> the mapped HBM FluidType, or {@link Fluids#NONE} if unmapped.
     * Mirrors {@code FluidConverter.getHbmFluid}.
     */
    public static FluidType getHbmFluid(Fluid forgeFluid) {
        if (forgeFluid == null) {
            return Fluids.NONE;
        }
        FluidType result = FORGE_TO_HBM.get(forgeFluid.getName());
        return result != null ? result : Fluids.NONE;
    }

    /**
     * Registers every HBM fluid (except NONE) as a Forge fluid, guarded so a
     * fluid already registered (e.g. by a coexisting ntm-fc) is left untouched.
     * The reverse map is populated unconditionally. Idempotent per JVM.
     *
     * <p>Call once during {@link cpw.mods.fml.common.event.FMLInitializationEvent}.
     */
    public static synchronized void registerHbmFluidsInForge() {
        if (registered) {
            return;
        }
        registered = true;

        int added = 0;
        for (FluidType hbmFluid : Fluids.getAll()) {
            if (hbmFluid == Fluids.NONE) {
                continue;
            }
            String forgeName = forgeName(hbmFluid);
            if (!FluidRegistry.isFluidRegistered(forgeName)) {
                // Reuse HBM's own "hbmfluid.<name>" localization key rather than
                // Forge's default "fluid.<name>", so display names stay in sync
                // with HBM across every language without shipping lang files.
                Fluid forgeFluid = new HbmBackedFluid(forgeName, hbmFluid.getUnlocalizedName());
                FluidRegistry.registerFluid(forgeFluid);
                OUR_FLUIDS.add(forgeName);
                added++;
            }
            FORGE_TO_HBM.put(forgeName, hbmFluid);
        }

        HbmCompat.LOG.info(
                "Registered {} HBM fluids as Forge fluids ({} total mapped)",
                added,
                FORGE_TO_HBM.size());
    }
}

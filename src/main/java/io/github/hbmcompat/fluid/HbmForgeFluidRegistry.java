package io.github.hbmcompat.fluid;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.CompatConfig;

/** Registers missing fluids and selects one preferred Forge identity per HBM type.
 * Bob mappings are resolved through its API, including custom names and suffixes.
 * Existing legacy names are accepted as input aliases without creating duplicates.
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

    private static final FluidMappingTable<FluidType, Fluid> MAPPINGS =
            new FluidMappingTable<FluidType, Fluid>();

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
        return MAPPINGS.output(hbmFluid);
    }

    /**
     * Forge fluid -> the mapped HBM FluidType, or {@link Fluids#NONE} if unmapped.
     * Mirrors {@code FluidConverter.getHbmFluid}.
     */
    public static FluidType getHbmFluid(Fluid forgeFluid) {
        if (forgeFluid == null) {
            return Fluids.NONE;
        }
        FluidType result = MAPPINGS.input(forgeFluid.getName());
        return result != null ? result : Fluids.NONE;
    }

    /** Called in init after Bob has completed preInit. */
    public static synchronized void registerHbmFluidsInForge() {
        if (registered) return;
        int added = 0;
        int reusedBob = 0;
        for (FluidType type : Fluids.getAll()) {
            if (type == Fluids.NONE) continue;
            Fluid fluid = CompatConfig.preferBob ? BobFluidBridge.getFluid(type) : null;
            if (fluid != null) {
                reusedBob++;
            } else {
                String name = forgeName(type);
                fluid = FluidRegistry.getFluid(name);
                if (fluid == null) {
                    Fluid candidate = new HbmBackedFluid(name, type.getUnlocalizedName());
                    if (FluidRegistry.registerFluid(candidate)) {
                        OUR_FLUIDS.add(name);
                        added++;
                    }
                    fluid = FluidRegistry.getFluid(name);
                }
            }
            MAPPINGS.prefer(type, fluid.getName(), fluid);
        }
        registered = true;
        registerExistingAliases();
        HbmCompat.LOG.info("HBM fluid mappings: mode={}, {} Bob mappings reused, {} fluids registered",
                CompatConfig.preferBob ? "auto" : "legacy", reusedBob, added);
        if (reusedBob > 0) {
            HbmCompat.LOG.warn("Auto mode uses Bob fluid names. Existing ME fluids/patterns are NOT migrated. "
                    + "Use fluids.fluidMapping=legacy before loading worlds that still require HBM-Compat names.");
        }
    }

    /** Run again in postInit to include names registered by other mods during init. */
    public static void registerExistingAliases() {
        for (FluidType type : Fluids.getAll()) {
            if (type == Fluids.NONE) continue;
            addAlias(type, FluidRegistry.getFluid(forgeName(type)));
            addAlias(type, BobFluidBridge.getFluid(type));
        }
    }

    private static void addAlias(FluidType type, Fluid fluid) {
        if (fluid != null && !MAPPINGS.alias(type, fluid.getName())) {
            HbmCompat.LOG.warn("Ignoring conflicting input alias '{}' for HBM fluid {}",
                    fluid.getName(), type.getName());
        }
    }
}

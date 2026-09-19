package io.github.hbmcompat.fluid;

import java.lang.reflect.Method;

import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.hbm.inventory.fluid.FluidType;

import cpw.mods.fml.common.Loader;

/** Optional dependency: no Bob classes are linked when Bob is absent. */
final class BobFluidBridge {
    private static boolean initialized;
    private static Method lookup;

    private BobFluidBridge() {}

    static Fluid getFluid(FluidType type) {
        if (!initialized) {
            if (Loader.isModLoaded("bobfluidtranslator")) {
                try {
                    lookup = Class.forName("com.ezzo.fluidtranslator.ModFluidRegistry")
                            .getMethod("getForgeFluid", FluidType.class);
                } catch (ReflectiveOperationException | LinkageError e) {
                    throw new IllegalStateException("Cannot read Bob fluid mappings; unsupported Bob version", e);
                }
            }
            initialized = true;
        }
        if (lookup == null) return null;
        try {
            Fluid fluid = (Fluid) lookup.invoke(null, type);
            // Use the actual registry object, never an unregistered wrapper.
            return fluid == null ? null : FluidRegistry.getFluid(fluid.getName());
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new IllegalStateException("Cannot read Bob mapping for " + type.getName(), e);
        }
    }
}

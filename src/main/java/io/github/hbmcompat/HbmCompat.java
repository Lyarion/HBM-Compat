package io.github.hbmcompat;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.github.hbmcompat.fluid.HbmForgeContainerBridge;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;
import io.github.hbmcompat.content.ModContent;
import io.github.hbmcompat.proxy.CommonProxy;

import cpw.mods.fml.common.Mod;
import cpw.mods.fml.common.SidedProxy;
import cpw.mods.fml.common.event.FMLInitializationEvent;
import cpw.mods.fml.common.event.FMLPostInitializationEvent;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;

@Mod(
        modid = HbmCompat.MODID,
        name = HbmCompat.NAME,
        version = Tags.VERSION,
        acceptedMinecraftVersions = "[1.7.10]",
        dependencies = "required-after:hbm;required-after:NotEnoughItems;"
                + "required-after:neenergistics@[1.7.38,);required-after:appliedenergistics2;"
                + "required-after:ae2fc@[1.5.99-gtnh,)")
public final class HbmCompat {

    public static final String MODID = "hbmcompat";
    public static final String NAME = "HBM Compat";
    public static final Logger LOG = LogManager.getLogger(MODID);

    // Per-gate fluid-bus diagnostics. Off by default (zero log spam in normal play).
    // Enable by adding JVM arg: -Dhbmcompat.debugBus=true
    public static final boolean DEBUG_BUS = Boolean.getBoolean("hbmcompat.debugBus");

    // Per-gate processing-adapter diagnostics: which check rejected a pattern push.
    // pushPattern() has a dozen silent early returns, so without this a stalled
    // craft gives the player nothing to go on. Off by default.
    // Enable by adding JVM arg: -Dhbmcompat.debugAdapter=true
    public static final boolean DEBUG_ADAPTER = Boolean.getBoolean("hbmcompat.debugAdapter");

    // Escape hatch for the HBM -> Forge fluid-container bridge. The bridge writes to
    // Forge's global container registry, so it changes how every other mod sees HBM's
    // canisters and tanks; this turns it off without removing the mod.
    // Disable by adding JVM arg: -Dhbmcompat.noContainerBridge=true
    public static final boolean NO_CONTAINER_BRIDGE = Boolean.getBoolean("hbmcompat.noContainerBridge");

    @SidedProxy(
            clientSide = "io.github.hbmcompat.proxy.ClientProxy",
            serverSide = "io.github.hbmcompat.proxy.CommonProxy")
    public static CommonProxy proxy;

    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        ModContent.preInit();
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Self-register HBM fluids as Forge fluids (drops the old ntm-fc runtime
        // dependency). Runs on both sides so servers register them too. Guarded so
        // a coexisting ntm-fc that registered first is respected — same names either
        // way, so old saves migrate seamlessly.
        HbmForgeFluidRegistry.registerHbmFluidsInForge();
        ModContent.init();
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Must run after HBM's own MainRegistry.PostLoad, which is what populates
        // com.hbm.inventory.FluidContainerRegistry. The "required-after:hbm"
        // dependency above guarantees that ordering.
        HbmForgeContainerBridge.registerHbmContainersInForge();
    }
}

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
        // The AE2 bound is not decoration: init() registers HbmPatternEncodeHandler, whose
        // verification resolves appeng.api.parts.IPatternTerminal$PatternEncodeListener. That
        // nested interface only exists from rv3-beta-1006 onwards (AE2 9220db1fb, "expose pattern
        // encoding apis"), and AE2FC's own bound is [rv3-beta-238,) - far too loose to catch it.
        // Without a bound here, an older AE2 loads this mod happily and then dies in init() with a
        // bare NoClassDefFoundError instead of FML reporting an unmet dependency. The floor is the
        // version this mod is compiled against (see dependencies.gradle), which is also well above
        // the stack-type APIs it uses (StorageName, IAEStackType, getAEInventoryByName).
        dependencies = "required-after:hbm;required-after:NotEnoughItems;"
                + "required-after:neenergistics@[1.7.38,);"
                + "required-after:appliedenergistics2@[rv3-beta-1024,);"
                + "required-after:ae2fc@[1.5.99-gtnh,);after:bobfluidtranslator")
public final class HbmCompat {

    @Mod.Instance("hbmcompat")
    public static HbmCompat instance;

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
        CompatConfig.load(event.getSuggestedConfigurationFile());
        io.github.hbmcompat.network.FactoryModeNetwork.init();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(new io.github.hbmcompat.debug.DebugChat());
        ModContent.preInit();
        cpw.mods.fml.common.network.NetworkRegistry.INSTANCE.registerGuiHandler(
                this, new io.github.hbmcompat.ae2.AdapterGuiHandler());
        proxy.preInit();
    }

    @Mod.EventHandler
    public void init(FMLInitializationEvent event) {
        // Bob registers in preInit; select its actual identities before installing consumers.
        HbmForgeFluidRegistry.registerHbmFluidsInForge();
        ModContent.init();
        proxy.init();
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        // Must run after HBM's own MainRegistry.PostLoad, which is what populates
        // com.hbm.inventory.FluidContainerRegistry. The "required-after:hbm"
        // dependency above guarantees that ordering. Optional after:bobfluidtranslator
        // also lets Bob establish its container mappings before we fill any gaps.
        HbmForgeFluidRegistry.registerExistingAliases();
        HbmForgeContainerBridge.registerHbmContainersInForge();
        proxy.postInit();
    }
    @Mod.EventHandler
    public void serverStarting(cpw.mods.fml.common.event.FMLServerStartingEvent event) {
        io.github.hbmcompat.debug.DebugChat.SUBSCRIPTIONS.clear();
        io.github.hbmcompat.machine.AdapterDiagnostics.clear();
        event.registerServerCommand(new io.github.hbmcompat.debug.HbmcCommand());
    }

    @Mod.EventHandler
    public void serverStopped(cpw.mods.fml.common.event.FMLServerStoppedEvent event) {
        io.github.hbmcompat.debug.DebugChat.SUBSCRIPTIONS.clear();
        io.github.hbmcompat.machine.AdapterDiagnostics.clear();
    }

}

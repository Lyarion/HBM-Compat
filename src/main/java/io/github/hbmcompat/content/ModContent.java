package io.github.hbmcompat.content;

import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.api.config.Upgrades;
import appeng.block.AEBaseItemBlock;
import cpw.mods.fml.common.registry.GameRegistry;
import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.ae2.TileHbmAdapter;

public final class ModContent {

    public static BlockHbmProcessingAdapter processingAdapter;
    public static ItemHbmFluidImportBus fluidImportBus;
    public static ItemHbmFluidExportBus fluidExportBus;
    public static ItemAutoExtractCard autoExtractCard;

    private ModContent() {}

    public static void preInit() {
        autoExtractCard = new ItemAutoExtractCard();
        autoExtractCard.setCreativeTab(CreativeTabs.tabRedstone);
        GameRegistry.registerItem(autoExtractCard, ItemAutoExtractCard.REGISTRY_NAME, HbmCompat.MODID);

        processingAdapter = new BlockHbmProcessingAdapter();
        processingAdapter.setCreativeTab(CreativeTabs.tabRedstone);
        GameRegistry.registerBlock(
                processingAdapter,
                AEBaseItemBlock.class,
                BlockHbmProcessingAdapter.REGISTRY_NAME);
        GameRegistry.registerTileEntity(
                TileHbmAdapter.class,
                HbmCompat.MODID + "." + BlockHbmProcessingAdapter.REGISTRY_NAME);

        fluidImportBus = new ItemHbmFluidImportBus();
        fluidImportBus.setCreativeTab(CreativeTabs.tabRedstone);
        GameRegistry.registerItem(fluidImportBus, ItemHbmFluidImportBus.REGISTRY_NAME, HbmCompat.MODID);

        fluidExportBus = new ItemHbmFluidExportBus();
        fluidExportBus.setCreativeTab(CreativeTabs.tabRedstone);
        GameRegistry.registerItem(fluidExportBus, ItemHbmFluidExportBus.REGISTRY_NAME, HbmCompat.MODID);
    }

    public static void init() {
        registerBusUpgrades(new ItemStack(fluidImportBus));
        registerBusUpgrades(new ItemStack(fluidExportBus));
        registerAdapterUpgrades(new ItemStack(processingAdapter));
        ModRecipes.init();

        // AE2's InterfaceTerminalRegistry only seeds its own TileInterface/PartInterface/PartP2PInterface, and the
        // grid indexes machines by their exact concrete class, so third-party interface hosts have to register
        // themselves to show up in the ME Interface Terminal.
        AEApi.instance().registries().interfaceTerminal().register(TileHbmAdapter.class);
    }

    private static void registerBusUpgrades(ItemStack bus) {
        Upgrades.CAPACITY.registerItem(bus, 2);
        Upgrades.REDSTONE.registerItem(bus, 1);
        Upgrades.SPEED.registerItem(bus, 4);
        Upgrades.SUPERSPEED.registerItem(bus, 4);
    }

    /**
     * The adapter runs AE2's stock interface GUI, which reveals one more row of nine pattern slots per installed
     * pattern capacity card, and DualityInterface already backs all four rows. Registering the card against the
     * adapter block is therefore the whole feature - without it the upgrade inventory rejects the card, because it
     * decides what it accepts by looking this block up in Upgrades.getSupported(). Three matches AE2's own limit for
     * the ME Interface and fills the 36-slot pattern inventory exactly.
     */
    private static void registerAdapterUpgrades(ItemStack adapter) {
        Upgrades.PATTERN_CAPACITY.registerItem(adapter, 3);
    }
}

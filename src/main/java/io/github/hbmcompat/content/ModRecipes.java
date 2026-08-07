package io.github.hbmcompat.content;

import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.ShapedOreRecipe;

import com.hbm.items.ModItems;
import com.hbm.items.machine.ItemCircuit.EnumCircuitType;

import appeng.api.AEApi;
import appeng.api.definitions.IMaterials;
import cpw.mods.fml.common.registry.GameRegistry;
import io.github.hbmcompat.HbmCompat;

/**
 * Vanilla crafting-table recipes for the three items this mod adds.
 *
 * <p>
 * Ingredients are pulled through the ore dictionary where a tag exists (dyeBlue, blockGlass) so any mod's equivalent
 * works, and through the AE2 API for the annihilation/formation cores so a config that disables them degrades to a
 * skipped recipe instead of a crash.
 */
public final class ModRecipes {

    private ModRecipes() {}

    public static void init() {
        final IMaterials materials = AEApi.instance().definitions().materials();
        final ItemStack annihilationCore = materials.annihilationCore().maybeStack(1).orNull();
        final ItemStack formationCore = materials.formationCore().maybeStack(1).orNull();

        // hbm:circuit is a multi-item whose meta is the EnumCircuitType ordinal.
        final ItemStack analogCircuit = new ItemStack(ModItems.circuit, 1, EnumCircuitType.ANALOG.ordinal());
        final ItemStack emptyFluidTank = new ItemStack(ModItems.fluid_tank_empty);
        final Item ironIngot = Items.iron_ingot;

        // dyeBlue  annihilationCore  dyeBlue
        // iron     emptyTank         iron
        addRecipe(
                new ItemStack(ModContent.fluidImportBus),
                "hbm_fluid_import_bus",
                "ABA",
                "CDC",
                'A',
                "dyeBlue",
                'B',
                annihilationCore,
                'C',
                ironIngot,
                'D',
                emptyFluidTank);

        // iron     emptyTank      iron
        // dyeBlue  formationCore  dyeBlue
        addRecipe(
                new ItemStack(ModContent.fluidExportBus),
                "hbm_fluid_export_bus",
                "CDC",
                "AEA",
                'A',
                "dyeBlue",
                'C',
                ironIngot,
                'D',
                emptyFluidTank,
                'E',
                formationCore);

        // iron              glass          iron
        // annihilationCore  analogCircuit  formationCore
        // iron              glass          iron
        addRecipe(
                new ItemStack(ModContent.processingAdapter),
                "me_hbm_processing_adapter",
                "CFC",
                "BGE",
                "CFC",
                'B',
                annihilationCore,
                'C',
                ironIngot,
                'E',
                formationCore,
                'F',
                "blockGlass",
                'G',
                analogCircuit);
    }

    /**
     * Registers a shaped ore recipe, skipping it if any ingredient resolved to null (an AE2 material disabled by
     * config) rather than letting the null reach the recipe list.
     */
    private static void addRecipe(ItemStack output, String name, Object... recipe) {
        for (Object ingredient : recipe) {
            if (ingredient == null) {
                HbmCompat.LOG.warn("Skipping the {} recipe: one of its ingredients is not available", name);
                return;
            }
        }

        GameRegistry.addRecipe(new ShapedOreRecipe(output, recipe));
    }
}

package io.github.hbmcompat.machine;

import io.github.hbmcompat.debug.DiagnosticMessage;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.tileentity.machine.TileEntityMachineChemicalPlant;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class ChemicalPlantDriver extends AbstractHbmMachineDriver {

    private static final int[] INPUT_SLOTS = { 4, 5, 6 };
    private static final int[] OUTPUT_SLOTS = { 7, 8, 9 };

    @Override
    public String getMachineId() {
        return "chemical_plant";
    }

    @Override
    public boolean supports(TileEntity tile) {
        return tile instanceof TileEntityMachineChemicalPlant;
    }

    @Override
    public HbmRecipeMatch match(ICraftingPatternDetails details) {
        PatternStacks inputs = PatternStacks.inputs(details);
        if (!inputs.isValid()) return null;
        GenericRecipe found = null;
        for (GenericRecipe recipe : ChemicalPlantRecipes.INSTANCE.recipeOrderedList) {
            if (PatternMatcher.matchesInputs(recipe, inputs)) {
                if (found != null) {
                    return null;
                }
                found = recipe;
            }
        }
        return found == null ? null : new HbmRecipeMatch(this, found, found.getInternalName());
    }

    @Override
    public boolean isBusy(TileEntity tile) {
        TileEntityMachineChemicalPlant machine = (TileEntityMachineChemicalPlant) tile;
        // Busy only while actively processing or with un-consumed inputs pending.
        // Output-slot / output-tank backlog must NOT block new input feeding.
        return pending(machine, machine.chemplantModule);
    }

    @Override
    public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs) {
        TileEntityMachineChemicalPlant machine = (TileEntityMachineChemicalPlant) tile;
        GenericRecipe recipe = (GenericRecipe) match.getRecipe();

        if (recipe.isPooled() && !recipe.isPartOfPool(ItemBlueprints.grabPool(machine.getStackInSlot(1)))) {
            AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                    "blueprint_missing", "recipe %s is blueprint-pooled and the blueprint in the machine's blueprint slot (slot 1) "
                    + "does not cover it. Insert the right blueprint.", recipe.getInternalName()));
            return false;
        }
        if (!isAutoSwitchStable(tile, recipe, ChemicalPlantRecipes.INSTANCE, suppliedInputs)) {
            return false;
        }
        if (!tanksCompatible(tile, machine.inputTanks, recipe.inputFluid)) {
            return false;
        }


        return commitModule(tile, machine.chemplantModule, recipe,
                planModule(tile, machine.chemplantModule, recipe, suppliedInputs));
    }

    @Override
    public int[] getOutputSlots(TileEntity tile) {
        return OUTPUT_SLOTS;
    }

    @Override
    public FluidTank[] getInputTanks(TileEntity tile) {
        return ((TileEntityMachineChemicalPlant) tile).inputTanks;
    }

    @Override
    public FluidTank[] getOutputTanks(TileEntity tile) {
        return ((TileEntityMachineChemicalPlant) tile).outputTanks;
    }
}

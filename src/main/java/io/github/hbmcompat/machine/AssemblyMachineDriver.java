package io.github.hbmcompat.machine;

import io.github.hbmcompat.debug.DiagnosticMessage;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.tileentity.machine.TileEntityMachineAssemblyMachine;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class AssemblyMachineDriver extends AbstractHbmMachineDriver {

    private static final int[] INPUT_SLOTS = { 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 };
    private static final int[] OUTPUT_SLOTS = { 16 };

    @Override
    public String getMachineId() {
        return "assembly_machine";
    }

    @Override
    public boolean supports(TileEntity tile) {
        return tile instanceof TileEntityMachineAssemblyMachine;
    }

    @Override
    public HbmRecipeMatch match(ICraftingPatternDetails details) {
        GenericRecipe found = PatternMatcher.selectRecipe(AssemblyMachineRecipes.INSTANCE.recipeOrderedList, details);
        return found == null ? null : new HbmRecipeMatch(this, found, found.getInternalName());
    }

    @Override
    public boolean isBusy(TileEntity tile) {
        TileEntityMachineAssemblyMachine machine = (TileEntityMachineAssemblyMachine) tile;
        // Busy only while actively processing or with un-consumed inputs pending.
        // Output-slot / output-tank backlog must NOT block new input feeding.
        return pending(machine, machine.assemblerModule);
    }

    @Override
    public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs) {
        TileEntityMachineAssemblyMachine machine = (TileEntityMachineAssemblyMachine) tile;
        GenericRecipe recipe = (GenericRecipe) match.getRecipe();
        FluidTank[] inputTanks = { machine.inputTank };

        if (recipe.isPooled() && !recipe.isPartOfPool(ItemBlueprints.grabPool(machine.getStackInSlot(1)))) {
            AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                    "blueprint_missing", "recipe %s is blueprint-pooled and the blueprint in the machine's blueprint slot (slot 1) "
                    + "does not cover it. Insert the right blueprint.", recipe.getInternalName()));
            return false;
        }
        if (!isAutoSwitchStable(tile, recipe, AssemblyMachineRecipes.INSTANCE, suppliedInputs)) {
            return false;
        }
        if (!tanksCompatible(tile, inputTanks, recipe.inputFluid)) {
            return false;
        }


        return commitModule(tile, machine.assemblerModule, recipe,
                planModule(tile, machine.assemblerModule, recipe, suppliedInputs));
    }

    @Override
    public int[] getOutputSlots(TileEntity tile) {
        return OUTPUT_SLOTS;
    }

    @Override
    public FluidTank[] getInputTanks(TileEntity tile) {
        return new FluidTank[] { ((TileEntityMachineAssemblyMachine) tile).inputTank };
    }

    @Override
    public FluidTank[] getOutputTanks(TileEntity tile) {
        return new FluidTank[] { ((TileEntityMachineAssemblyMachine) tile).outputTank };
    }
}

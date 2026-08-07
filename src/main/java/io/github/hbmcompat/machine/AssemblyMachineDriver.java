package io.github.hbmcompat.machine;

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
        PatternStacks inputs = PatternStacks.inputs(details);
        PatternStacks outputs = PatternStacks.outputs(details);
        GenericRecipe found = null;
        for (GenericRecipe recipe : AssemblyMachineRecipes.INSTANCE.recipeOrderedList) {
            if (PatternMatcher.matches(recipe, inputs, outputs)) {
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
        TileEntityMachineAssemblyMachine machine = (TileEntityMachineAssemblyMachine) tile;
        // Busy only while actively processing or with un-consumed inputs pending.
        // Output-slot / output-tank backlog must NOT block new input feeding.
        return machine.assemblerModule.progress > 0D || hasItems(machine, INPUT_SLOTS);
    }

    @Override
    public boolean hasContents(TileEntity tile) {
        TileEntityMachineAssemblyMachine machine = (TileEntityMachineAssemblyMachine) tile;
        // Fully-drained check for AE2 blocking mode: progress, any input/output
        // slot item, or any fluid in the input OR output tank.
        return machine.assemblerModule.progress > 0D
                || hasItems(machine, INPUT_SLOTS)
                || hasItems(machine, OUTPUT_SLOTS)
                || machine.inputTank.getFill() > 0
                || machine.outputTank.getFill() > 0;
    }

    @Override
    public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs) {
        TileEntityMachineAssemblyMachine machine = (TileEntityMachineAssemblyMachine) tile;
        GenericRecipe recipe = (GenericRecipe) match.getRecipe();
        FluidTank[] inputTanks = { machine.inputTank };

        if (recipe.isPooled() && !recipe.isPartOfPool(ItemBlueprints.grabPool(machine.getStackInSlot(1)))) {
            AdapterDiagnostics.report(
                    tile,
                    "recipe " + recipe.getInternalName()
                            + " is blueprint-pooled and the blueprint in the machine's blueprint slot (slot 1) does not"
                            + " cover it. Insert the right blueprint.");
            return false;
        }
        if (!isAutoSwitchStable(tile, recipe, AssemblyMachineRecipes.INSTANCE, suppliedInputs)) {
            return false;
        }
        if (!tanksCompatible(tile, inputTanks, recipe.inputFluid)) {
            return false;
        }

        // Everything that can fail on the item side is settled before the machine is touched.
        MachineInputPlan plan = planInputs(
                tile,
                machine,
                INPUT_SLOTS,
                recipe.inputItem,
                inputTanks,
                recipe.inputFluid,
                suppliedInputs);
        if (plan == null) {
            return false;
        }

        machine.assemblerModule.setRecipe(recipe.getInternalName(), false);
        machine.assemblerModule.setupTanks(recipe);
        return commitInputs(plan, tile, inputTanks, recipe.inputFluid);
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

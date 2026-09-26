package io.github.hbmcompat.machine;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.ArcWelderRecipes.ArcWelderRecipe;
import com.hbm.tileentity.machine.TileEntityMachineArcWelder;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class ArcWelderDriver extends AbstractHbmMachineDriver {

    private static final int[] INPUT_SLOTS = { 0, 1, 2 };
    private static final int[] OUTPUT_SLOTS = { 3 };

    @Override
    public String getMachineId() {
        return "arc_welder";
    }

    @Override
    public boolean supports(TileEntity tile) {
        return tile instanceof TileEntityMachineArcWelder;
    }

    @Override
    public HbmRecipeMatch match(ICraftingPatternDetails details) {
        PatternStacks inputs = PatternStacks.inputs(details);
        if (!inputs.isValid()) return null;
        ArcWelderRecipe found = null;
        for (ArcWelderRecipe recipe : ArcWelderRecipes.recipes) {
            if (PatternMatcher.matchesAStacks(recipe.ingredients, inputs.getItems())
                    && PatternMatcher.matchesSingleHbmFluid(recipe.fluid, inputs.getFluids())) {
                if (found != null) {
                    return null;
                }
                found = recipe;
            }
        }
        return found == null ? null : new HbmRecipeMatch(this, found, "auto");
    }

    @Override
    public boolean isBusy(TileEntity tile) {
        TileEntityMachineArcWelder machine = (TileEntityMachineArcWelder) tile;
        // Busy only while actively processing or with un-consumed inputs pending.
        // Output-slot backlog must NOT block new input feeding.
        return machine.progress > 0 || hasItems(machine, INPUT_SLOTS) || hasFluids(getInputTanks(tile));
    }

    @Override
    public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs) {
        TileEntityMachineArcWelder machine = (TileEntityMachineArcWelder) tile;
        ArcWelderRecipe recipe = (ArcWelderRecipe) match.getRecipe();
        com.hbm.inventory.FluidStack[] fluids = recipe.fluid == null ? null
                : new com.hbm.inventory.FluidStack[] { recipe.fluid };
        FluidTank[] inputTanks = { machine.tank };
        ArcWelderRecipe current = ArcWelderRecipes.getRecipe(snapshot(machine, null, 3));
        if ((current != null && current != recipe) || (machine.progress > 0 && current != recipe)
                || !tanksCompatible(tile, inputTanks, fluids)) return false;

        // This machine has no recipe-selection call, so there is no machine state to mutate before
        // the transfer: plan and commit back to back.
        MachineInputPlan plan = planInputs(
                tile,
                machine,
                INPUT_SLOTS,
                recipe.ingredients,
                inputTanks,
                fluids,
                suppliedInputs);
        if (plan == null) {
            return false;
        }
        return ArcWelderRecipes.getRecipe(snapshot(machine, plan, 3)) == recipe
                && commitInputs(plan, tile, inputTanks, fluids);
    }

    @Override
    public int[] getOutputSlots(TileEntity tile) {
        return OUTPUT_SLOTS;
    }

    @Override
    public FluidTank[] getInputTanks(TileEntity tile) {
        return new FluidTank[] { ((TileEntityMachineArcWelder) tile).tank };
    }

    @Override
    public FluidTank[] getOutputTanks(TileEntity tile) {
        return new FluidTank[0];
    }
}

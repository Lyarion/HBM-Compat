package io.github.hbmcompat.machine;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.SolderingRecipes.SolderingRecipe;
import com.hbm.tileentity.machine.TileEntityMachineSolderingStation;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class SolderingStationDriver extends AbstractHbmMachineDriver {

    private static final int[] BUSY_INPUT_SLOTS = { 0, 1, 2, 3, 4, 5 };
    private static final int[] OUTPUT_SLOTS = { 6 };

    @Override
    public String getMachineId() {
        return "soldering_station";
    }

    @Override
    public boolean supports(TileEntity tile) {
        return tile instanceof TileEntityMachineSolderingStation;
    }

    @Override
    public HbmRecipeMatch match(ICraftingPatternDetails details) {
        PatternStacks inputs = PatternStacks.inputs(details);
        if (!inputs.isValid()) return null;
        SolderingRecipe found = null;
        for (SolderingRecipe recipe : SolderingRecipes.recipes) {
            if (PatternMatcher.matchesAStacks(flatten(recipe), inputs.getItems())
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
        TileEntityMachineSolderingStation machine = (TileEntityMachineSolderingStation) tile;
        // Busy only while actively processing or with un-consumed inputs pending.
        // Output-slot backlog must NOT block new input feeding.
        return machine.progress > 0 || hasItems(machine, BUSY_INPUT_SLOTS);
    }

    @Override
    public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs) {
        TileEntityMachineSolderingStation machine = (TileEntityMachineSolderingStation) tile;
        SolderingRecipe recipe = (SolderingRecipe) match.getRecipe();
        com.hbm.inventory.FluidStack[] fluids = recipe.fluid == null ? null
                : new com.hbm.inventory.FluidStack[] { recipe.fluid };
        FluidTank[] inputTanks = { machine.tank };

        // This machine has no recipe-selection call, so there is no machine state to mutate before
        // the transfer: plan and commit back to back.
        MachineInputPlan plan = planInputs(
                tile,
                machine,
                routedSlots(recipe),
                flatten(recipe),
                inputTanks,
                fluids,
                suppliedInputs);
        if (plan == null) {
            return false;
        }
        return commitInputs(plan, tile, inputTanks, fluids);
    }

    @Override
    public int[] getOutputSlots(TileEntity tile) {
        return OUTPUT_SLOTS;
    }

    @Override
    public FluidTank[] getInputTanks(TileEntity tile) {
        return new FluidTank[] { ((TileEntityMachineSolderingStation) tile).tank };
    }

    @Override
    public FluidTank[] getOutputTanks(TileEntity tile) {
        return new FluidTank[0];
    }

    private AStack[] flatten(SolderingRecipe recipe) {
        int toppingCount = recipe.toppings == null ? 0 : recipe.toppings.length;
        int pcbCount = recipe.pcb == null ? 0 : recipe.pcb.length;
        int solderCount = recipe.solder == null ? 0 : recipe.solder.length;
        AStack[] result = new AStack[toppingCount + pcbCount + solderCount];
        int index = 0;
        if (recipe.toppings != null) {
            for (AStack stack : recipe.toppings) result[index++] = stack;
        }
        if (recipe.pcb != null) {
            for (AStack stack : recipe.pcb) result[index++] = stack;
        }
        if (recipe.solder != null) {
            for (AStack stack : recipe.solder) result[index++] = stack;
        }
        return result;
    }

    private int[] routedSlots(SolderingRecipe recipe) {
        int toppingCount = recipe.toppings == null ? 0 : recipe.toppings.length;
        int pcbCount = recipe.pcb == null ? 0 : recipe.pcb.length;
        int solderCount = recipe.solder == null ? 0 : recipe.solder.length;
        int[] result = new int[toppingCount + pcbCount + solderCount];
        int index = 0;
        for (int slot = 0; slot < toppingCount; slot++) result[index++] = slot;
        for (int slot = 0; slot < pcbCount; slot++) result[index++] = 3 + slot;
        for (int slot = 0; slot < solderCount; slot++) result[index++] = 5 + slot;
        return result;
    }
}

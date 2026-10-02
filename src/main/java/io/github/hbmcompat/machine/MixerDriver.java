package io.github.hbmcompat.machine;

import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.MixerRecipes.MixerRecipe;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.machine.TileEntityMachineMixer;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import io.github.hbmcompat.debug.DiagnosticMessage;

/** The mixer selects a recipe through both its output tank type and its recipe index. */
public final class MixerDriver extends AbstractHbmMachineDriver {

    private static final int[] INPUT_SLOTS = { 1 };
    private static final int[] NO_OUTPUT_SLOTS = new int[0];

    static final class Selection {
        final FluidType output;
        final int index;
        final MixerRecipe recipe;

        Selection(FluidType output, int index, MixerRecipe recipe) {
            this.output = output;
            this.index = index;
            this.recipe = recipe;
        }

        String name() { return output.getName() + "/" + index; }

        AStack[] items() {
            return recipe.solidInput == null ? null : new AStack[] { recipe.solidInput };
        }

        com.hbm.inventory.FluidStack[] fluids() {
            if (recipe.input1 == null) {
                return recipe.input2 == null ? null : new com.hbm.inventory.FluidStack[] { recipe.input2 };
            }
            return recipe.input2 == null ? new com.hbm.inventory.FluidStack[] { recipe.input1 }
                    : new com.hbm.inventory.FluidStack[] { recipe.input1, recipe.input2 };
        }

        com.hbm.inventory.FluidStack fluidForTank(int index) {
            return index == 0 ? recipe.input1 : recipe.input2;
        }
    }

    @Override public String getMachineId() { return "mixer"; }

    @Override public boolean supports(TileEntity tile) { return tile instanceof TileEntityMachineMixer; }

    @Override public HbmRecipeMatch match(ICraftingPatternDetails details) {
        return matchStacks(PatternStacks.inputs(details), PatternStacks.outputs(details));
    }

    HbmRecipeMatch matchStacks(PatternStacks inputs, PatternStacks outputs) {
        if (!inputs.isValid() || !outputs.isValid() || !outputs.getItems().isEmpty()) return null;
        Selection found = null;
        for (Map.Entry<FluidType, MixerRecipe[]> entry : MixerRecipes.recipes.entrySet()) {
            MixerRecipe[] recipes = entry.getValue();
            if (entry.getKey() == null || recipes == null) continue;
            for (int index = 0; index < recipes.length; index++) {
                MixerRecipe recipe = recipes[index];
                if (recipe == null || recipe.output <= 0) continue;
                Selection candidate = new Selection(entry.getKey(), index, recipe);
                if (!PatternMatcher.matchesAStacks(candidate.items(), inputs.getItems())
                        || !PatternMatcher.matchesHbmFluids(candidate.fluids(), inputs.getFluids())
                        || !PatternMatcher.matchesSingleHbmFluid(
                                new com.hbm.inventory.FluidStack(candidate.output, recipe.output),
                                outputs.getFluids())) continue;
                if (found != null) return null;
                found = candidate;
            }
        }
        return found == null ? null : new HbmRecipeMatch(this, found, found.name());
    }

    @Override public boolean isBusy(TileEntity tile) {
        TileEntityMachineMixer machine = (TileEntityMachineMixer) tile;
        return machine.progress > 0 || hasItems(machine, INPUT_SLOTS) || hasFluids(getInputTanks(tile));
    }

    @Override public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks inputs) {
        return push(tile, match, inputs, FactoryAllocationMode.PARALLEL_FIRST, FeedingMode.SINGLE_BATCH);
    }

    @Override public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks inputs,
            FactoryAllocationMode allocation, FeedingMode feeding) {
        if (!inputs.isValid() || match == null || !(match.getRecipe() instanceof Selection)) return false;
        TileEntityMachineMixer machine = (TileEntityMachineMixer) tile;
        Selection selected = (Selection) match.getRecipe();
        MixerRecipe[] liveRecipes = MixerRecipes.getOutput(selected.output);
        if (liveRecipes == null || selected.index >= liveRecipes.length
                || liveRecipes[selected.index] != selected.recipe) return false;

        boolean active = isBusy(tile);
        if (active && (feeding != FeedingMode.CONTINUOUS
                || machine.tanks[2].getTankType() != selected.output
                || machine.recipeIndex != selected.index)) {
            AdapterDiagnostics.report(tile, DiagnosticMessage.of("mixer_recipe_busy",
                    "mixer still has pending inputs or is processing another recipe; wait before switching"));
            return false;
        }

        ItemStack identifier = machine.getStackInSlot(2);
        if (identifier != null && identifier.getItem() instanceof IItemFluidIdentifier
                && ((IItemFluidIdentifier) identifier.getItem()).getType(null, 0, 0, 0, identifier)
                        != selected.output) {
            AdapterDiagnostics.report(tile, DiagnosticMessage.of("mixer_identifier",
                    "mixer fluid identifier selects a different output; remove or change it"));
            return false;
        }
        FluidTank output = machine.tanks[2];
        if (output == null || output.getPressure() != 0
                || (output.getFill() > 0 && output.getTankType() != selected.output)
                || (long) output.getFill() + selected.recipe.output > output.getMaxFill()) {
            AdapterDiagnostics.report(tile, DiagnosticMessage.of("mixer_output_blocked",
                    "mixer output tank contains another fluid, is pressurised, or lacks room for one result"));
            return false;
        }

        FluidTank[] tanks = getInputTanks(tile);
        for (int index = 0; index < tanks.length; index++) {
            FluidTank tank = tanks[index];
            com.hbm.inventory.FluidStack expected = selected.fluidForTank(index);
            if (tank == null || (tank.getFill() > 0 && (expected == null
                    || tank.getTankType() != expected.type || tank.getPressure() != expected.pressure))) {
                AdapterDiagnostics.report(tile, DiagnosticMessage.of("mixer_input_tank",
                        "mixer input tank %s contains fluid incompatible with the selected recipe", index));
                return false;
            }
        }

        MachineInputPlan plan = planInputs(tile, machine, INPUT_SLOTS, selected.items(), tanks,
                selected.fluids(), inputs);
        if (plan == null) return false;
        for (int index = 0; index < tanks.length; index++) {
            com.hbm.inventory.FluidStack expected = selected.fluidForTank(index);
            if (expected != null && !plan.addFluid(tanks[index], expected)) {
                AdapterDiagnostics.report(tile, DiagnosticMessage.of("tank_fill_failed",
                        "cannot fill input tank %s with [%s]: tank holds %s. Drain leftover fluid of another type, or the batch exceeds tank capacity.",
                        index, AdapterDiagnostics.describeHbmFluids(new com.hbm.inventory.FluidStack[] { expected }),
                        tanks[index].getFill() + "/" + tanks[index].getMaxFill()));
                return false;
            }
        }

        // Every failure gate is above this point. HBM's setTankType clears fluid on type change.
        output.setTankType(selected.output);
        machine.recipeIndex = selected.index;
        plan.commit();
        AdapterDiagnostics.reset(tile);
        return true;
    }

    @Override public FluidTank[] getInputTanks(TileEntity tile) {
        FluidTank[] tanks = ((TileEntityMachineMixer) tile).tanks;
        return new FluidTank[] { tanks[0], tanks[1] };
    }

    @Override public int[] getOutputSlots(TileEntity tile) { return NO_OUTPUT_SLOTS; }

    @Override public FluidTank[] getOutputTanks(TileEntity tile) {
        return new FluidTank[] { ((TileEntityMachineMixer) tile).tanks[2] };
    }
}

package io.github.hbmcompat.machine;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;
import com.hbm.items.machine.ItemBlueprints;
import com.hbm.module.machine.ModuleMachineBase;

import appeng.api.networking.crafting.ICraftingPatternDetails;

/** Shared four-lane scheduling for HBM's assembly and chemical factories. */
abstract class AbstractHbmFactoryDriver extends AbstractHbmMachineDriver {

    private static final int NO_LANE = -1;

    protected abstract GenericRecipes<? extends GenericRecipe> getRecipeSet();

    protected abstract ModuleMachineBase[] getModules(TileEntity tile);

    protected abstract int getBlueprintSlot(int lane);

    protected abstract String getFactoryName();

    @Override
    public final HbmRecipeMatch match(ICraftingPatternDetails details) {
        PatternStacks inputs = PatternStacks.inputs(details);
        if (!inputs.isValid()) return null;
        GenericRecipe found = null;
        for (GenericRecipe recipe : getRecipeSet().recipeOrderedList) {
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
    public final boolean isBusy(TileEntity tile) {
        IInventory inventory = (IInventory) tile;
        for (ModuleMachineBase module : getModules(tile)) {
            if (!laneHasPendingInput(inventory, module)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public final boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs) {
        return push(tile, match, suppliedInputs, FactoryAllocationMode.PARALLEL_FIRST);
    }

    @Override
    public final boolean isFactory() { return true; }

    @Override
    public final boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs,
            FactoryAllocationMode mode) {
        IInventory inventory = (IInventory) tile;
        GenericRecipe recipe = (GenericRecipe) match.getRecipe();

        if (mode == FactoryAllocationMode.VARIETY_FIRST) {
            for (ModuleMachineBase module : getModules(tile)) {
                if (module != null && recipe.getInternalName().equals(module.getRecipeName())
                        && laneHasPendingInput(inventory, module)) {
                    AdapterDiagnostics.report(tile, "variety-first: this recipe already occupies an active lane");
                    return false;
                }
            }
        }

        if (!isAutoSwitchStable(tile, recipe, getRecipeSet(), suppliedInputs)) {
            return false;
        }

        ModuleMachineBase[] modules = getModules(tile);
        String[] rejected = AdapterDiagnostics.enabled() ? new String[modules.length] : null;
        int selected = selectLane(tile, inventory, modules, recipe, rejected);
        if (selected == NO_LANE) {
            if (AdapterDiagnostics.enabled()) {
                AdapterDiagnostics.report(tile, describeRejections(recipe, rejected));
            }
            return false;
        }

        ModuleMachineBase module = modules[selected];
        MachineInputPlan plan = planInputs(
                tile,
                inventory,
                module.inputSlots,
                recipe.inputItem,
                module.inputTanks,
                recipe.inputFluid,
                suppliedInputs);
        if (plan == null) {
            return false;
        }

        // No machine mutation occurs before all four lanes have been inspected and the selected
        // lane's item/fluid input has been staged. The output-tank guard in selectLane is crucial:
        // setupTanks clears a tank when a recipe changes its type or no longer uses that tank.
        module.setRecipe(recipe.getInternalName(), false);
        module.setupTanks(recipe);
        return commitInputs(plan, tile, module.inputTanks, recipe.inputFluid);
    }

    @Override
    public final FluidTank[] getInputTanks(TileEntity tile) {
        return flattenTanks(getModules(tile), true);
    }

    @Override
    public final int[] getOutputSlots(TileEntity tile) {
        ModuleMachineBase[] modules = getModules(tile);
        int size = 0;
        for (ModuleMachineBase module : modules) {
            size += module.outputSlots == null ? 0 : module.outputSlots.length;
        }
        int[] result = new int[size];
        int at = 0;
        for (ModuleMachineBase module : modules) {
            if (module.outputSlots == null) {
                continue;
            }
            for (int slot : module.outputSlots) {
                result[at++] = slot;
            }
        }
        return result;
    }

    @Override
    public final FluidTank[] getOutputTanks(TileEntity tile) {
        return flattenTanks(getModules(tile), false);
    }

    private int selectLane(
            TileEntity tile,
            IInventory inventory,
            ModuleMachineBase[] modules,
            GenericRecipe recipe,
            String[] rejected) {
        int bestLane = NO_LANE;
        int bestPriority = Integer.MAX_VALUE;

        for (int lane = 0; lane < modules.length; lane++) {
            ModuleMachineBase module = modules[lane];
            if (laneHasPendingInput(inventory, module)) {
                reject(rejected, lane, "busy or holding unconsumed input");
                continue;
            }

            ItemStack blueprint = inventory.getStackInSlot(getBlueprintSlot(lane));
            if (recipe.isPooled() && !recipe.isPartOfPool(ItemBlueprints.grabPool(blueprint))) {
                reject(rejected, lane, "blueprint does not unlock this recipe");
                continue;
            }

            if (!tanksCompatible(tile, module.inputTanks, recipe.inputFluid, false)) {
                reject(rejected, lane, "input tanks are incompatible with this recipe");
                continue;
            }

            if (!tanksCanBeRetypedWithoutLoss(module.outputTanks, recipe.outputFluid)) {
                reject(rejected, lane, "switching recipe would clear a non-empty output tank");
                continue;
            }

            int priority = lanePriority(inventory, module, recipe);
            if (priority < bestPriority) {
                bestPriority = priority;
                bestLane = lane;
            }
        }
        return bestLane;
    }

    private int lanePriority(IInventory inventory, ModuleMachineBase module, GenericRecipe recipe) {
        int readiness = outputsCanAcceptResult(inventory, module, recipe) ? 0 : 3;
        if (recipe.getInternalName().equals(module.getRecipeName())) {
            return readiness; // Preserve recipe affinity when it will not reduce throughput.
        }
        if (!hasItems(inventory, module.outputSlots) && !hasFluids(module.outputTanks)) {
            return readiness + 1; // A completely drained lane is the safest retarget candidate.
        }
        return readiness + 2; // Residual output is safe, but may need extraction before processing.
    }

    /** Mirrors ModuleMachineBase.canFitOutput without consuming power or touching the lane. */
    private boolean outputsCanAcceptResult(
            IInventory inventory,
            ModuleMachineBase module,
            GenericRecipe recipe) {
        if (recipe.outputItem != null) {
            if (recipe.outputItem.length > module.outputSlots.length) {
                return false;
            }
            for (int index = 0; index < recipe.outputItem.length; index++) {
                ItemStack current = inventory.getStackInSlot(module.outputSlots[index]);
                if (current == null) {
                    continue;
                }
                IOutput output = recipe.outputItem[index];
                if (output == null || output.possibleMultiOutput()) {
                    return false;
                }
                ItemStack produced = output.getSingle();
                if (produced == null || current.getItem() != produced.getItem()
                        || current.getItemDamage() != produced.getItemDamage()
                        || current.stackSize + produced.stackSize > current.getMaxStackSize()) {
                    return false;
                }
            }
        }

        if (recipe.outputFluid != null) {
            if (recipe.outputFluid.length > module.outputTanks.length) {
                return false;
            }
            for (int index = 0; index < recipe.outputFluid.length; index++) {
                if (recipe.outputFluid[index].fill + module.outputTanks[index].getFill()
                        > module.outputTanks[index].getMaxFill()) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean laneHasPendingInput(IInventory inventory, ModuleMachineBase module) {
        return module == null || module.progress > 0D || hasItems(inventory, module.inputSlots)
                || hasFluids(module.inputTanks);
    }

    private FluidTank[] flattenTanks(ModuleMachineBase[] modules, boolean input) {
        int size = 0;
        for (ModuleMachineBase module : modules) {
            FluidTank[] tanks = input ? module.inputTanks : module.outputTanks;
            size += tanks == null ? 0 : tanks.length;
        }
        FluidTank[] result = new FluidTank[size];
        int at = 0;
        for (ModuleMachineBase module : modules) {
            FluidTank[] tanks = input ? module.inputTanks : module.outputTanks;
            if (tanks == null) {
                continue;
            }
            for (FluidTank tank : tanks) {
                result[at++] = tank;
            }
        }
        return result;
    }

    private void reject(String[] rejected, int lane, String reason) {
        if (rejected != null) {
            rejected[lane] = reason;
        }
    }

    private String describeRejections(GenericRecipe recipe, String[] rejected) {
        StringBuilder result = new StringBuilder();
        result.append("no lane in ").append(getFactoryName()).append(" can accept ")
                .append(recipe.getInternalName()).append(':');
        for (int lane = 0; lane < rejected.length; lane++) {
            result.append(" lane ").append(lane + 1).append(" = ")
                    .append(rejected[lane] == null ? "not usable" : rejected[lane]);
            if (lane + 1 < rejected.length) {
                result.append(';');
            }
        }
        return result.toString();
    }
}

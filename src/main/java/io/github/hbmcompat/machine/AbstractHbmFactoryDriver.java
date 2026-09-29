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

    protected abstract GenericRecipes<? extends GenericRecipe> getRecipeSet();

    protected abstract ModuleMachineBase[] getModules(TileEntity tile);

    protected abstract int getBlueprintSlot(int lane);

    protected abstract String getFactoryName();

    @Override
    public final HbmRecipeMatch match(ICraftingPatternDetails details) {
        GenericRecipe found = PatternMatcher.selectRecipe(getRecipeSet().recipeOrderedList, details);
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
        return push(tile, match, suppliedInputs, mode, FeedingMode.SINGLE_BATCH);
    }

    @Override
    public final boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs,
            FactoryAllocationMode mode, FeedingMode feeding) {
        IInventory inventory = (IInventory) tile;
        GenericRecipe recipe = (GenericRecipe) match.getRecipe();
        if (!isAutoSwitchStable(tile, recipe, getRecipeSet(), suppliedInputs)) return false;
        ModuleMachineBase[] modules = getModules(tile);
        boolean sameActive = false;
        for (ModuleMachineBase module : modules) {
            if (module != null && pending(inventory, module)
                    && recipe.getInternalName().equals(module.getRecipeName())) sameActive = true;
        }
        ModuleMachineBase selected = null;
        MachineInputPlan selectedPlan = null;
        long best = Long.MAX_VALUE;
        for (int lane = 0; lane < modules.length; lane++) {
            ModuleMachineBase module = modules[lane];
            if (module == null) continue;
            boolean active = pending(inventory, module);
            if (active && feeding == FeedingMode.SINGLE_BATCH) continue;
            if (mode == FactoryAllocationMode.VARIETY_FIRST && sameActive && !active) continue;
            if (recipe.isPooled() && !recipe.isPartOfPool(
                    ItemBlueprints.grabPool(inventory.getStackInSlot(getBlueprintSlot(lane))))) continue;
            MachineInputPlan plan = planModule(tile, module, recipe, suppliedInputs);
            if (plan == null) continue;
            long priority = active ? 10L + queuedBatches(inventory, module, recipe)
                    : lanePriority(inventory, module, recipe);
            if (priority < best) {
                best = priority;
                selected = module;
                selectedPlan = plan;
            }
        }
        return selected != null && commitModule(tile, selected, recipe, selectedPlan);
    }

    private int queuedBatches(IInventory inventory, ModuleMachineBase module, GenericRecipe recipe) {
        int batches = Integer.MAX_VALUE;
        if (recipe.inputItem != null) {
            for (int i = 0; i < recipe.inputItem.length; i++) {
                ItemStack stack = inventory.getStackInSlot(module.inputSlots[i]);
                batches = Math.min(batches, stack == null ? 0 : stack.stackSize / Math.max(1, recipe.inputItem[i].stacksize));
            }
        }
        if (recipe.inputFluid != null) {
            for (int i = 0; i < recipe.inputFluid.length; i++) {
                batches = Math.min(batches, module.inputTanks[i].getFill() / Math.max(1, recipe.inputFluid[i].fill));
            }
        }
        return batches == Integer.MAX_VALUE ? 0 : batches;
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
                        || (long) current.stackSize + produced.stackSize > current.getMaxStackSize()) {
                    return false;
                }
            }
        }

        if (recipe.outputFluid != null) {
            if (recipe.outputFluid.length > module.outputTanks.length) {
                return false;
            }
            for (int index = 0; index < recipe.outputFluid.length; index++) {
                if ((long) recipe.outputFluid[index].fill + module.outputTanks[index].getFill()
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

}

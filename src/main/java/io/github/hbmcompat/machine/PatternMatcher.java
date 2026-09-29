package io.github.hbmcompat.machine;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutputMulti;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;

import appeng.api.networking.crafting.ICraftingPatternDetails;

import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;

final class PatternMatcher {

    private PatternMatcher() {}

    static GenericRecipe selectRecipe(Iterable<? extends GenericRecipe> recipes, ICraftingPatternDetails details) {
        return selectRecipe(recipes, PatternStacks.inputs(details), () -> PatternStacks.outputs(details));
    }

    /** Preserve custom outputs for unique inputs; consult outputs only to resolve a collision. */
    static GenericRecipe selectRecipe(Iterable<? extends GenericRecipe> recipes, PatternStacks inputs,
            Supplier<PatternStacks> outputs) {
        List<GenericRecipe> candidates = new ArrayList<GenericRecipe>();
        for (GenericRecipe recipe : recipes) {
            if (matchesInputs(recipe, inputs)) candidates.add(recipe);
        }
        if (candidates.isEmpty()) return null;
        if (candidates.size() == 1) return candidates.get(0);

        PatternStacks expectedOutputs = outputs.get();
        GenericRecipe found = null;
        for (GenericRecipe recipe : candidates) {
            if (matchesOutputs(recipe, expectedOutputs)) {
                if (found != null) return null;
                found = recipe;
            }
        }
        return found;
    }

    static boolean matchesOutputs(GenericRecipe recipe, PatternStacks outputs) {
        if (!outputs.isValid()) return false;
        List<ItemStack> pool = copyItems(outputs.getItems());
        if (recipe.outputItem != null) {
            for (IOutput output : recipe.outputItem) {
                // Never roll random outputs while deciding which recipe to run.
                if (output instanceof ChanceOutputMulti) {
                    List<ChanceOutput> choices = ((ChanceOutputMulti) output).pool;
                    if (choices.size() != 1) return false;
                    output = choices.get(0);
                }
                if (output == null || output.possibleMultiOutput()
                        || (output instanceof ChanceOutput && !(((ChanceOutput) output).chance >= 1F))) {
                    return false;
                }
                ItemStack produced = output.getSingle();
                if (produced == null || produced.getItem() == null || produced.stackSize <= 0) return false;
                int remaining = produced.stackSize;
                for (ItemStack candidate : pool) {
                    if (candidate.isItemEqual(produced) && ItemStack.areItemStackTagsEqual(candidate, produced)) {
                        int consumed = Math.min(remaining, candidate.stackSize);
                        candidate.stackSize -= consumed;
                        remaining -= consumed;
                    }
                }
                if (remaining != 0) return false;
            }
        }
        return isEmpty(pool) && matchesHbmFluids(recipe.outputFluid, outputs.getFluids());
    }

    static boolean matchesInputs(GenericRecipe recipe, PatternStacks inputs) {
        if (recipe == null || !inputs.isValid()) {
            return false;
        }
        return matchesAStacks(recipe.inputItem, inputs.getItems())
                && matchesHbmFluids(recipe.inputFluid, inputs.getFluids());
    }

    static boolean matchesAStacks(AStack[] expected, List<ItemStack> actual) {
        List<ItemStack> pool = copyItems(actual);
        if (expected != null) {
            for (AStack ingredient : expected) {
                if (!consume(pool, ingredient)) {
                    return false;
                }
            }
        }
        return isEmpty(pool);
    }

    static boolean matchesHbmFluids(com.hbm.inventory.FluidStack[] expected, List<FluidStack> actual) {
        List<FluidStack> pool = copyFluids(actual);
        if (expected != null) {
            for (com.hbm.inventory.FluidStack stack : expected) {
                if (stack == null || stack.pressure != 0 || !consumeFluid(pool, stack)) {
                    return false;
                }
            }
        }
        return isFluidEmpty(pool);
    }

    static boolean matchesSingleHbmFluid(com.hbm.inventory.FluidStack expected, List<FluidStack> actual) {
        com.hbm.inventory.FluidStack[] expectedArray = expected == null ? null
                : new com.hbm.inventory.FluidStack[] { expected };
        return matchesHbmFluids(expectedArray, actual);
    }

    static List<ItemStack> assign(AStack[] expected, List<ItemStack> actual) {
        List<ItemStack> pool = copyItems(actual);
        List<ItemStack> result = new ArrayList<ItemStack>();
        if (expected != null) {
            for (AStack ingredient : expected) {
                ItemStack consumed = consumeAndReturn(pool, ingredient);
                if (consumed == null) {
                    return null;
                }
                result.add(consumed);
            }
        }
        return isEmpty(pool) ? result : null;
    }

    private static boolean consume(List<ItemStack> pool, AStack expected) {
        return consumeAndReturn(pool, expected) != null;
    }

    private static ItemStack consumeAndReturn(List<ItemStack> pool, AStack expected) {
        if (expected == null || expected.stacksize <= 0) {
            return null;
        }
        for (ItemStack candidate : pool) {
            if (candidate.stackSize >= expected.stacksize && expected.matchesRecipe(candidate, true)) {
                ItemStack consumed = candidate.copy();
                consumed.stackSize = expected.stacksize;
                candidate.stackSize -= expected.stacksize;
                return consumed;
            }
        }
        return null;
    }

    private static boolean consumeFluid(List<FluidStack> pool, com.hbm.inventory.FluidStack expected) {
        for (FluidStack candidate : pool) {
            if (candidate.amount >= expected.fill
                    && HbmForgeFluidRegistry.getHbmFluid(candidate.getFluid()) == expected.type) {
                candidate.amount -= expected.fill;
                return true;
            }
        }
        return false;
    }

    private static List<ItemStack> copyItems(List<ItemStack> source) {
        List<ItemStack> result = new ArrayList<ItemStack>(source.size());
        for (ItemStack stack : source) {
            result.add(stack.copy());
        }
        return result;
    }

    private static List<FluidStack> copyFluids(List<FluidStack> source) {
        List<FluidStack> result = new ArrayList<FluidStack>(source.size());
        for (FluidStack stack : source) {
            result.add(stack.copy());
        }
        return result;
    }

    private static boolean isEmpty(List<ItemStack> pool) {
        for (ItemStack stack : pool) {
            if (stack.stackSize > 0) {
                return false;
            }
        }
        return true;
    }

    private static boolean isFluidEmpty(List<FluidStack> pool) {
        for (FluidStack stack : pool) {
            if (stack.amount > 0) {
                return false;
            }
        }
        return true;
    }
}

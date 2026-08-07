package io.github.hbmcompat.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;

import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;

final class PatternMatcher {

    private PatternMatcher() {}

    static boolean matches(GenericRecipe recipe, PatternStacks inputs, PatternStacks outputs) {
        if (recipe == null || !inputs.isValid() || !outputs.isValid()) {
            return false;
        }
        return matchesAStacks(recipe.inputItem, inputs.getItems())
                && matchesHbmFluids(recipe.inputFluid, inputs.getFluids())
                && matchesOutputs(recipe.outputItem, outputs.getItems())
                && matchesHbmFluids(recipe.outputFluid, outputs.getFluids());
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

    static boolean matchesOutputs(IOutput[] expected, List<ItemStack> actual) {
        List<ItemStack> pool = copyItems(actual);
        if (expected != null) {
            for (IOutput output : expected) {
                if (output == null || output.possibleMultiOutput()) {
                    return false;
                }
                ItemStack single = output.getSingle();
                if (single == null || !consumeExact(pool, single)) {
                    return false;
                }
            }
        }
        return isEmpty(pool);
    }

    static boolean matchesExactItems(ItemStack[] expected, List<ItemStack> actual) {
        List<ItemStack> pool = copyItems(actual);
        if (expected != null) {
            for (ItemStack stack : expected) {
                if (stack == null || !consumeExact(pool, stack)) {
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

    private static boolean consumeExact(List<ItemStack> pool, ItemStack expected) {
        for (ItemStack candidate : pool) {
            if (candidate.stackSize >= expected.stackSize && candidate.isItemEqual(expected)
                    && ItemStack.areItemStackTagsEqual(candidate, expected)) {
                candidate.stackSize -= expected.stackSize;
                return true;
            }
        }
        return false;
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

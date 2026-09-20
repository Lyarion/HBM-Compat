package io.github.hbmcompat.machine;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;

import org.junit.Test;
import static org.junit.Assert.*;

public class PatternMatcherTest {
    private PatternStacks inputs(boolean valid, ItemStack... stacks) throws Exception {
        Constructor<PatternStacks> constructor = PatternStacks.class.getDeclaredConstructor(
                List.class, List.class, boolean.class);
        constructor.setAccessible(true);
        return constructor.newInstance(Arrays.asList(stacks), Collections.<FluidStack>emptyList(), valid);
    }

    @Test
    public void exactInputsMatchWithoutInspectingAnyOutputs() throws Exception {
        Item ingredient = new Item();
        GenericRecipe recipe = new GenericRecipe("test.byproducts")
                .inputItems(new ComparableStack(ingredient, 2));
        // Output content is deliberately unusable: input-only matching must not read it.
        recipe.outputItem = new IOutput[] { null };
        recipe.outputFluid = new com.hbm.inventory.FluidStack[] { null };
        ItemStack supplied = new ItemStack(ingredient, 2);
        assertTrue(PatternMatcher.matchesInputs(recipe, inputs(true, supplied)));
        assertEquals(2, supplied.stackSize);
    }

    @Test
    public void missingWrongAndExcessInputsAreRejected() throws Exception {
        Item ingredient = new Item();
        GenericRecipe recipe = new GenericRecipe("test.exact")
                .inputItems(new ComparableStack(ingredient, 2));
        assertFalse(PatternMatcher.matchesInputs(recipe, inputs(true)));
        assertFalse(PatternMatcher.matchesInputs(recipe, inputs(true, new ItemStack(ingredient, 1))));
        assertFalse(PatternMatcher.matchesInputs(recipe, inputs(true, new ItemStack(ingredient, 3))));
        assertFalse(PatternMatcher.matchesInputs(recipe, inputs(true, new ItemStack(new Item(), 2))));
        assertFalse(PatternMatcher.matchesInputs(recipe,
                inputs(true, new ItemStack(ingredient, 2), new ItemStack(new Item(), 1))));
    }

    @Test
    public void invalidInputDecodingCannotMatchEvenAnEmptyRecipe() throws Exception {
        assertFalse(PatternMatcher.matchesInputs(new GenericRecipe("test.invalid"), inputs(false)));
        assertFalse(PatternMatcher.matchesInputs(null, inputs(true)));
    }
}

package io.github.hbmcompat.machine;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraft.nbt.NBTTagCompound;

import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes.IOutput;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.render.util.EnumSymbol;
import com.hbm.inventory.recipes.loader.GenericRecipes.ChanceOutput;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;

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

    @Test
    public void uniqueInputDoesNotReadCustomOrInvalidOutputs() throws Exception {
        Item ingredient = new Item();
        GenericRecipe recipe = new GenericRecipe("test.unique").inputItems(new ComparableStack(ingredient));
        recipe.outputItem = new IOutput[] { null };
        assertSame(recipe, PatternMatcher.selectRecipe(Collections.singletonList(recipe),
                inputs(true, new ItemStack(ingredient)), () -> { throw new AssertionError("Outputs were read"); }));
        assertNull(PatternMatcher.selectRecipe(Collections.singletonList(recipe),
                inputs(false), () -> { throw new AssertionError("Outputs were read"); }));
    }

    @Test
    public void conflictingInputsUseOutputsRegardlessOfRecipeOrder() throws Exception {
        Item ingredient = new Item();
        Item product = new Item();
        GenericRecipe first = new GenericRecipe("test.first").inputItems(new ComparableStack(ingredient))
                .outputItems(new ItemStack(product, 16));
        GenericRecipe second = new GenericRecipe("test.second").inputItems(new ComparableStack(ingredient))
                .outputItems(new ItemStack(product, 8));
        PatternStacks in = inputs(true, new ItemStack(ingredient));
        PatternStacks out = inputs(true, new ItemStack(product, 8));
        assertSame(second, PatternMatcher.selectRecipe(Arrays.asList(first, second), in, () -> out));
        assertSame(second, PatternMatcher.selectRecipe(Arrays.asList(second, first), in, () -> out));
        assertEquals(8, out.getItems().get(0).stackSize);
        PatternStacks wrong = inputs(true, new ItemStack(product, 7));
        assertNull(PatternMatcher.selectRecipe(Arrays.asList(first, second), in, () -> wrong));
        PatternStacks invalid = inputs(false);
        assertNull(PatternMatcher.selectRecipe(Arrays.asList(first, second), in, () -> invalid));
        assertNull(PatternMatcher.selectRecipe(Arrays.asList(first, second, second), in, () -> out));
        GenericRecipe wrongInput = new GenericRecipe("test.wrong_input")
                .inputItems(new ComparableStack(new Item())).outputItems(new ItemStack(product, 8));
        assertSame(second, PatternMatcher.selectRecipe(Arrays.asList(first, second, wrongInput), in, () -> out));
    }

    @Test
    public void outputComparisonIncludesTagsAndSupportsSplitStacks() throws Exception {
        Item product = new Item();
        ItemStack expected = new ItemStack(product, 16);
        expected.setTagCompound(new NBTTagCompound());
        expected.getTagCompound().setString("variant", "concrete");
        GenericRecipe recipe = new GenericRecipe("test.tagged").outputItems(expected);
        assertFalse(PatternMatcher.matchesOutputs(recipe, inputs(true, new ItemStack(product, 16))));
        ItemStack half = expected.copy();
        half.stackSize = 8;
        assertTrue(PatternMatcher.matchesOutputs(recipe, inputs(true, half, half.copy())));
        assertEquals(8, half.stackSize);
        assertFalse(PatternMatcher.matchesOutputs(recipe, inputs(true, expected, new ItemStack(new Item()))));
    }

    @Test
    public void randomOutputsCannotResolveAnAmbiguity() throws Exception {
        Item product = new Item();
        GenericRecipe recipe = new GenericRecipe("test.random");
        recipe.outputItem = new IOutput[] { new ChanceOutput(new ItemStack(product), 0.5F) };
        assertFalse(PatternMatcher.matchesOutputs(recipe, inputs(true, new ItemStack(product))));
    }

    private static final FluidType CONCRETE = new FluidType("test_matcher_concrete", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final Fluid FORGE_CONCRETE = new Fluid("test_matcher_concrete");

    @Test
    @SuppressWarnings("unchecked")
    public void concreteSolidAndLiquidRecipesAreDistinguished() throws Exception {
        Field tableField = HbmForgeFluidRegistry.class.getDeclaredField("MAPPINGS");
        tableField.setAccessible(true);
        Object table = tableField.get(null);
        Field canonical = table.getClass().getDeclaredField("canonical");
        canonical.setAccessible(true);
        Map<String, FluidType> mappings = (Map<String, FluidType>) canonical.get(table);
        FluidType previous = mappings.put(FORGE_CONCRETE.getName(), CONCRETE);
        try {
            Item cement = new Item();
            Item concrete = new Item();
            GenericRecipe solid = new GenericRecipe("chem.concrete")
                    .inputItems(new ComparableStack(cement)).outputItems(new ItemStack(concrete, 16));
            GenericRecipe liquid = new GenericRecipe("chem.liquidconk")
                    .inputItems(new ComparableStack(cement))
                    .outputFluids(new com.hbm.inventory.FluidStack(CONCRETE, 16000));
            List<GenericRecipe> recipes = Arrays.asList(solid, liquid);
            PatternStacks in = inputs(true, new ItemStack(cement));
            PatternStacks solidOut = inputs(true, new ItemStack(concrete, 16));
            Constructor<PatternStacks> ctor = PatternStacks.class.getDeclaredConstructor(
                    List.class, List.class, boolean.class);
            ctor.setAccessible(true);
            PatternStacks liquidOut = ctor.newInstance(Collections.emptyList(),
                    Collections.singletonList(TestFluidStack.create(16000)), true);
            assertSame(solid, PatternMatcher.selectRecipe(recipes, in, () -> solidOut));
            assertSame(liquid, PatternMatcher.selectRecipe(recipes, in, () -> liquidOut));
            assertEquals(16000, liquidOut.getFluids().get(0).amount);
            PatternStacks wrongAmount = ctor.newInstance(Collections.emptyList(),
                    Collections.singletonList(TestFluidStack.create(1000)), true);
            assertNull(PatternMatcher.selectRecipe(recipes, in, () -> wrongAmount));
        } finally {
            if (previous == null) mappings.remove(FORGE_CONCRETE.getName());
            else mappings.put(FORGE_CONCRETE.getName(), previous);
        }
    }

    /** Minimal fluid value fixture: Forge's fluid registry requires a running game. */
    private static final class TestFluidStack extends FluidStack {
        private TestFluidStack() { super(FORGE_CONCRETE, 1); }
        static TestFluidStack create(int amount) {
            try {
                Constructor<?> ctor = sun.reflect.ReflectionFactory.getReflectionFactory()
                        .newConstructorForSerialization(TestFluidStack.class, Object.class.getDeclaredConstructor());
                TestFluidStack stack = (TestFluidStack) ctor.newInstance();
                Field delegate = FluidStack.class.getDeclaredField("fluidDelegate");
                delegate.setAccessible(true);
                delegate.set(stack, new cpw.mods.fml.common.registry.RegistryDelegate<Fluid>() {
                    @Override public Fluid get() { return FORGE_CONCRETE; }
                    @Override public String name() { return FORGE_CONCRETE.getName(); }
                    @Override public Class<Fluid> type() { return Fluid.class; }
                });
                stack.amount = amount;
                return stack;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        @Override public FluidStack copy() { return create(amount); }
    }

}

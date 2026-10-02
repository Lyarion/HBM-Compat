package io.github.hbmcompat.machine;

import static org.junit.Assert.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.recipes.MixerRecipes;
import com.hbm.inventory.recipes.MixerRecipes.MixerRecipe;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.render.util.EnumSymbol;
import com.hbm.tileentity.machine.TileEntityMachineMixer;

import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class MixerDriverTest {
    private static final FluidType A = new FluidType("test_mixer_a", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final FluidType B = new FluidType("test_mixer_b", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final FluidType OUT = new FluidType("test_mixer_out", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final FluidType OTHER = new FluidType("test_mixer_other", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final Fluid FORGE_A = new Fluid("test_mixer_a");
    private static final Fluid FORGE_B = new Fluid("test_mixer_b");
    private static final Fluid FORGE_OUT = new Fluid("test_mixer_out");
    private static final Fluid FORGE_OTHER = new Fluid("test_mixer_other");

    private final Item solid = new Item();
    private final MixerDriver driver = new MixerDriver();
    private final Machine machine = new Machine();
    private Map<FluidType, MixerRecipe[]> originals;
    private Map<String, FluidType> mappings;
    private Map<String, FluidType> previousMappings;
    private MixerRecipe first;
    private MixerRecipe second;

    private static final class Machine extends TileEntityMachineMixer {
        int dirty;
        @Override public void markDirty() { dirty++; }
    }

    private static final class Identifier extends Item implements IItemFluidIdentifier {
        private final FluidType type;
        Identifier(FluidType type) { this.type = type; }
        @Override public FluidType getType(World world, int x, int y, int z, ItemStack stack) { return type; }
    }

    /** Forge's registry is not bootstrapped in unit tests; create a usable value without registering it. */
    private static final class TestFluidStack extends FluidStack {
        private TestFluidStack() { super(FORGE_A, 1); }
        static TestFluidStack of(Fluid fluid, int amount) {
            try {
                Constructor<?> ctor = sun.reflect.ReflectionFactory.getReflectionFactory()
                        .newConstructorForSerialization(TestFluidStack.class, Object.class.getDeclaredConstructor());
                TestFluidStack stack = (TestFluidStack) ctor.newInstance();
                Field delegate = FluidStack.class.getDeclaredField("fluidDelegate");
                delegate.setAccessible(true);
                delegate.set(stack, new cpw.mods.fml.common.registry.RegistryDelegate<Fluid>() {
                    @Override public Fluid get() { return fluid; }
                    @Override public String name() { return fluid.getName(); }
                    @Override public Class<Fluid> type() { return Fluid.class; }
                });
                stack.amount = amount;
                return stack;
            } catch (ReflectiveOperationException e) {
                throw new AssertionError(e);
            }
        }
        @Override public FluidStack copy() { return of(getFluid(), amount); }
    }

    @Before @SuppressWarnings("unchecked") public void setUp() throws Exception {
        originals = new HashMap<FluidType, MixerRecipe[]>(MixerRecipes.recipes);
        MixerRecipes.recipes.clear();
        Field field = HbmForgeFluidRegistry.class.getDeclaredField("MAPPINGS");
        field.setAccessible(true);
        Object table = field.get(null);
        Field canonical = table.getClass().getDeclaredField("canonical");
        canonical.setAccessible(true);
        mappings = (Map<String, FluidType>) canonical.get(table);
        previousMappings = new HashMap<String, FluidType>();
        map(FORGE_A, A);
        map(FORGE_B, B);
        map(FORGE_OUT, OUT);
        map(FORGE_OTHER, OTHER);
        first = recipe(1000, 500, 250, 2);
        second = recipe(800, 500, 0, 0);
        MixerRecipes.register(OUT, first, second);
    }

    @After public void tearDown() {
        MixerRecipes.recipes.clear();
        MixerRecipes.recipes.putAll(originals);
        for (Map.Entry<String, FluidType> entry : previousMappings.entrySet()) {
            if (entry.getValue() == null) mappings.remove(entry.getKey());
            else mappings.put(entry.getKey(), entry.getValue());
        }
    }

    private void map(Fluid fluid, FluidType type) {
        previousMappings.put(fluid.getName(), mappings.put(fluid.getName(), type));
    }

    private MixerRecipe recipe(int output, int input1, int input2, int solidCount) throws Exception {
        Constructor<MixerRecipe> ctor = MixerRecipe.class.getDeclaredConstructor(int.class, int.class);
        ctor.setAccessible(true);
        MixerRecipe recipe = ctor.newInstance(output, 20);
        if (input1 > 0) recipe.input1 = new com.hbm.inventory.FluidStack(A, input1);
        if (input2 > 0) recipe.input2 = new com.hbm.inventory.FluidStack(B, input2);
        if (solidCount > 0) recipe.solidInput = new ComparableStack(solid, solidCount);
        return recipe;
    }

    private PatternStacks stacks(List<ItemStack> items, FluidStack... fluids) throws Exception {
        Constructor<PatternStacks> ctor = PatternStacks.class.getDeclaredConstructor(List.class, List.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(items, Arrays.asList(fluids), true);
    }

    private PatternStacks firstInputs() throws Exception {
        return stacks(Collections.singletonList(new ItemStack(solid, 2)),
                TestFluidStack.of(FORGE_A, 500), TestFluidStack.of(FORGE_B, 250));
    }

    private PatternStacks secondInputs() throws Exception {
        return stacks(Collections.<ItemStack>emptyList(), TestFluidStack.of(FORGE_A, 500));
    }

    private PatternStacks output(Fluid fluid, int amount) throws Exception {
        return stacks(Collections.<ItemStack>emptyList(), TestFluidStack.of(fluid, amount));
    }

    private HbmRecipeMatch firstMatch() throws Exception {
        return driver.matchStacks(firstInputs(), output(FORGE_OUT, 1000));
    }

    private boolean push(HbmRecipeMatch match, PatternStacks inputs, FeedingMode feeding) {
        return driver.push(machine, match, inputs, FactoryAllocationMode.PARALLEL_FIRST, feeding);
    }

    @Test public void requiresExactInputsAndFluidOutput() throws Exception {
        HbmRecipeMatch match = firstMatch();
        assertNotNull(match);
        assertEquals("mixer", match.getDriver().getMachineId());
        assertEquals(OUT.getName() + "/0", match.getRecipeName());
        assertNull(driver.matchStacks(firstInputs(), output(FORGE_OUT, 999)));
        assertNull(driver.matchStacks(firstInputs(), output(FORGE_OTHER, 1000)));
        assertNull(driver.matchStacks(stacks(Collections.singletonList(new ItemStack(solid, 1)),
                TestFluidStack.of(FORGE_A, 500), TestFluidStack.of(FORGE_B, 250)), output(FORGE_OUT, 1000)));
        assertNull(driver.matchStacks(stacks(Collections.singletonList(new ItemStack(solid, 2)),
                TestFluidStack.of(FORGE_A, 501), TestFluidStack.of(FORGE_B, 250)), output(FORGE_OUT, 1000)));
        MixerRecipes.register(OUT, first, first);
        assertNull(firstMatch());
    }

    @Test public void selectsOutputAndIndexBeforePuttingInputsIntoPhysicalTanks() throws Exception {
        PatternStacks inputs = firstInputs();
        assertTrue(push(firstMatch(), inputs, FeedingMode.SINGLE_BATCH));
        assertSame(OUT, machine.tanks[2].getTankType());
        assertEquals(0, machine.recipeIndex);
        assertEquals(500, machine.tanks[0].getFill());
        assertEquals(250, machine.tanks[1].getFill());
        assertEquals(2, machine.getStackInSlot(1).stackSize);
        assertEquals(2, inputs.getItems().get(0).stackSize);
        assertEquals(500, inputs.getFluids().get(0).amount);
        assertTrue(machine.dirty > 0);
        assertTrue(HbmMachineDrivers.forTile(machine) instanceof MixerDriver);
        assertSame(machine.tanks[2], driver.getOutputTanks(machine)[0]);
        assertEquals(0, driver.getOutputSlots(machine).length);
    }

    @Test public void preservesSecondTankPositionWhenFirstFluidIsAbsent() throws Exception {
        MixerRecipe sparse = recipe(500, 0, 250, 1);
        MixerRecipes.register(OTHER, sparse);
        PatternStacks inputs = stacks(Collections.singletonList(new ItemStack(solid)), TestFluidStack.of(FORGE_B, 250));
        HbmRecipeMatch match = driver.matchStacks(inputs, output(FORGE_OTHER, 500));
        assertNotNull(match);
        assertTrue(push(match, inputs, FeedingMode.SINGLE_BATCH));
        assertSame(Fluids.NONE, machine.tanks[0].getTankType());
        assertEquals(0, machine.tanks[0].getFill());
        assertSame(B, machine.tanks[1].getTankType());
        assertEquals(250, machine.tanks[1].getFill());
    }

    @Test public void neverClearsOutputFluidOrOverridesIdentifier() throws Exception {
        HbmRecipeMatch match = firstMatch();
        machine.tanks[2].setTankType(OTHER);
        machine.tanks[2].setFill(200);
        assertFalse(push(match, firstInputs(), FeedingMode.SINGLE_BATCH));
        assertSame(OTHER, machine.tanks[2].getTankType());
        assertEquals(200, machine.tanks[2].getFill());
        assertEquals(0, machine.dirty);
        machine.tanks[2].setFill(0);
        machine.setInventorySlotContents(2, new ItemStack(new Identifier(OTHER)));
        machine.dirty = 0;
        assertFalse(push(match, firstInputs(), FeedingMode.SINGLE_BATCH));
        assertSame(OTHER, machine.tanks[2].getTankType());
        assertEquals(0, machine.dirty);
        machine.setInventorySlotContents(2, new ItemStack(new Identifier(OUT)));
        assertTrue(push(match, firstInputs(), FeedingMode.SINGLE_BATCH));
        assertSame(OUT, machine.tanks[2].getTankType());
    }

    @Test public void preservesSameTypeOutputAndExposesItToRecoveryAndBuses() throws Exception {
        machine.tanks[2].setTankType(OUT);
        machine.tanks[2].setFill(1200);
        assertTrue(push(firstMatch(), firstInputs(), FeedingMode.SINGLE_BATCH));
        assertEquals(1200, machine.tanks[2].getFill());
        assertSame(machine.tanks[2], HbmFluidAccess.sourceTanks(machine)[0]);
        assertEquals(2, HbmFluidAccess.sinkTanks(machine).length);
        assertTrue(OutputRecovery.recover(Collections.<net.minecraft.tileentity.TileEntity>singletonList(machine),
                HbmMachineDrivers::forTile, type -> type == OUT ? FORGE_OUT : null,
                new OutputRecovery.Sink() {
                    @Override public int insertItem(ItemStack offered) { return 0; }
                    @Override public int insertFluid(Fluid fluid, int offered) { return offered; }
                }));
        assertEquals(0, machine.tanks[2].getFill());
    }

    @Test public void continuousFeedingKeepsRecipeAndRejectsSwitches() throws Exception {
        PatternStacks inputs = secondInputs();
        HbmRecipeMatch match = driver.matchStacks(inputs, output(FORGE_OUT, 800));
        assertNotNull(match);
        assertTrue(push(match, inputs, FeedingMode.CONTINUOUS));
        assertEquals(1, machine.recipeIndex);
        machine.progress = 1;
        assertTrue(push(match, inputs, FeedingMode.CONTINUOUS));
        assertEquals(1000, machine.tanks[0].getFill());
        assertEquals(1, machine.progress);
        assertFalse(push(match, inputs, FeedingMode.SINGLE_BATCH));
        assertFalse(push(firstMatch(), firstInputs(), FeedingMode.CONTINUOUS));
        assertEquals(1, machine.recipeIndex);
        assertEquals(1000, machine.tanks[0].getFill());
        assertNull(machine.getStackInSlot(1));
    }

    @Test public void rejectsFullOutputAndInputTanksWithoutPartialChanges() throws Exception {
        HbmRecipeMatch match = firstMatch();
        machine.tanks[2].setTankType(OUT);
        machine.tanks[2].setFill(machine.tanks[2].getMaxFill() - 999);
        assertFalse(push(match, firstInputs(), FeedingMode.SINGLE_BATCH));
        assertEquals(0, machine.tanks[0].getFill());
        assertNull(machine.getStackInSlot(1));
        machine.tanks[2].setFill(0);
        machine.tanks[1].setTankType(OTHER);
        machine.tanks[1].setFill(1);
        assertFalse(push(match, firstInputs(), FeedingMode.SINGLE_BATCH));
        assertSame(OTHER, machine.tanks[1].getTankType());
        assertEquals(1, machine.tanks[1].getFill());
        assertEquals(0, machine.tanks[0].getFill());
        machine.tanks[1].setFill(0);
        machine.tanks[0].setTankType(A);
        machine.tanks[0].setFill(machine.tanks[0].getMaxFill() - 499);
        assertFalse(push(match, firstInputs(), FeedingMode.CONTINUOUS));
        assertEquals(machine.tanks[0].getMaxFill() - 499, machine.tanks[0].getFill());
        assertEquals(0, machine.tanks[1].getFill());
        assertNull(machine.getStackInSlot(1));
    }
}

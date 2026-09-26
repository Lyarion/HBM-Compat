package io.github.hbmcompat.machine;

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
import net.minecraft.util.RegistrySimple;
import net.minecraft.util.RegistryNamespaced;
import net.minecraft.util.ObjectIntIdentityMap;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes.CrystallizerRecipe;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.render.util.EnumSymbol;
import com.hbm.tileentity.machine.TileEntityMachineCrystallizer;

import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class CrystallizerDriverTest {
    private static final FluidType ACID = new FluidType("test_acidizer_acid", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final FluidType OTHER = new FluidType("test_acidizer_other", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final Fluid FORGE_ACID = new Fluid("test_acidizer_acid");
    private final CrystallizerDriver driver = new CrystallizerDriver();
    private static final Item ingredient = new Item();
    private static final Item product = new Item();
    // Populate the vanilla backing registry without bootstrapping Forge's LaunchClassLoader.
    @SuppressWarnings("unchecked")
    private static void registerTestItems() throws Exception {
        Field namesField = RegistrySimple.class.getDeclaredField("registryObjects");
        namesField.setAccessible(true);
        Map<String, Object> names = (Map<String, Object>) namesField.get(Item.itemRegistry);
        names.put("hbmcompat:test_acidizer_input", ingredient);
        names.put("hbmcompat:test_acidizer_output", product);
        Field idsField = RegistryNamespaced.class.getDeclaredField("underlyingIntegerMap");
        idsField.setAccessible(true);
        ObjectIntIdentityMap ids = (ObjectIntIdentityMap) idsField.get(Item.itemRegistry);
        ids.func_148746_a(ingredient, 31000);
        ids.func_148746_a(product, 31001);
    }
    private final Machine machine = new Machine();
    private Map<Object, Object> recipes, originalRecipes, amounts, originalAmounts;
    private Map<String, FluidType> mappings;
    private FluidType previousMapping;

    private static final class Machine extends TileEntityMachineCrystallizer {
        int dirty;
        @Override public void markDirty() { dirty++; }
    }

    private static final class Identifier extends Item implements IItemFluidIdentifier {
        private final FluidType type;
        Identifier(FluidType type) { this.type = type; }
        @Override public FluidType getType(World world, int x, int y, int z, ItemStack stack) { return type; }
    }

    @Before @SuppressWarnings("unchecked") public void setup() throws Exception {
        registerTestItems();
        recipes = (Map<Object, Object>) new CrystallizerRecipes().getRecipeObject();
        originalRecipes = new HashMap<Object, Object>(recipes);
        Field amountField = CrystallizerRecipes.class.getDeclaredField("amounts");
        amountField.setAccessible(true);
        amounts = (Map<Object, Object>) amountField.get(null);
        originalAmounts = new HashMap<Object, Object>(amounts);
        recipes.clear();
        amounts.clear();
        Field field = HbmForgeFluidRegistry.class.getDeclaredField("MAPPINGS");
        field.setAccessible(true);
        Object table = field.get(null);
        Field canonical = table.getClass().getDeclaredField("canonical");
        canonical.setAccessible(true);
        mappings = (Map<String, FluidType>) canonical.get(table);
        previousMapping = mappings.put(FORGE_ACID.getName(), ACID);
        machine.tank = new FluidTank(OTHER, 8000);
        CrystallizerRecipes.registerRecipe(new ComparableStack(ingredient), recipe(2), acid(500));
    }

    @After public void restore() {
        recipes.clear();
        recipes.putAll(originalRecipes);
        amounts.clear();
        amounts.putAll(originalAmounts);
        if (previousMapping == null) mappings.remove(FORGE_ACID.getName());
        else mappings.put(FORGE_ACID.getName(), previousMapping);
    }

    private CrystallizerRecipe recipe(int count) {
        return new CrystallizerRecipe(new ItemStack(product), 20).setReq(count);
    }

    private com.hbm.inventory.FluidStack acid(int amount) {
        return new com.hbm.inventory.FluidStack(ACID, amount);
    }

    /** Minimal fluid value fixture: Forge's fluid registry requires a running game. */
    private static final class TestFluidStack extends FluidStack {
        private TestFluidStack() { super(FORGE_ACID, 1); }
        static TestFluidStack create(int amount) {
            try {
                Constructor<?> ctor = sun.reflect.ReflectionFactory.getReflectionFactory()
                        .newConstructorForSerialization(TestFluidStack.class, Object.class.getDeclaredConstructor());
                TestFluidStack stack = (TestFluidStack) ctor.newInstance();
                Field delegate = FluidStack.class.getDeclaredField("fluidDelegate");
                delegate.setAccessible(true);
                delegate.set(stack, new cpw.mods.fml.common.registry.RegistryDelegate<Fluid>() {
                    @Override public Fluid get() { return FORGE_ACID; }
                    @Override public String name() { return FORGE_ACID.getName(); }
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

    private PatternStacks inputs(int count, int fluid) throws Exception {
        return inputs(true, fluid, new ItemStack(ingredient, count));
    }

    private PatternStacks inputs(boolean valid, int fluid, ItemStack... items) throws Exception {
        Constructor<PatternStacks> ctor = PatternStacks.class.getDeclaredConstructor(List.class, List.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(Arrays.asList(items), Collections.singletonList(TestFluidStack.create(fluid)), valid);
    }

    private boolean push() throws Exception {
        PatternStacks inputs = inputs(2, 500);
        return driver.push(machine, driver.matchInputs(inputs), inputs);
    }

    private void assertRejectedUnchanged() throws Exception {
        FluidType type = machine.tank.getTankType();
        int fill = machine.tank.getFill();
        int dirty = machine.dirty;
        assertFalse(push());
        assertNull(machine.getStackInSlot(0));
        assertSame(type, machine.tank.getTankType());
        assertEquals(fill, machine.tank.getFill());
        assertEquals(dirty, machine.dirty);
    }

    @Test public void exactInputsOnlyAndInvalidInputsRejected() throws Exception {
        assertNotNull(driver.matchInputs(inputs(2, 500)));
        assertNull(driver.matchInputs(inputs(1, 500)));
        assertNull(driver.matchInputs(inputs(3, 500)));
        assertNull(driver.matchInputs(inputs(2, 499)));
        assertNull(driver.matchInputs(inputs(2, 501)));
        assertNull(driver.matchInputs(inputs(false, 500, new ItemStack(ingredient, 2))));
        assertNull(driver.matchInputs(inputs(true, 500, new ItemStack(product, 2))));
        assertNull(driver.matchInputs(inputs(true, 500, new ItemStack(ingredient, 2), new ItemStack(product))));
    }

    @Test public void emptyTankSwitchesAndInputsAreNotMutated() throws Exception {
        PatternStacks inputs = inputs(2, 500);
        assertTrue(driver.push(machine, driver.matchInputs(inputs), inputs));
        assertSame(ACID, machine.tank.getTankType());
        assertEquals(500, machine.tank.getFill());
        assertEquals(2, machine.getStackInSlot(0).stackSize);
        assertEquals(2, inputs.getItems().get(0).stackSize);
        assertEquals(500, inputs.getFluids().get(0).amount);
        assertTrue(machine.dirty > 0);
    }

    @Test public void compatibleRemainderAndIdentifierAllowPush() throws Exception {
        machine.tank.setTankType(ACID);
        machine.tank.setFill(250);
        machine.setInventorySlotContents(7, new ItemStack(new Identifier(ACID)));
        assertTrue(push());
        assertEquals(750, machine.tank.getFill());
        assertNotNull(machine.getStackInSlot(7));
    }

    @Test public void incompatibleRemainderCapacityPressureAndIdentifierAreAtomic() throws Exception {
        machine.tank.setFill(1);
        assertRejectedUnchanged();
        machine.tank.setFill(0);
        machine.setInventorySlotContents(7, new ItemStack(new Identifier(OTHER)));
        ItemStack identifier = machine.getStackInSlot(7);
        assertRejectedUnchanged();
        assertSame(identifier, machine.getStackInSlot(7));
        machine.setInventorySlotContents(7, null);
        machine.tank = new FluidTank(OTHER, 499);
        assertRejectedUnchanged();
        machine.tank = new FluidTank(ACID, 8000);
        machine.tank.setFill(7501);
        assertRejectedUnchanged();
        machine.tank.setFill(0);
        machine.tank.withPressure(1);
        assertRejectedUnchanged();
    }

    @Test public void busyInputsAndProgressBlockButOutputsDoNot() throws Exception {
        machine.progress = 1;
        assertTrue(driver.isBusy(machine));
        assertRejectedUnchanged();
        machine.progress = 0;
        machine.setInventorySlotContents(0, new ItemStack(ingredient));
        assertTrue(driver.isBusy(machine));
        assertFalse(push());
        assertEquals(1, machine.getStackInSlot(0).stackSize);
        machine.setInventorySlotContents(0, null);
        machine.setInventorySlotContents(2, new ItemStack(product, 64));
        assertFalse(driver.isBusy(machine));
        assertTrue(push());
        assertEquals(64, machine.getStackInSlot(2).stackSize);
    }

    @Test public void registryAndRecoveryExposeOnlyProductSlot() {
        assertTrue(HbmMachineDrivers.forTile(machine) instanceof CrystallizerDriver);
        assertArrayEquals(new int[] { 2 }, driver.getOutputSlots(machine));
        assertEquals(0, driver.getOutputTanks(machine).length);
        assertSame(machine.tank, driver.getInputTanks(machine)[0]);
        assertEquals("crystallizer", driver.getMachineId());
    }

    @Test public void continuousRefillsPartialInputAndKeepsProgressAndOutputs() throws Exception {
        assertTrue(push());
        machine.getStackInSlot(0).stackSize = 1;
        machine.progress = 1;
        machine.setInventorySlotContents(2, new ItemStack(product, 64));
        PatternStacks inputs = inputs(2, 500);
        HbmRecipeMatch match = driver.matchInputs(inputs);
        assertFalse(driver.push(machine, match, inputs, FactoryAllocationMode.PARALLEL_FIRST, FeedingMode.SINGLE_BATCH));
        assertTrue(driver.push(machine, match, inputs, FactoryAllocationMode.PARALLEL_FIRST, FeedingMode.CONTINUOUS));
        assertEquals(3, machine.getStackInSlot(0).stackSize);
        assertEquals(1000, machine.tank.getFill());
        assertEquals(1, machine.progress);
        assertEquals(64, machine.getStackInSlot(2).stackSize);
    }

    @Test public void singleItemCustomRecipeMatches() throws Exception {
        CrystallizerRecipes.registerRecipe(new ComparableStack(ingredient), recipe(1), acid(250));
        PatternStacks inputs = inputs(1, 250);
        assertNotNull(driver.matchInputs(inputs));
        assertTrue(driver.push(machine, driver.matchInputs(inputs), inputs));
        assertEquals(1, machine.getStackInSlot(0).stackSize);
        assertEquals(250, machine.tank.getFill());
    }

    @Test public void overlappingRecipeInputsAreRejected() throws Exception {
        ComparableStack overlapping = new ComparableStack(ingredient) {};
        CrystallizerRecipes.registerRecipe(overlapping, recipe(2), acid(500));
        assertNull(driver.matchInputs(inputs(2, 500)));
    }

    @Test public void changedRuntimeRecipeRejectsStaleMatch() throws Exception {
        PatternStacks inputs = inputs(2, 500);
        HbmRecipeMatch match = driver.matchInputs(inputs);
        CrystallizerRecipes.registerRecipe(new ComparableStack(ingredient), recipe(3), acid(500));
        assertFalse(driver.push(machine, match, inputs));
        assertNull(machine.getStackInSlot(0));
        assertSame(OTHER, machine.tank.getTankType());
        assertEquals(0, machine.tank.getFill());
    }
}

package io.github.hbmcompat.machine;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.fluids.FluidStack;

import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.module.machine.ModuleMachineBase;
import org.junit.Test;
import static org.junit.Assert.*;

public class FactoryAllocationTest {
    private final Item ingredient = new Item();
    private final TileEntityChest inventory = new TileEntityChest();
    private final TestDriver driver = new TestDriver();

    private static final class TestDriver extends AbstractHbmFactoryDriver {
        final ModuleMachineBase[] modules = new ModuleMachineBase[4];
        TestDriver() {
            for (int lane = 0; lane < modules.length; lane++) {
                ModuleMachineBase module = new ModuleMachineBase(lane, null, new ItemStack[27]) {
                    @Override public GenericRecipes getRecipeSet() { return null; }
                };
                module.inputSlots = new int[] { lane };
                module.outputSlots = new int[] { lane + 4 };
                module.inputTanks = new FluidTank[0];
                module.outputTanks = new FluidTank[0];
                modules[lane] = module;
            }
        }
        @Override public String getMachineId() { return "test"; }
        @Override public boolean supports(TileEntity tile) { return true; }
        @Override protected GenericRecipes<? extends GenericRecipe> getRecipeSet() { return null; }
        @Override protected ModuleMachineBase[] getModules(TileEntity tile) { return modules; }
        @Override protected int getBlueprintSlot(int lane) { return 8 + lane; }
        @Override protected String getFactoryName() { return "test factory"; }
    }

    private boolean push(String name, FactoryAllocationMode mode) throws Exception {
        GenericRecipe recipe = new GenericRecipe(name).inputItems(new ComparableStack(ingredient, 1));
        return push(recipe, mode);
    }

    private boolean push(GenericRecipe recipe, FactoryAllocationMode mode) throws Exception {
        Constructor<PatternStacks> constructor = PatternStacks.class.getDeclaredConstructor(
                List.class, List.class, boolean.class);
        constructor.setAccessible(true);
        PatternStacks inputs = constructor.newInstance(Collections.singletonList(new ItemStack(ingredient)),
                Collections.<FluidStack>emptyList(), true);
        return driver.push(inventory, new HbmRecipeMatch(driver, recipe, recipe.getInternalName()), inputs, mode);
    }

    @Test public void parallelFillsFourLanesButNeverOverwritesPendingInputs() throws Exception {
        for (int lane = 0; lane < 4; lane++) assertTrue(push("A", FactoryAllocationMode.PARALLEL_FIRST));
        assertFalse(push("A", FactoryAllocationMode.PARALLEL_FIRST));
        for (int lane = 0; lane < 4; lane++) assertEquals(1, inventory.getStackInSlot(lane).stackSize);
    }

    @Test public void varietyRejectsPendingDuplicateAndAcceptsOtherRecipes() throws Exception {
        assertTrue(push("A", FactoryAllocationMode.VARIETY_FIRST));
        assertFalse(push("A", FactoryAllocationMode.VARIETY_FIRST));
        assertNull(inventory.getStackInSlot(1));
        assertTrue(push("B", FactoryAllocationMode.VARIETY_FIRST));
        assertTrue(push("C", FactoryAllocationMode.VARIETY_FIRST));
        assertTrue(push("D", FactoryAllocationMode.VARIETY_FIRST));
    }

    @Test public void runningLaneBlocksDuplicateUntilFinishedEvenWithNoInputs() throws Exception {
        driver.modules[2].setRecipe("A", false);
        driver.modules[2].progress = 0.5D;
        assertFalse(push("A", FactoryAllocationMode.VARIETY_FIRST));
        driver.modules[2].progress = 0;
        inventory.setInventorySlotContents(6, new ItemStack(ingredient));
        assertTrue(push("A", FactoryAllocationMode.VARIETY_FIRST));
        assertNotNull(inventory.getStackInSlot(2));
        assertEquals(1, inventory.getStackInSlot(6).stackSize);
    }

    @Test public void modeChangeDoesNotCancelPreviouslyAcceptedJobs() throws Exception {
        assertTrue(push("A", FactoryAllocationMode.PARALLEL_FIRST));
        assertTrue(push("A", FactoryAllocationMode.PARALLEL_FIRST));
        assertFalse(push("A", FactoryAllocationMode.VARIETY_FIRST));
        assertNotNull(inventory.getStackInSlot(0));
        assertNotNull(inventory.getStackInSlot(1));
        assertTrue(push("B", FactoryAllocationMode.VARIETY_FIRST));
        assertTrue(push("A", FactoryAllocationMode.PARALLEL_FIRST));
    }

    @Test public void modesDecodeConservatively() {
        assertEquals(FactoryAllocationMode.PARALLEL_FIRST, FactoryAllocationMode.decode(""));
        assertEquals(FactoryAllocationMode.PARALLEL_FIRST, FactoryAllocationMode.decode("future-mode"));
        for (FactoryAllocationMode mode : FactoryAllocationMode.values()) {
            assertEquals(mode, FactoryAllocationMode.decode(mode.name()));
            assertEquals(mode, mode.next().next());
            net.minecraft.nbt.NBTTagCompound data = new net.minecraft.nbt.NBTTagCompound();
            mode.write(data);
            assertEquals(mode, FactoryAllocationMode.read(data));
        }
        net.minecraft.nbt.NBTTagCompound legacy = new net.minecraft.nbt.NBTTagCompound();
        assertEquals(FactoryAllocationMode.PARALLEL_FIRST, FactoryAllocationMode.read(legacy));
        legacy.setString("hbmFactoryAllocation", "UNKNOWN");
        assertEquals(FactoryAllocationMode.PARALLEL_FIRST, FactoryAllocationMode.read(legacy));
    }

    @Test public void pendingFluidAloneReservesRecipe() throws Exception {
        driver.modules[0].setRecipe("A", false);
        FluidTank tank = new FluidTank(com.hbm.inventory.fluid.Fluids.WATER, 1000);
        tank.setFill(100);
        driver.modules[0].inputTanks = new FluidTank[] { tank };
        assertFalse(push("A", FactoryAllocationMode.VARIETY_FIRST));
        assertEquals(100, tank.getFill());
        assertTrue(push("B", FactoryAllocationMode.VARIETY_FIRST));
    }

    @Test public void outputFluidIsNotDestroyedWhenRetargeting() throws Exception {
        FluidTank tank = new FluidTank(com.hbm.inventory.fluid.Fluids.WATER, 1000);
        tank.setFill(100);
        driver.modules[0].outputTanks = new FluidTank[] { tank };
        assertTrue(push("A", FactoryAllocationMode.VARIETY_FIRST));
        assertNull(inventory.getStackInSlot(0));
        assertNotNull(inventory.getStackInSlot(1));
        assertEquals(100, tank.getFill());
    }

    @Test public void missingBlueprintRejectsWithoutConsumingAnyInput() throws Exception {
        GenericRecipe locked = new GenericRecipe("locked") {
            @Override public boolean isPooled() { return true; }
            @Override public boolean isPartOfPool(String pool) { return false; }
        }.inputItems(new ComparableStack(ingredient, 1));
        for (FactoryAllocationMode mode : FactoryAllocationMode.values()) {
            assertFalse(push(locked, mode));
            for (int lane = 0; lane < 4; lane++) assertNull(inventory.getStackInSlot(lane));
        }
    }
}

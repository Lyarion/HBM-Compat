package io.github.hbmcompat.machine;

import java.lang.reflect.Constructor;
import java.util.Collections;
import java.util.List;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.RecipesCommon.ComparableStack;
import com.hbm.inventory.recipes.ArcWelderRecipes;
import com.hbm.inventory.recipes.SolderingRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.module.machine.ModuleMachineBase;
import com.hbm.tileentity.machine.*;
import org.junit.Test;
import static org.junit.Assert.*;

public class ContinuousFeedingTest {
    private final Item item = new Item();

    private PatternStacks inputs() throws Exception {
        Constructor<PatternStacks> ctor = PatternStacks.class.getDeclaredConstructor(List.class, List.class, boolean.class);
        ctor.setAccessible(true);
        return ctor.newInstance(Collections.singletonList(new ItemStack(item, 2)), Collections.emptyList(), true);
    }

    private boolean push(IHbmMachineDriver driver, TileEntity tile, Object recipe, String name, FeedingMode mode) throws Exception {
        return driver.push(tile, new HbmRecipeMatch(driver, recipe, name), inputs(), FactoryAllocationMode.VARIETY_FIRST, mode);
    }

    private void checkModule(TileEntity tile, ModuleMachineBase module) throws Exception {
        IHbmMachineDriver driver = HbmMachineDrivers.forTile(tile);
        IInventory inventory = (IInventory) tile;
        GenericRecipe recipe = new GenericRecipe("feeding_test").inputItems(new ComparableStack(item, 2));
        assertTrue(push(driver, tile, recipe, recipe.getInternalName(), FeedingMode.CONTINUOUS));
        module.progress = 0.5;
        inventory.getStackInSlot(module.inputSlots[0]).stackSize = 1;
        assertFalse(push(driver, tile, recipe, recipe.getInternalName(), FeedingMode.SINGLE_BATCH));
        assertTrue(push(driver, tile, recipe, recipe.getInternalName(), FeedingMode.CONTINUOUS));
        assertEquals(3, inventory.getStackInSlot(module.inputSlots[0]).stackSize);
        assertEquals(0.5, module.progress, 0);
        ItemStack tagged = inventory.getStackInSlot(module.inputSlots[0]);
        tagged.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        tagged.getTagCompound().setString("test", "different");
        assertFalse(push(driver, tile, recipe, recipe.getInternalName(), FeedingMode.CONTINUOUS));
        assertEquals(3, tagged.stackSize);
    }

    @Test public void assemblerRefillsWithoutReset() throws Exception {
        TileEntityMachineAssemblyMachine tile = new TileEntityMachineAssemblyMachine();
        checkModule(tile, tile.assemblerModule);
    }
    @Test public void chemicalPlantRefillsWithoutReset() throws Exception {
        TileEntityMachineChemicalPlant tile = new TileEntityMachineChemicalPlant();
        checkModule(tile, tile.chemplantModule);
    }
    @Test public void assemblyFactoryRefillsWithoutReset() throws Exception {
        TileEntityMachineAssemblyFactory tile = new TileEntityMachineAssemblyFactory();
        checkModule(tile, tile.assemblerModule[0]);
    }
    @Test public void chemicalFactoryRefillsWithoutReset() throws Exception {
        TileEntityMachineChemicalFactory tile = new TileEntityMachineChemicalFactory();
        checkModule(tile, tile.chemplantModule[0]);
    }

    @Test public void arcWelderUsesRealRecipeNotAutoMetadata() throws Exception {
        ArcWelderRecipes.ArcWelderRecipe recipe = new ArcWelderRecipes.ArcWelderRecipe(
                new ItemStack(item), 20, 1, new ComparableStack(item, 2));
        ArcWelderRecipes.ArcWelderRecipe other = new ArcWelderRecipes.ArcWelderRecipe(
                new ItemStack(item), 20, 1, new ComparableStack(item, 4));
        ArcWelderRecipes.recipes.add(recipe);
        ArcWelderRecipes.recipes.add(other);
        try {
            TileEntityMachineArcWelder tile = new TileEntityMachineArcWelder();
            IHbmMachineDriver driver = new ArcWelderDriver();
            assertTrue(push(driver, tile, recipe, "auto", FeedingMode.CONTINUOUS));
            tile.progress = 1;
            assertTrue(push(driver, tile, recipe, "auto", FeedingMode.CONTINUOUS));
            assertEquals(4, tile.getStackInSlot(0).stackSize);
            assertEquals(1, tile.progress);
            assertFalse(push(driver, tile, other, "auto", FeedingMode.CONTINUOUS));
            assertFalse(push(driver, tile, recipe, "auto", FeedingMode.SINGLE_BATCH));
        } finally {
            ArcWelderRecipes.recipes.remove(recipe);
            ArcWelderRecipes.recipes.remove(other);
        }
    }

    @Test public void solderingUsesRealRecipeAndRejectsExtraInputs() throws Exception {
        // HBM hashes recipe ingredients using the live item registry, absent in this unit fixture.
        ComparableStack topping = new ComparableStack(item, 2) {
            @Override public int hashCode() { return System.identityHashCode(this); }
        };
        SolderingRecipes.SolderingRecipe recipe = new SolderingRecipes.SolderingRecipe(
                new ItemStack(item), 20, 1, new AStack[] { topping }, new AStack[0], new AStack[0]);
        SolderingRecipes.recipes.add(recipe);
        try {
            TileEntityMachineSolderingStation tile = new TileEntityMachineSolderingStation();
            IHbmMachineDriver driver = new SolderingStationDriver();
            assertTrue(push(driver, tile, recipe, "auto", FeedingMode.CONTINUOUS));
            tile.progress = 1;
            assertTrue(push(driver, tile, recipe, "auto", FeedingMode.CONTINUOUS));
            assertEquals(4, tile.getStackInSlot(0).stackSize);
            assertEquals(1, tile.progress);
            tile.setInventorySlotContents(4, new ItemStack(item));
            assertFalse(push(driver, tile, recipe, "auto", FeedingMode.CONTINUOUS));
        } finally {
            SolderingRecipes.recipes.remove(recipe);
            SolderingRecipes.toppings.remove(topping);
        }
    }
}

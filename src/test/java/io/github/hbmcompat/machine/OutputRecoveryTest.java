package io.github.hbmcompat.machine;

import java.util.Arrays;
import java.util.Collections;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityChest;
import net.minecraftforge.fluids.Fluid;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.render.util.EnumSymbol;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import org.junit.Test;
import static org.junit.Assert.*;

public class OutputRecoveryTest {
    private static final FluidType HBM_WATER = new FluidType("test_recovery_water", 0, 0, 0, 0, EnumSymbol.NONE);
    private static final Fluid FORGE_WATER = new Fluid("test_recovery_water");
    private final Item product = new Item();
    private final Machine machine = new Machine();
    private final Driver driver = new Driver();
    private final Sink sink = new Sink();
    private boolean mappingAvailable = true;

    private static final class Machine extends TileEntityChest {
        int dirty;
        @Override public void markDirty() { dirty++; }
    }

    private static final class Driver implements IHbmMachineDriver {
        int[] slots = { 4, 5, 6, 7 };
        FluidTank[] tanks = new FluidTank[0];
        @Override public String getMachineId() { return "test"; }
        @Override public boolean supports(TileEntity tile) { return tile instanceof Machine; }
        @Override public HbmRecipeMatch match(ICraftingPatternDetails pattern) {
            throw new AssertionError("Recovery must not inspect patterns");
        }
        @Override public boolean isBusy(TileEntity tile) {
            throw new AssertionError("Recovery must not depend on machine processing state");
        }
        @Override public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks supplied) {
            throw new AssertionError("Recovery must not push inputs");
        }
        @Override public int[] getOutputSlots(TileEntity tile) { return slots; }
        @Override public FluidTank[] getOutputTanks(TileEntity tile) { return tanks; }
        @Override public FluidTank[] getInputTanks(TileEntity tile) {
            throw new AssertionError("Recovery must not access input tanks");
        }
    }

    private static final class Sink implements OutputRecovery.Sink {
        int itemBudget = Integer.MAX_VALUE;
        int fluidBudget = Integer.MAX_VALUE;
        int items, fluids, itemCalls, fluidCalls;
        ItemStack lastItem;
        Fluid lastFluid;
        @Override public int insertItem(ItemStack offered) {
            itemCalls++;
            lastItem = offered.copy();
            int accepted = Math.min(itemBudget, offered.stackSize);
            itemBudget -= accepted;
            items += accepted;
            // A sink can mutate its argument; that must not corrupt the machine's remaining stack.
            offered.stackSize = 0;
            if (offered.hasTagCompound()) offered.getTagCompound().setString("owner", "changed");
            return accepted;
        }
        @Override public int insertFluid(Fluid fluid, int offered) {
            fluidCalls++;
            lastFluid = fluid;
            int accepted = Math.min(fluidBudget, offered);
            fluidBudget -= accepted;
            fluids += accepted;
            return accepted;
        }
    }

    private FluidTank tank(FluidType type, int amount, int pressure) {
        FluidTank tank = new FluidTank(type, 10000).withPressure(pressure);
        tank.setFill(amount);
        return tank;
    }

    private boolean recover(TileEntity... targets) {
        return OutputRecovery.recover(Arrays.asList(targets), t -> t instanceof Machine ? driver : null,
                t -> mappingAvailable ? FORGE_WATER : null, sink);
    }

    @Test public void recoversEveryOutputLaneAndByproductWithoutPatterns() {
        machine.setInventorySlotContents(0, new ItemStack(product, 32));
        machine.setInventorySlotContents(8, new ItemStack(product, 1)); // blueprint/tool slot
        for (int i = 4; i < 8; i++) machine.setInventorySlotContents(i, new ItemStack(product, i));
        machine.dirty = 0;
        assertTrue(recover(machine));
        assertEquals(22, sink.items);
        for (int i = 4; i < 8; i++) assertNull(machine.getStackInSlot(i));
        assertEquals(32, machine.getStackInSlot(0).stackSize);
        assertEquals(1, machine.getStackInSlot(8).stackSize);
        assertTrue(machine.dirty > 0);
    }

    @Test public void partialItemAcceptancePreservesCountMetadataAndTags() {
        ItemStack output = new ItemStack(product, 20, 7);
        output.setTagCompound(new NBTTagCompound());
        output.getTagCompound().setString("owner", "original");
        machine.setInventorySlotContents(4, output);
        sink.itemBudget = 3;
        assertTrue(recover(machine));
        assertEquals(3, sink.items);
        assertEquals(17, machine.getStackInSlot(4).stackSize);
        assertEquals(7, sink.lastItem.getItemDamage());
        assertEquals("original", sink.lastItem.getTagCompound().getString("owner"));
        assertEquals("original", machine.getStackInSlot(4).getTagCompound().getString("owner"));
    }

    @Test public void rejectedOutputsRemainAndRetryWhenNetworkAcceptsAgain() {
        machine.setInventorySlotContents(4, new ItemStack(product, 12));
        driver.tanks = new FluidTank[] { tank(HBM_WATER, 2000, 0) };
        sink.itemBudget = 0;
        sink.fluidBudget = 0;
        machine.dirty = 0;
        assertFalse(recover(machine));
        assertEquals(0, machine.dirty);
        assertEquals(12, machine.getStackInSlot(4).stackSize);
        assertEquals(2000, driver.tanks[0].getFill());
        sink.itemBudget = 12;
        sink.fluidBudget = 2000;
        assertTrue(recover(machine));
        assertNull(machine.getStackInSlot(4));
        assertEquals(0, driver.tanks[0].getFill());
        assertEquals(12, sink.items);
        assertEquals(2000, sink.fluids);
    }

    @Test public void partialFluidAcceptancePreservesTypeAndRemainingAmount() {
        driver.tanks = new FluidTank[] { tank(HBM_WATER, 2500, 0) };
        sink.fluidBudget = 750;
        assertTrue(recover(machine));
        assertEquals(1750, driver.tanks[0].getFill());
        assertSame(HBM_WATER, driver.tanks[0].getTankType());
        assertEquals(0, driver.tanks[0].getPressure());
        assertEquals(750, sink.fluids);
        assertSame(FORGE_WATER, sink.lastFluid);
    }

    @Test public void unmappedPressurizedAndUntypedFluidsStayInMachine() {
        driver.tanks = new FluidTank[] { tank(HBM_WATER, 1000, 1), tank(Fluids.NONE, 1000, 0),
                tank(HBM_WATER, 1000, 0), null };
        mappingAvailable = false;
        assertFalse(recover(machine));
        assertEquals(0, sink.fluidCalls);
        for (int i = 0; i < 3; i++) assertEquals(1000, driver.tanks[i].getFill());
        mappingAvailable = true;
        assertTrue(recover(machine));
        assertEquals(1000, sink.fluids);
        assertEquals(1000, driver.tanks[0].getFill());
        assertEquals(1000, driver.tanks[1].getFill());
        assertEquals(0, driver.tanks[2].getFill());
    }

    @Test public void duplicateCoreIsOnlyVisitedOncePerSweep() {
        machine.setInventorySlotContents(4, new ItemStack(product, 10));
        sink.itemBudget = 1;
        assertTrue(recover(machine, machine, machine));
        assertEquals(1, sink.itemCalls);
        assertEquals(9, machine.getStackInSlot(4).stackSize);
    }

    @Test public void separateMachinesAreBothVisited() {
        Machine second = new Machine();
        machine.setInventorySlotContents(4, new ItemStack(product, 2));
        second.setInventorySlotContents(7, new ItemStack(product, 3));
        assertTrue(recover(machine, second));
        assertEquals(5, sink.items);
        assertNull(second.getStackInSlot(7));
    }

    @Test public void emptyMachineCanProduceLaterWithoutAnExternalNotification() {
        assertFalse(recover(machine));
        machine.setInventorySlotContents(4, new ItemStack(product, 8));
        assertTrue(recover(machine));
        assertEquals(8, sink.items);
    }

    @Test public void invalidMissingAndUnsupportedTargetsAreIgnored() {
        machine.setInventorySlotContents(4, new ItemStack(product, 8));
        machine.invalidate();
        assertFalse(recover(null, new TileEntityChest(), machine));
        assertEquals(0, sink.itemCalls);
        assertEquals(8, machine.getStackInSlot(4).stackSize);
    }

    @Test public void suppliedFluidMappingIdentityIsUsed() {
        Fluid mapped = new Fluid("hbmcompat_test_output");
        driver.tanks = new FluidTank[] { tank(HBM_WATER, 400, 0) };
        assertTrue(OutputRecovery.recover(Collections.<TileEntity>singletonList(machine), t -> driver,
                t -> mapped, sink));
        assertSame(mapped, sink.lastFluid);
        assertEquals(400, sink.fluids);
    }
}

package io.github.hbmcompat.machine;

import java.util.Arrays;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.tileentity.machine.TileEntityMachineAssemblyFactory;
import com.hbm.tileentity.machine.TileEntityMachineChemicalFactory;
import org.junit.Test;

import static org.junit.Assert.*;

public class FactoryCoolingWaterTest {

    @Test
    public void assemblyFactoryExposesCoolingWaterToExportBus() {
        TileEntityMachineAssemblyFactory tile = new TileEntityMachineAssemblyFactory();
        checkCoolingWater(tile, tile.water, tile.lps, tile.inputTanks);
    }

    @Test
    public void chemicalFactoryExposesCoolingWaterToExportBus() {
        TileEntityMachineChemicalFactory tile = new TileEntityMachineChemicalFactory();
        checkCoolingWater(tile, tile.water, tile.lps, tile.inputTanks);
    }

    private void checkCoolingWater(TileEntity tile, FluidTank water, FluidTank steam, FluidTank[] recipeInputs) {
        IHbmMachineDriver driver = HbmMachineDrivers.forTile(tile);
        FluidTank[] sinks = HbmFluidAccess.sinkTanks(tile);
        assertEquals(recipeInputs.length + 1, sinks.length);
        assertArrayEquals(recipeInputs, Arrays.copyOf(sinks, recipeInputs.length));
        assertSame(water, sinks[recipeInputs.length]);
        assertSame(Fluids.WATER, water.getTankType());
        assertFalse(Arrays.asList(sinks).contains(steam));
        assertFalse(Arrays.asList(HbmFluidAccess.sourceTanks(tile)).contains(water));

        // Filling the bus destination must update the actual cooling tank, without
        // making coolant a recipe ingredient or making idle lanes look occupied.
        sinks[recipeInputs.length].setFill(1000);
        assertEquals(1000, water.getFill());
        assertArrayEquals(recipeInputs, driver.getInputTanks(tile));
        assertFalse(driver.isBusy(tile));
        for (FluidTank tank : recipeInputs) {
            assertEquals(0, tank.getFill());
            assertSame(Fluids.NONE, tank.getTankType());
        }

        // A full coolant tank must not put the bus permanently to sleep.
        water.setFill(water.getMaxFill());
        assertTrue(HbmFluidAccess.canEverSink(tile));
        water.setFill(0);
        assertSame(water, HbmFluidAccess.sinkTanks(tile)[recipeInputs.length]);
    }
}

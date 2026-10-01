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
    public void assemblyFactoryExposesSpentSteamToImportBus() {
        TileEntityMachineAssemblyFactory tile = new TileEntityMachineAssemblyFactory();
        checkSpentSteam(tile, tile.water, tile.lps, tile.outputTanks);
    }

    @Test
    public void chemicalFactoryExposesSpentSteamToImportBus() {
        TileEntityMachineChemicalFactory tile = new TileEntityMachineChemicalFactory();
        checkSpentSteam(tile, tile.water, tile.lps, tile.outputTanks);
    }

    private void checkSpentSteam(TileEntity tile, FluidTank water, FluidTank steam, FluidTank[] recipeOutputs) {
        IHbmMachineDriver driver = HbmMachineDrivers.forTile(tile);
        FluidTank[] sources = HbmFluidAccess.sourceTanks(tile);
        assertEquals(recipeOutputs.length + 1, sources.length);
        assertArrayEquals(recipeOutputs, Arrays.copyOf(sources, recipeOutputs.length));
        assertSame(steam, sources[recipeOutputs.length]);
        assertSame(Fluids.SPENTSTEAM, steam.getTankType());
        assertEquals(0, steam.getPressure());
        assertFalse(Arrays.asList(sources).contains(water));
        assertFalse(Arrays.asList(HbmFluidAccess.sinkTanks(tile)).contains(steam));

        // Empty steam tanks must keep polling so later cooling output can be imported.
        assertEquals(0, steam.getFill());
        assertTrue(HbmFluidAccess.canEverSource(tile));
        steam.setFill(steam.getMaxFill());
        water.setFill(1000);
        FluidTank source = HbmFluidAccess.sourceTanks(tile)[recipeOutputs.length];
        source.setFill(source.getFill() - 100);
        assertEquals(steam.getMaxFill() - 100, steam.getFill());
        assertEquals(1000, water.getFill());
        assertArrayEquals(recipeOutputs, driver.getOutputTanks(tile));
        assertFalse(driver.isBusy(tile));
        for (FluidTank tank : recipeOutputs) {
            assertEquals(0, tank.getFill());
            assertSame(Fluids.NONE, tank.getTankType());
        }
        source.setFill(0);
        assertTrue(HbmFluidAccess.canEverSource(tile));
    }

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

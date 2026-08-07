package io.github.hbmcompat.machine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class HbmMachineDrivers {

    private static final List<IHbmMachineDriver> DRIVERS = Collections.unmodifiableList(Arrays.<IHbmMachineDriver>asList(
            new AssemblyMachineDriver(),
            new ChemicalPlantDriver(),
            new ArcWelderDriver(),
            new SolderingStationDriver()));

    private HbmMachineDrivers() {}

    public static IHbmMachineDriver forTile(TileEntity tile) {
        for (IHbmMachineDriver driver : DRIVERS) {
            if (driver.supports(tile)) {
                return driver;
            }
        }
        return null;
    }

    public static HbmRecipeMatch uniqueMatch(ICraftingPatternDetails details) {
        HbmRecipeMatch result = null;
        for (IHbmMachineDriver driver : DRIVERS) {
            HbmRecipeMatch match = driver.match(details);
            if (match != null) {
                if (result != null) {
                    return null;
                }
                result = match;
            }
        }
        return result;
    }
}

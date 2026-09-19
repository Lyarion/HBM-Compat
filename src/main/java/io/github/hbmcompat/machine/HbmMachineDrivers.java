package io.github.hbmcompat.machine;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import net.minecraft.tileentity.TileEntity;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class HbmMachineDrivers {

    private static final List<IHbmMachineDriver> DRIVERS = Collections.unmodifiableList(Arrays.<IHbmMachineDriver>asList(
            new AssemblyMachineDriver(),
            new AssemblyFactoryDriver(),
            new ChemicalPlantDriver(),
            new ChemicalFactoryDriver(),
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
                    // Several concrete machines can execute the same recipe family. They are one
                    // logical match for pattern metadata, not an ambiguity. A real cross-family or
                    // cross-recipe collision remains ambiguous and is rejected as before.
                    if (!result.getDriver().getMachineId().equals(match.getDriver().getMachineId())
                            || !result.getRecipeName().equals(match.getRecipeName())) {
                        return null;
                    }
                    continue;
                }
                result = match;
            }
        }
        return result;
    }
}

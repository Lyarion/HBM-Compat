package io.github.hbmcompat.machine;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.recipes.ChemicalPlantRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.module.machine.ModuleMachineBase;
import com.hbm.tileentity.machine.TileEntityMachineChemicalFactory;

/** AE2 processing-pattern support for the four-lane HBM Chemical Factory. */
public final class ChemicalFactoryDriver extends AbstractHbmFactoryDriver {

    @Override
    public String getMachineId() {
        // Canonical recipe-family id shared with the ordinary chemical plant.
        return "chemical_plant";
    }

    @Override
    public boolean supports(TileEntity tile) {
        return tile instanceof TileEntityMachineChemicalFactory;
    }

    @Override
    protected GenericRecipes<? extends GenericRecipe> getRecipeSet() {
        return ChemicalPlantRecipes.INSTANCE;
    }

    @Override
    protected ModuleMachineBase[] getModules(TileEntity tile) {
        return ((TileEntityMachineChemicalFactory) tile).chemplantModule;
    }

    @Override
    protected int getBlueprintSlot(int lane) {
        return 4 + lane * 7;
    }

    @Override
    protected String getFactoryName() {
        return "chemical factory";
    }
}

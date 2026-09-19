package io.github.hbmcompat.machine;

import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.recipes.AssemblyMachineRecipes;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;
import com.hbm.module.machine.ModuleMachineBase;
import com.hbm.tileentity.machine.TileEntityMachineAssemblyFactory;

/** AE2 processing-pattern support for the four-lane HBM Assembly Factory. */
public final class AssemblyFactoryDriver extends AbstractHbmFactoryDriver {

    @Override
    public String getMachineId() {
        // This is a recipe-family id, not a concrete block id. Keeping the same value as the
        // ordinary assembler makes already encoded patterns portable between both machines.
        return "assembly_machine";
    }

    @Override
    public boolean supports(TileEntity tile) {
        return tile instanceof TileEntityMachineAssemblyFactory;
    }

    @Override
    protected GenericRecipes<? extends GenericRecipe> getRecipeSet() {
        return AssemblyMachineRecipes.INSTANCE;
    }

    @Override
    protected ModuleMachineBase[] getModules(TileEntity tile) {
        return ((TileEntityMachineAssemblyFactory) tile).assemblerModule;
    }

    @Override
    protected int getBlueprintSlot(int lane) {
        return 4 + lane * 14;
    }

    @Override
    protected String getFactoryName() {
        return "assembly factory";
    }
}

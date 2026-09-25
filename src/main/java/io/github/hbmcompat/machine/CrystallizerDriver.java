package io.github.hbmcompat.machine;

import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.CrystallizerRecipes;
import com.hbm.inventory.recipes.CrystallizerRecipes.CrystallizerRecipe;
import com.hbm.items.machine.IItemFluidIdentifier;
import com.hbm.tileentity.machine.TileEntityMachineCrystallizer;
import com.hbm.util.Tuple.Pair;

import appeng.api.networking.crafting.ICraftingPatternDetails;

public final class CrystallizerDriver extends AbstractHbmMachineDriver {

    private static final int[] INPUT_SLOTS = { 0 };
    private static final int[] OUTPUT_SLOTS = { 2 };

    private static final class Recipe {
        final AStack[] items;
        final com.hbm.inventory.FluidStack[] fluids;
        final CrystallizerRecipe source;

        Recipe(Pair<AStack, FluidType> key, CrystallizerRecipe source) {
            this.items = new AStack[] { key.getKey().copy(source.itemAmount) };
            this.fluids = new com.hbm.inventory.FluidStack[] {
                    new com.hbm.inventory.FluidStack(key.getValue(), source.acidAmount) };
            this.source = source;
        }
    }

    @Override
    public String getMachineId() { return "crystallizer"; }

    @Override
    public boolean supports(TileEntity tile) { return tile instanceof TileEntityMachineCrystallizer; }

    @Override
    public HbmRecipeMatch match(ICraftingPatternDetails details) {
        return matchInputs(PatternStacks.inputs(details));
    }

    @SuppressWarnings("unchecked")
    HbmRecipeMatch matchInputs(PatternStacks inputs) {
        if (!inputs.isValid()) return null;
        // The NEI display map omits some recipes. Use the live recipe table instead.
        Map<Pair<AStack, FluidType>, CrystallizerRecipe> recipes =
                (Map<Pair<AStack, FluidType>, CrystallizerRecipe>) new CrystallizerRecipes().getRecipeObject();
        Recipe found = null;
        for (Map.Entry<Pair<AStack, FluidType>, CrystallizerRecipe> entry : recipes.entrySet()) {
            Recipe recipe = new Recipe(entry.getKey(), entry.getValue());
            if (PatternMatcher.matchesAStacks(recipe.items, inputs.getItems())
                    && PatternMatcher.matchesHbmFluids(recipe.fluids, inputs.getFluids())) {
                if (found != null) return null;
                found = recipe;
            }
        }
        return found == null ? null : new HbmRecipeMatch(this, found, "auto");
    }

    @Override
    public boolean isBusy(TileEntity tile) {
        TileEntityMachineCrystallizer machine = (TileEntityMachineCrystallizer) tile;
        return machine.progress > 0 || hasItems(machine, INPUT_SLOTS);
    }

    @Override
    public boolean push(TileEntity tile, HbmRecipeMatch match, PatternStacks suppliedInputs) {
        if (!suppliedInputs.isValid() || isBusy(tile)) return false;
        TileEntityMachineCrystallizer machine = (TileEntityMachineCrystallizer) tile;
        Recipe recipe = (Recipe) match.getRecipe();
        FluidType acid = recipe.fluids[0].type;
        List<ItemStack> assigned = PatternMatcher.assign(recipe.items, suppliedInputs.getItems());
        if (assigned == null || CrystallizerRecipes.getOutput(assigned.get(0), acid) != recipe.source) {
            AdapterDiagnostics.report(tile, "supplied item selects a different acidizer recipe; check pattern substitution");
            return false;
        }
        ItemStack identifier = machine.getStackInSlot(7);
        if (identifier != null && identifier.getItem() instanceof IItemFluidIdentifier
                && ((IItemFluidIdentifier) identifier.getItem()).getType(null, 0, 0, 0, identifier) != acid) {
            // HBM reapplies this identifier every tick, clearing the tank on a type change.
            AdapterDiagnostics.report(tile, "acidizer fluid identifier conflicts with the recipe; change or remove it");
            return false;
        }
        FluidTank[] tanks = getInputTanks(tile);
        MachineInputPlan plan = planInputs(tile, machine, INPUT_SLOTS, recipe.items, tanks,
                recipe.fluids, suppliedInputs);
        return plan != null && commitInputs(plan, tile, tanks, recipe.fluids);
    }

    @Override
    public FluidTank[] getInputTanks(TileEntity tile) {
        return new FluidTank[] { ((TileEntityMachineCrystallizer) tile).tank };
    }

    @Override
    public int[] getOutputSlots(TileEntity tile) { return OUTPUT_SLOTS; }

    @Override
    public FluidTank[] getOutputTanks(TileEntity tile) { return new FluidTank[0]; }
}

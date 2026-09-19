package io.github.hbmcompat.machine;

import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;

import com.hbm.inventory.RecipesCommon.AStack;
import com.hbm.inventory.fluid.tank.FluidTank;
import com.hbm.inventory.recipes.loader.GenericRecipe;
import com.hbm.inventory.recipes.loader.GenericRecipes;

abstract class AbstractHbmMachineDriver implements IHbmMachineDriver {

    protected boolean isAutoSwitchStable(
            TileEntity tile,
            GenericRecipe recipe,
            GenericRecipes<?> recipeSet,
            PatternStacks suppliedInputs) {
        if (recipe.autoSwitchGroup == null || recipe.inputItem == null || recipe.inputItem.length == 0) {
            return true;
        }
        List<ItemStack> assigned = PatternMatcher.assign(recipe.inputItem, suppliedInputs.getItems());
        if (assigned == null || assigned.isEmpty()) {
            // The real item mismatch is reported in detail by planInputs; stay quiet here so the
            // useful message is the one the player sees.
            return false;
        }
        List<GenericRecipe> group = recipeSet.autoSwitchGroups.get(recipe.autoSwitchGroup);
        if (group == null) {
            return true;
        }
        ItemStack switchInput = assigned.get(0);
        for (GenericRecipe candidate : group) {
            if (candidate == recipe || candidate.getInternalName().equals(recipe.getInternalName())
                    || candidate.inputItem == null || candidate.inputItem.length == 0) {
                continue;
            }
            if (candidate.inputItem[0].matchesRecipe(switchInput, true)) {
                if (AdapterDiagnostics.enabled()) {
                    AdapterDiagnostics.report(
                            tile,
                            "recipe " + recipe.getInternalName() + " is ambiguous with " + candidate.getInternalName()
                                    + " in auto-switch group '" + recipe.autoSwitchGroup
                                    + "': both accept the first input, so the machine would switch away by itself."
                                    + " This recipe cannot be automated through the adapter.");
                }
                return false;
            }
        }
        return true;
    }

    /**
     * True if the input tanks can serve this recipe as they stand. Leftover fluid of another type,
     * or fluid sitting in a tank the recipe does not use, blocks the push.
     */
    protected boolean tanksCompatible(
            TileEntity tile,
            FluidTank[] tanks,
            com.hbm.inventory.FluidStack[] fluids) {
        return tanksCompatible(tile, tanks, fluids, true);
    }

    /**
     * Variant used while a multi-lane driver is probing candidates. A rejected lane must not emit
     * a diagnostic immediately: doing so for all four lanes on every AE2 retry would alternate the
     * dedup key and spam the log. The factory driver reports one aggregate reason after the scan.
     */
    protected boolean tanksCompatible(
            TileEntity tile,
            FluidTank[] tanks,
            com.hbm.inventory.FluidStack[] fluids,
            boolean report) {
        if (fluids == null) {
            if (hasFluids(tanks)) {
                if (report) {
                    AdapterDiagnostics.report(
                            tile,
                            "recipe needs no input fluid but an input tank still holds some. Drain the input tank(s).");
                }
                return false;
            }
            return true;
        }
        if (fluids.length > tanks.length) {
            if (report) {
                AdapterDiagnostics.report(
                        tile,
                        "recipe needs " + fluids.length + " input fluids but this machine has only " + tanks.length
                                + " input tanks");
            }
            return false;
        }
        for (int index = 0; index < tanks.length; index++) {
            if (tanks[index].getFill() <= 0) {
                continue;
            }
            if (index >= fluids.length || fluids[index].pressure != 0
                    || tanks[index].getTankType() != fluids[index].type) {
                if (report && AdapterDiagnostics.enabled()) {
                    AdapterDiagnostics.report(
                            tile,
                            "input tank " + index + " holds " + tanks[index].getFill() + " of "
                                    + (tanks[index].getTankType() == null ? "?"
                                            : tanks[index].getTankType().getName())
                                    + ", which this recipe cannot use (it wants ["
                                    + AdapterDiagnostics.describeHbmFluids(fluids)
                                    + "], matched tank-by-tank in order). Drain that tank.");
                }
                return false;
            }
        }
        return true;
    }

    /**
     * Validates the supplied inputs against the recipe and stages the item half of the transfer,
     * without touching the machine. Returns null (having logged why, under
     * {@code -Dhbmcompat.debugAdapter=true}) when the inputs cannot be placed.
     *
     * <p>Split out of the old single-step {@code pushInputs} so callers can run it <em>before</em>
     * {@code setRecipe}/{@code setupTanks}. Those two mutate the machine, so a later failure used to
     * leave it switched to a recipe whose inputs never arrived — visible in-game as "the recipe
     * changed but nothing is being fed". Everything checked here is a pure comparison against the
     * pattern plus a read of the item slots, neither of which those two calls affect.
     *
     * <p>Fluids are deliberately not staged here: {@code setupTanks} can retype and resize the input
     * tanks, so their capacity is only known afterwards. Add them with {@link #commitInputs} once the
     * machine has been set up. For item-only recipes that step adds nothing, so those pushes are
     * fully validated before any mutation.
     */
    protected MachineInputPlan planInputs(
            TileEntity tile,
            IInventory inventory,
            int[] inputSlots,
            AStack[] expectedItems,
            FluidTank[] inputTanks,
            com.hbm.inventory.FluidStack[] expectedFluids,
            PatternStacks suppliedInputs) {
        List<ItemStack> assignedItems = PatternMatcher.assign(expectedItems, suppliedInputs.getItems());
        if (assignedItems == null) {
            if (AdapterDiagnostics.enabled()) {
                AdapterDiagnostics.report(
                        tile,
                        "input items do not match the recipe: recipe wants ["
                                + AdapterDiagnostics.describeIngredients(expectedItems) + "], AE2 supplied ["
                                + AdapterDiagnostics.describeItems(suppliedInputs.getItems())
                                + "]. Turn off substitution on the pattern (AE2 may hand over an ore-dict stand-in"
                                + " the HBM recipe rejects) and check the pattern amounts equal the recipe exactly.");
            }
            return null;
        }
        if (assignedItems.size() > inputSlots.length) {
            AdapterDiagnostics.report(
                    tile,
                    "recipe needs " + assignedItems.size() + " distinct item inputs but this machine has only "
                            + inputSlots.length + " input slots");
            return null;
        }
        if (!PatternMatcher.matchesHbmFluids(expectedFluids, suppliedInputs.getFluids())) {
            if (AdapterDiagnostics.enabled()) {
                AdapterDiagnostics.report(
                        tile,
                        "input fluids do not match the recipe: recipe wants ["
                                + AdapterDiagnostics.describeHbmFluids(expectedFluids) + "], AE2 supplied ["
                                + AdapterDiagnostics.describeFluids(suppliedInputs.getFluids())
                                + "]. Pressurised recipe fluids are never accepted, and the ME fluid must be the"
                                + " Forge mirror this mod registers for that HBM fluid.");
            }
            return null;
        }

        int fluidCount = expectedFluids == null ? 0 : expectedFluids.length;
        if (fluidCount > inputTanks.length) {
            AdapterDiagnostics.report(
                    tile,
                    "recipe needs " + fluidCount + " input fluids but this machine has only " + inputTanks.length
                            + " input tanks");
            return null;
        }

        MachineInputPlan plan = new MachineInputPlan(inventory);
        for (int index = 0; index < assignedItems.size(); index++) {
            ItemStack addition = assignedItems.get(index);
            if (!plan.addItem(inputSlots[index], addition)) {
                if (AdapterDiagnostics.enabled()) {
                    AdapterDiagnostics.report(
                            tile,
                            "cannot place " + AdapterDiagnostics.describeItems(Collections.singletonList(addition))
                                    + " into input slot " + inputSlots[index]
                                    + ": the slot holds an incompatible item, or the amount exceeds the stack limit"
                                    + " (machine limit " + inventory.getInventoryStackLimit() + ", item limit "
                                    + addition.getMaxStackSize() + ")");
                }
                return null;
            }
        }
        return plan;
    }

    /**
     * Whether calling ModuleMachineBase.setupTanks for {@code expected} can preserve every drop
     * already present. HBM clears a tank when its type/pressure changes and also clears unused
     * tanks, so this check is mandatory before retargeting an idle factory lane.
     */
    protected boolean tanksCanBeRetypedWithoutLoss(
            FluidTank[] tanks,
            com.hbm.inventory.FluidStack[] expected) {
        for (int index = 0; index < tanks.length; index++) {
            FluidTank tank = tanks[index];
            if (tank == null || tank.getFill() <= 0) {
                continue;
            }
            if (expected == null || index >= expected.length || expected[index] == null
                    || tank.getTankType() != expected[index].type
                    || tank.getPressure() != expected[index].pressure) {
                return false;
            }
        }
        return true;
    }

    /**
     * Stages the fluid half of a {@link #planInputs} plan against the now-set-up tanks and writes the
     * whole transfer into the machine. Returns false (having logged why) if a tank cannot take its
     * share, in which case nothing is written.
     */
    protected boolean commitInputs(
            MachineInputPlan plan,
            TileEntity tile,
            FluidTank[] inputTanks,
            com.hbm.inventory.FluidStack[] expectedFluids) {
        int fluidCount = expectedFluids == null ? 0 : expectedFluids.length;
        for (int index = 0; index < fluidCount; index++) {
            if (!plan.addFluid(inputTanks[index], expectedFluids[index])) {
                if (AdapterDiagnostics.enabled()) {
                    FluidTank tank = inputTanks[index];
                    AdapterDiagnostics.report(
                            tile,
                            "cannot fill input tank " + index + " with ["
                                    + AdapterDiagnostics.describeHbmFluids(
                                            new com.hbm.inventory.FluidStack[] { expectedFluids[index] })
                                    + "]: tank holds "
                                    + (tank == null ? "?"
                                            : tank.getFill() + "/" + tank.getMaxFill() + " of "
                                                    + (tank.getTankType() == null ? "?"
                                                            : tank.getTankType().getName()))
                                    + ". Drain leftover fluid of another type, or the batch exceeds tank capacity.");
                }
                return false;
            }
        }
        plan.commit();
        AdapterDiagnostics.reset(tile);
        return true;
    }

    protected boolean hasItems(IInventory inventory, int[] slots) {
        for (int slot : slots) {
            if (inventory.getStackInSlot(slot) != null) {
                return true;
            }
        }
        return false;
    }

    protected boolean hasFluids(FluidTank[] tanks) {
        for (FluidTank tank : tanks) {
            if (tank != null && tank.getFill() > 0) {
                return true;
            }
        }
        return false;
    }

    protected boolean matchSingleOutput(ItemStack output, PatternStacks outputs) {
        return output != null && PatternMatcher.matchesExactItems(new ItemStack[] { output }, outputs.getItems())
                && outputs.getFluids().isEmpty();
    }
}

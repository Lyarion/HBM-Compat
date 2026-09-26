package io.github.hbmcompat.machine;

import io.github.hbmcompat.debug.DiagnosticMessage;

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

    protected ItemStack[] snapshot(IInventory inventory, MachineInputPlan plan, int count) {
        ItemStack[] result = new ItemStack[count];
        for (int i = 0; i < count; i++) {
            ItemStack stack = plan == null ? inventory.getStackInSlot(i) : plan.plannedSlotValue(i);
            result[i] = stack == null ? null : stack.copy();
        }
        return result;
    }

    protected boolean pending(IInventory inventory, com.hbm.module.machine.ModuleMachineBase module) {
        return module.progress > 0 || hasItems(inventory, module.inputSlots) || hasFluids(module.inputTanks);
    }

    /** Stage a whole batch before changing recipe, tank configuration, or inventory. */
    protected MachineInputPlan planModule(TileEntity tile, com.hbm.module.machine.ModuleMachineBase module,
            GenericRecipe recipe, PatternStacks inputs) {
        IInventory inventory = (IInventory) tile;
        boolean active = pending(inventory, module);
        if (active && !recipe.getInternalName().equals(module.getRecipeName())) return null;
        if (!tanksCompatible(tile, module.inputTanks, recipe.inputFluid, false)
                || (!active && !tanksCanBeRetypedWithoutLoss(module.outputTanks, recipe.outputFluid))) return null;
        MachineInputPlan plan = planInputs(tile, inventory, module.inputSlots, recipe.inputItem,
                module.inputTanks, recipe.inputFluid, inputs);
        if (plan == null) return null;
        int count = recipe.inputFluid == null ? 0 : recipe.inputFluid.length;
        for (int i = 0; i < count; i++) {
            FluidTank tank = module.inputTanks[i];
            int capacity = tank.getMaxFill();
            if (!active && module instanceof com.hbm.module.machine.ModuleMachineAssembler) {
                // Mirror HBM's setupTanks, rejecting values whose native int multiplication overflows.
                if (recipe.inputFluid[i].fill > Integer.MAX_VALUE / 2) return null;
                capacity = Math.max(tank.getFill(), Math.max(recipe.inputFluid[i].fill * 2, 4000));
            }
            if (!plan.addFluid(tank, recipe.inputFluid[i], capacity)) return null;
        }
        if (!active && module instanceof com.hbm.module.machine.ModuleMachineAssembler
                && recipe.outputFluid != null) {
            for (com.hbm.inventory.FluidStack fluid : recipe.outputFluid) {
                if (fluid.fill > Integer.MAX_VALUE / 2) return null;
            }
        }
        return plan;
    }

    protected boolean commitModule(TileEntity tile, com.hbm.module.machine.ModuleMachineBase module,
            GenericRecipe recipe, MachineInputPlan plan) {
        if (plan == null) return false;
        if (!pending((IInventory) tile, module)) {
            module.setRecipe(recipe.getInternalName(), false);
            module.setupTanks(recipe);
        }
        plan.commit();
        AdapterDiagnostics.reset(tile);
        return true;
    }

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
                    AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                            "auto_switch_ambiguous", "recipe %s is ambiguous with %s in auto-switch group '%s': both accept the first input, so "
                            + "the machine would switch away by itself. This recipe cannot be automated through the "
                            + "adapter.", recipe.getInternalName(), candidate.getInternalName(), recipe.autoSwitchGroup));
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
                    AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                            "tank_not_empty", "recipe needs no input fluid but an input tank still holds some. Drain the input tank(s)."));
                }
                return false;
            }
            return true;
        }
        if (fluids.length > tanks.length) {
            if (report) {
                AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                        "fluid_slots_short", "recipe needs %s input fluids but this machine has only %s input tanks", fluids.length, tanks.length));
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
                    AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                            "tank_incompatible", "input tank %s holds %s of %s, which this recipe cannot use (it wants [%s], matched tank-by-"
                            + "tank in order). Drain that tank.", index, tanks[index].getFill(), (tanks[index].getTankType() == null ? "?"
                                            : tanks[index].getTankType().getName()), AdapterDiagnostics.describeHbmFluids(fluids)));
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
     * <p>Module callers use {@link #planModule} to stage fluids against projected tank capacity
     * before setup. Native recipe-selection machines use {@link #commitInputs} with fixed tanks.
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
                AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                        "item_mismatch", "input items do not match the recipe: recipe wants [%s], AE2 supplied [%s]. Turn off "
                        + "substitution on the pattern (AE2 may hand over an ore-dict stand-in the HBM recipe rejects) "
                        + "and check the pattern amounts equal the recipe exactly.", AdapterDiagnostics.describeIngredients(expectedItems), AdapterDiagnostics.describeItems(suppliedInputs.getItems())));
            }
            return null;
        }
        if (assignedItems.size() > inputSlots.length) {
            AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                    "item_slots_short", "recipe needs %s distinct item inputs but this machine has only %s input slots", assignedItems.size(), inputSlots.length));
            return null;
        }
        if (!PatternMatcher.matchesHbmFluids(expectedFluids, suppliedInputs.getFluids())) {
            if (AdapterDiagnostics.enabled()) {
                AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                        "fluid_mismatch", "input fluids do not match the recipe: recipe wants [%s], AE2 supplied [%s]. Pressurised "
                        + "recipe fluids are never accepted, and the ME fluid must be the Forge mirror this mod "
                        + "registers for that HBM fluid.", AdapterDiagnostics.describeHbmFluids(expectedFluids), AdapterDiagnostics.describeFluids(suppliedInputs.getFluids())));
            }
            return null;
        }

        int fluidCount = expectedFluids == null ? 0 : expectedFluids.length;
        if (fluidCount > inputTanks.length) {
            AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                    "fluid_slots_short", "recipe needs %s input fluids but this machine has only %s input tanks", fluidCount, inputTanks.length));
            return null;
        }

        MachineInputPlan plan = new MachineInputPlan(inventory);
        // Unused slots must not contain another recipe's leftovers.
        for (int index = assignedItems.size(); index < inputSlots.length; index++) {
            if (inventory.getStackInSlot(inputSlots[index]) != null) return null;
        }
        for (int index = 0; index < assignedItems.size(); index++) {
            ItemStack addition = assignedItems.get(index);
            if (!plan.addItem(inputSlots[index], addition)) {
                if (AdapterDiagnostics.enabled()) {
                    AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                            "item_slot_rejected", "cannot place %s into input slot %s: the slot holds an incompatible item, or the amount "
                            + "exceeds the stack limit (machine limit %s, item limit %s)", AdapterDiagnostics.describeItems(Collections.singletonList(addition)), inputSlots[index], inventory.getInventoryStackLimit(), addition.getMaxStackSize()));
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
                    AdapterDiagnostics.report(tile, DiagnosticMessage.of(
                            "tank_fill_failed", "cannot fill input tank %s with [%s]: tank holds %s. Drain leftover fluid of another type, or"
                            + " the batch exceeds tank capacity.", index, AdapterDiagnostics.describeHbmFluids(
                                            new com.hbm.inventory.FluidStack[] { expectedFluids[index] }), (tank == null ? "?"
                                            : tank.getFill() + "/" + tank.getMaxFill() + " of "
                                                    + (tank.getTankType() == null ? "?"
                                                            : tank.getTankType().getName()))));
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

}

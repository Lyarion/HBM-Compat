package io.github.hbmcompat.machine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import net.minecraft.inventory.InventoryCrafting;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

import appeng.api.networking.crafting.ICraftingPatternDetails;
import appeng.api.storage.data.IAEFluidStack;
import appeng.api.storage.data.IAEItemStack;
import appeng.api.storage.data.IAEStack;
import appeng.util.inv.MEInventoryCrafting;

public final class PatternStacks {

    private final List<ItemStack> items;
    private final List<FluidStack> fluids;
    private final boolean valid;

    private PatternStacks(List<ItemStack> items, List<FluidStack> fluids, boolean valid) {
        this.items = Collections.unmodifiableList(items);
        this.fluids = Collections.unmodifiableList(fluids);
        this.valid = valid;
    }

    public static PatternStacks inputs(ICraftingPatternDetails details) {
        return fromAEStacks(details == null ? null : details.getAEInputs());
    }

    public static PatternStacks outputs(ICraftingPatternDetails details) {
        return fromAEStacks(details == null ? null : details.getAEOutputs());
    }

    public static PatternStacks fromInventory(InventoryCrafting inventory) {
        if (!(inventory instanceof MEInventoryCrafting)) {
            return new PatternStacks(new ArrayList<ItemStack>(), new ArrayList<FluidStack>(), false);
        }

        MEInventoryCrafting meInventory = (MEInventoryCrafting) inventory;
        IAEStack<?>[] stacks = new IAEStack<?>[inventory.getSizeInventory()];
        for (int slot = 0; slot < stacks.length; slot++) {
            stacks[slot] = meInventory.getAEStackInSlot(slot);
        }
        return fromAEStacks(stacks);
    }

    private static PatternStacks fromAEStacks(IAEStack<?>[] stacks) {
        List<ItemStack> items = new ArrayList<ItemStack>();
        List<FluidStack> fluids = new ArrayList<FluidStack>();
        boolean valid = true;

        if (stacks != null) {
            for (IAEStack<?> stack : stacks) {
                if (stack == null || stack.getStackSize() <= 0) {
                    continue;
                }
                if (stack.getStackSize() > Integer.MAX_VALUE) {
                    valid = false;
                    continue;
                }

                if (stack instanceof IAEItemStack) {
                    ItemStack item = ((IAEItemStack) stack).getItemStack();
                    if (item == null || item.getItem() == null) {
                        valid = false;
                        continue;
                    }
                    ItemStack copy = item.copy();
                    copy.stackSize = (int) stack.getStackSize();
                    items.add(copy);
                } else if (stack instanceof IAEFluidStack) {
                    FluidStack fluid = ((IAEFluidStack) stack).getFluidStack();
                    if (fluid == null || fluid.getFluid() == null) {
                        valid = false;
                        continue;
                    }
                    FluidStack copy = fluid.copy();
                    copy.amount = (int) stack.getStackSize();
                    fluids.add(copy);
                } else {
                    valid = false;
                }
            }
        }

        return new PatternStacks(items, fluids, valid);
    }

    public List<ItemStack> getItems() {
        return items;
    }

    public List<FluidStack> getFluids() {
        return fluids;
    }

    public boolean isValid() {
        return valid;
    }
}

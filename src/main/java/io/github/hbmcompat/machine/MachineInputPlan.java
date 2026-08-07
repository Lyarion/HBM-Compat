package io.github.hbmcompat.machine;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

import com.hbm.inventory.fluid.Fluids;
import com.hbm.inventory.fluid.tank.FluidTank;

final class MachineInputPlan {

    private final IInventory inventory;
    private final List<Integer> slots = new ArrayList<Integer>();
    private final List<ItemStack> slotValues = new ArrayList<ItemStack>();
    private final List<FluidTank> tanks = new ArrayList<FluidTank>();
    private final List<com.hbm.inventory.FluidStack> tankValues = new ArrayList<com.hbm.inventory.FluidStack>();

    MachineInputPlan(IInventory inventory) {
        this.inventory = inventory;
    }

    boolean addItem(int slot, ItemStack addition) {
        if (addition == null || addition.stackSize <= 0) {
            return false;
        }

        ItemStack current = plannedSlotValue(slot);
        ItemStack merged;
        if (current == null) {
            merged = addition.copy();
        } else {
            if (!current.isItemEqual(addition) || !ItemStack.areItemStackTagsEqual(current, addition)) {
                return false;
            }
            merged = current.copy();
            merged.stackSize += addition.stackSize;
        }

        int limit = Math.min(inventory.getInventoryStackLimit(), merged.getMaxStackSize());
        if (merged.stackSize > limit) {
            return false;
        }

        int index = slots.indexOf(Integer.valueOf(slot));
        if (index >= 0) {
            slotValues.set(index, merged);
        } else {
            slots.add(Integer.valueOf(slot));
            slotValues.add(merged);
        }
        return true;
    }

    boolean addFluid(FluidTank tank, com.hbm.inventory.FluidStack addition) {
        if (tank == null || addition == null || addition.type == null || addition.type == Fluids.NONE
                || addition.fill <= 0 || addition.pressure != 0 || tank.getPressure() != 0) {
            return false;
        }

        int index = tanks.indexOf(tank);
        int currentFill = index >= 0 ? tankValues.get(index).fill : tank.getFill();
        com.hbm.inventory.fluid.FluidType currentType = index >= 0 ? tankValues.get(index).type : tank.getTankType();
        if (currentType != Fluids.NONE && currentType != addition.type && currentFill > 0) {
            return false;
        }
        if (currentFill + addition.fill > tank.getMaxFill()) {
            return false;
        }

        com.hbm.inventory.FluidStack next = new com.hbm.inventory.FluidStack(addition.type, currentFill + addition.fill);
        if (index >= 0) {
            tankValues.set(index, next);
        } else {
            tanks.add(tank);
            tankValues.add(next);
        }
        return true;
    }

    void commit() {
        for (int index = 0; index < slots.size(); index++) {
            inventory.setInventorySlotContents(slots.get(index).intValue(), slotValues.get(index));
        }
        for (int index = 0; index < tanks.size(); index++) {
            FluidTank tank = tanks.get(index);
            com.hbm.inventory.FluidStack value = tankValues.get(index);
            tank.setTankType(value.type);
            tank.setFill(value.fill);
        }
        inventory.markDirty();
    }

    private ItemStack plannedSlotValue(int slot) {
        int index = slots.indexOf(Integer.valueOf(slot));
        if (index >= 0) {
            return slotValues.get(index);
        }
        ItemStack current = inventory.getStackInSlot(slot);
        return current == null ? null : current.copy();
    }
}

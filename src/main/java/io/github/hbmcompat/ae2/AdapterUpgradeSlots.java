package io.github.hbmcompat.ae2;

import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.container.implementations.ContainerInterface;
import appeng.container.slot.SlotRestrictedInput;
import io.github.hbmcompat.content.ItemAutoExtractCard;

/** Identical slot order and backing inventory on both sides; only acceptance changes. */
public final class AdapterUpgradeSlots {
    private AdapterUpgradeSlots() {}

    public static void install(ContainerInterface container, TileHbmAdapter tile) {
        IInventory upgrades = tile.getInventoryByName("upgrades");
        for (int i = 0; i < container.inventorySlots.size(); i++) {
            Slot old = (Slot) container.inventorySlots.get(i);
            if (old.inventory != upgrades) continue;
            SlotRestrictedInput slot = new SlotRestrictedInput(SlotRestrictedInput.PlacableItemType.UPGRADES,
                    upgrades, old.getSlotIndex(), old.xDisplayPosition, old.yDisplayPosition,
                    container.getInventoryPlayer()) {
                @Override public boolean isItemValid(ItemStack stack) {
                    if (!ItemAutoExtractCard.isCard(stack)) return super.isItemValid(stack);
                    return isEnabled() && canTakeStack(container.getInventoryPlayer().player)
                            && container.isValidForSlot(this, stack)
                            && !ItemAutoExtractCard.isInstalled(inventory);
                }
            };
            slot.setStackLimit(1);
            slot.setNotDraggable();
            slot.setContainer(container);
            slot.slotNumber = old.slotNumber;
            container.inventorySlots.set(i, slot);
        }
    }
}

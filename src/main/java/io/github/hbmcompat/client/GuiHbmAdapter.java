package io.github.hbmcompat.client;

import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Slot;
import net.minecraft.item.ItemStack;

import appeng.api.AEApi;
import appeng.core.localization.GuiText;
import appeng.client.gui.implementations.GuiInterface;
import appeng.container.implementations.ContainerInterface;
import io.github.hbmcompat.ae2.AdapterUpgradeSlots;
import io.github.hbmcompat.ae2.TileHbmAdapter;
import io.github.hbmcompat.content.ModContent;

public final class GuiHbmAdapter extends GuiInterface {
    private final TileHbmAdapter adapter;

    public GuiHbmAdapter(InventoryPlayer player, TileHbmAdapter tile) {
        super(player, tile);
        this.adapter = tile;
        AdapterUpgradeSlots.install((ContainerInterface) inventorySlots, tile);
    }

    @Override
    protected void handleUpgradeSlotTooltip(int mouseX, int mouseY) {
        Slot slot = getSlot(mouseX, mouseY);
        if (slot == null || slot.getHasStack() || slot.inventory != adapter.getInventoryByName("upgrades")) return;
        ItemStack capacity = AEApi.instance().definitions().materials().cardPatternCapacity().maybeStack(1).orNull();
        if (capacity == null) {
            drawTooltip(mouseX, mouseY, new String[] { GuiText.Accepts.getLocal(),
                    new ItemStack(ModContent.autoExtractCard).getDisplayName() + " (1)" });
        } else {
            drawTooltip(mouseX, mouseY, new String[] { GuiText.Accepts.getLocal(), capacity.getDisplayName() + " (3)",
                    new ItemStack(ModContent.autoExtractCard).getDisplayName() + " (1)" });
        }
    }
}

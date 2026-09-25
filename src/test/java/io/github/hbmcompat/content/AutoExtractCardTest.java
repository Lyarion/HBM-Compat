package io.github.hbmcompat.content;

import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntityChest;

import org.junit.Test;
import static org.junit.Assert.*;

public class AutoExtractCardTest {
    @Test public void installationAndRemovalUseActualCardIdentity() {
        TileEntityChest inventory = new TileEntityChest();
        assertFalse(ItemAutoExtractCard.isInstalled(null));
        assertFalse(ItemAutoExtractCard.isInstalled(inventory));
        inventory.setInventorySlotContents(0, new ItemStack(new Item()));
        assertFalse(ItemAutoExtractCard.isInstalled(inventory));
        inventory.setInventorySlotContents(3, new ItemStack(new ItemAutoExtractCard()));
        assertTrue(ItemAutoExtractCard.isInstalled(inventory));
        inventory.setInventorySlotContents(3, null);
        assertFalse(ItemAutoExtractCard.isInstalled(inventory));
    }
}

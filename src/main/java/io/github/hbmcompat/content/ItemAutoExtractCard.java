package io.github.hbmcompat.content;

import java.util.List;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.inventory.IInventory;
import net.minecraft.util.StatCollector;

/** An adapter-local upgrade; deliberately does not impersonate an AE2 upgrade enum. */
public final class ItemAutoExtractCard extends Item {
    public static final String REGISTRY_NAME = "auto_extract_card";

    public ItemAutoExtractCard() {
        setUnlocalizedName(REGISTRY_NAME);
        setTextureName("hbmcompat:auto_extract_card");
    }

    public static boolean isCard(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemAutoExtractCard;
    }

    public static boolean isInstalled(IInventory inventory) {
        if (inventory == null) return false;
        for (int i = 0; i < inventory.getSizeInventory(); i++) {
            if (isCard(inventory.getStackInSlot(i))) return true;
        }
        return false;
    }

    @Override
    @SuppressWarnings({ "rawtypes", "unchecked" })
    public void addInformation(ItemStack stack, EntityPlayer player, List lines, boolean advanced) {
        lines.add(StatCollector.translateToLocal("hbmcompat.auto_extract.description"));
        lines.add(StatCollector.translateToLocal("hbmcompat.auto_extract.limit"));
    }
}

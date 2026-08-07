package io.github.hbmcompat.client;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.hbmcompat.pattern.HbmPatternMetadata;

public final class HbmPatternTooltipHandler {

    @SubscribeEvent
    public void onTooltip(ItemTooltipEvent event) {
        if (event.itemStack == null || !event.itemStack.hasTagCompound()) {
            return;
        }
        NBTTagCompound tag = event.itemStack.getTagCompound();
        if (!tag.hasKey(HbmPatternMetadata.MACHINE_KEY) || !tag.hasKey(HbmPatternMetadata.RECIPE_KEY)) {
            return;
        }
        event.toolTip.add(
                EnumChatFormatting.AQUA
                        + StatCollector.translateToLocalFormatted(
                                "tooltip.hbmcompat.hbm_machine",
                                tag.getString(HbmPatternMetadata.MACHINE_KEY)));
        event.toolTip.add(
                EnumChatFormatting.GRAY
                        + StatCollector.translateToLocalFormatted(
                                "tooltip.hbmcompat.hbm_recipe",
                                tag.getString(HbmPatternMetadata.RECIPE_KEY)));
    }
}

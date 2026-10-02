package io.github.hbmcompat.debug;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;

/** Uses vanilla server chat packets; no client-only classes or custom protocol. */
public final class DebugChat {
    public static final DebugSubscriptions SUBSCRIPTIONS = new DebugSubscriptions();

    public static String position(TileEntity tile) {
        return tile == null ? "?" : tile.xCoord + "," + tile.yCoord + "," + tile.zCoord;
    }

    public static String sourceKey(TileEntity tile, String source) {
        return source + "@" + position(tile);
    }

    public static void report(TileEntity tile, String source, String label, DiagnosticMessage message) {
        if (!SUBSCRIPTIONS.active() || tile == null || tile.getWorldObj() == null
                || tile.getWorldObj().isRemote) return;
        DebugFilter category = "push".equals(label) ? DebugFilter.ADAPTER : DebugFilter.BUS;
        if (!SUBSCRIPTIONS.active(category)) return;
        String reason = message.toString();
        int dimension = tile.getWorldObj().provider.dimensionId;
        for (Object entry : tile.getWorldObj().playerEntities) {
            EntityPlayer player = (EntityPlayer) entry;
            if (player instanceof EntityPlayerMP && SUBSCRIPTIONS.shouldSend(player.getUniqueID(),
                    player.dimension, dimension, category, sourceKey(tile, source), reason)) {
                player.addChatMessage(format(label, dimension, position(tile), message));
            }
        }
    }

    public static ChatComponentTranslation format(String label, int dimension, String position,
            DiagnosticMessage message) {
        ChatComponentTranslation device = new ChatComponentTranslation("hbmcompat.debug.source." + label);
        device.getChatStyle().setColor("push".equals(label) ? EnumChatFormatting.GOLD
                : "bus:fluid-import".equals(label) ? EnumChatFormatting.BLUE : EnumChatFormatting.LIGHT_PURPLE);
        device.getChatStyle().setBold(true);
        ChatComponentText dimensionText = new ChatComponentText(Integer.toString(dimension));
        dimensionText.getChatStyle().setColor(EnumChatFormatting.DARK_AQUA);
        ChatComponentText coordinates = new ChatComponentText(position);
        coordinates.getChatStyle().setColor(EnumChatFormatting.AQUA);
        ChatComponentTranslation line = new ChatComponentTranslation("hbmcompat.debug.line",
                device, dimensionText, coordinates, message.toChat());
        line.getChatStyle().setColor(EnumChatFormatting.GRAY);
        return line;
    }

    public static void reset(TileEntity tile, String source) {
        if (tile != null && tile.getWorldObj() != null && !tile.getWorldObj().isRemote) {
            SUBSCRIPTIONS.reset(tile.getWorldObj().provider.dimensionId, sourceKey(tile, source));
        }
    }

    @SubscribeEvent
    public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!event.player.worldObj.isRemote) SUBSCRIPTIONS.remove(event.player.getUniqueID());
    }
}

package io.github.hbmcompat.debug;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
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
        String reason = message.toString();
        int dimension = tile.getWorldObj().provider.dimensionId;
        for (Object entry : tile.getWorldObj().playerEntities) {
            EntityPlayer player = (EntityPlayer) entry;
            if (player instanceof EntityPlayerMP && SUBSCRIPTIONS.shouldSend(player.getUniqueID(),
                    player.dimension, dimension, sourceKey(tile, source), reason)) {
                player.addChatMessage(new ChatComponentTranslation("hbmcompat.debug.line",
                        new ChatComponentTranslation("hbmcompat.debug.source." + label),
                        dimension, position(tile), message.toChat()));
            }
        }
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

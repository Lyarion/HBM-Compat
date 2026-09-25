package io.github.hbmcompat.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.container.ContainerOpenContext;
import appeng.core.sync.GuiBridge;
import cpw.mods.fml.common.network.IGuiHandler;
import io.github.hbmcompat.HbmCompat;

public final class AdapterGuiHandler implements IGuiHandler {
    private static final int ADAPTER = 0;

    public static void open(EntityPlayer player, TileHbmAdapter tile) {
        player.openGui(HbmCompat.instance, ADAPTER, tile.getWorldObj(), tile.xCoord, tile.yCoord, tile.zCoord);
    }

    private TileHbmAdapter tile(int id, World world, int x, int y, int z) {
        if (id != ADAPTER || !world.blockExists(x, y, z)) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        return tile instanceof TileHbmAdapter ? (TileHbmAdapter) tile : null;
    }

    @Override public Object getServerGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileHbmAdapter tile = tile(id, world, x, y, z);
        if (tile == null || !GuiBridge.GUI_INTERFACE.hasPermissions(
                tile, x, y, z, ForgeDirection.UNKNOWN, player)) return null;
        ContainerHbmAdapter container = new ContainerHbmAdapter(player.inventory, tile);
        ContainerOpenContext context = new ContainerOpenContext(tile);
        context.setWorld(world);
        context.setX(x); context.setY(y); context.setZ(z);
        context.setSide(ForgeDirection.UNKNOWN);
        container.setOpenContext(context);
        return container;
    }

    @Override public Object getClientGuiElement(int id, EntityPlayer player, World world, int x, int y, int z) {
        TileHbmAdapter tile = tile(id, world, x, y, z);
        return tile == null ? null : HbmCompat.proxy.createAdapterGui(player.inventory, tile);
    }
}

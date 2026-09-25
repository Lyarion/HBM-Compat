package io.github.hbmcompat.content;

import java.util.EnumSet;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.api.util.IOrientable;
import appeng.block.AEBaseTileBlock;
import appeng.core.features.AEFeature;
import appeng.core.features.ActivityState;
import appeng.core.features.BlockStackSrc;
import appeng.tile.AEBaseTile;
import appeng.util.Platform;
import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.ae2.AdapterGuiHandler;
import io.github.hbmcompat.ae2.TileHbmAdapter;

public final class BlockHbmProcessingAdapter extends AEBaseTileBlock {

    public static final String REGISTRY_NAME = "me_hbm_processing_adapter";

    public BlockHbmProcessingAdapter() {
        super(Material.iron);
        setBlockName(REGISTRY_NAME);
        setBlockTextureName(HbmCompat.MODID + ":hbm_adapter");
        isOpaque = true;
        isFullSize = true;
        setTileEntity(TileHbmAdapter.class);
        setFeature(EnumSet.of(AEFeature.Core));
    }

    @Override
    public void setTileEntity(Class<? extends TileEntity> tileClass) {
        AEBaseTile.registerTileItem(tileClass, new BlockStackSrc(this, 0, ActivityState.Enabled));
        super.setTileEntity(tileClass);
    }

    @Override
    public boolean onActivated(
            World world,
            int x,
            int y,
            int z,
            EntityPlayer player,
            int facing,
            float hitX,
            float hitY,
            float hitZ) {
        if (player.isSneaking()) {
            return false;
        }
        TileHbmAdapter tile = getTileEntity(world, x, y, z);
        if (tile == null) {
            return false;
        }
        if (Platform.isServer()) {
            AdapterGuiHandler.open(player, tile);
        }
        return true;
    }

    @Override
    public void onNeighborBlockChange(World world, int x, int y, int z, Block neighbor) {
        TileHbmAdapter tile = getTileEntity(world, x, y, z);
        if (tile != null) {
            tile.getInterfaceDuality().updateRedstoneState();
        }
    }

    @Override
    protected boolean hasCustomRotation() {
        return true;
    }

    @Override
    protected void customRotateBlock(IOrientable rotatable, ForgeDirection axis) {
        if (rotatable instanceof TileHbmAdapter) {
            ((TileHbmAdapter) rotatable).setSide(axis);
        }
    }
}

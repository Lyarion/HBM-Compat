package io.github.hbmcompat.client;

import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.IIcon;
import net.minecraft.world.IBlockAccess;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.client.render.BaseBlockRender;
import appeng.client.texture.ExtraBlockTextures;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.github.hbmcompat.ae2.TileHbmAdapter;
import io.github.hbmcompat.content.BlockHbmProcessingAdapter;

@SideOnly(Side.CLIENT)
public final class RenderHbmAdapter extends BaseBlockRender<BlockHbmProcessingAdapter, TileHbmAdapter> {

    public RenderHbmAdapter() {
        super(false, 20);
    }

    @Override
    public boolean renderInWorld(BlockHbmProcessingAdapter block, IBlockAccess world, int x, int y, int z,
            RenderBlocks renderer) {
        boolean rendered = super.renderInWorld(block, world, x, y, z, renderer);
        TileHbmAdapter tile = block.getTileEntity(world, x, y, z);
        // Use the synchronized orientation: pointAt is only maintained on the server.
        if (tile == null || tile.getUp() == ForgeDirection.UNKNOWN || renderer.hasOverrideBlockTexture()) {
            return rendered;
        }
        ForgeDirection feed = tile.getUp().getOpposite();
        IIcon white = ExtraBlockTextures.White.getIcon();
        Tessellator tess = Tessellator.instance;
        for (ForgeDirection face : ForgeDirection.VALID_DIRECTIONS) {
            if (face == feed || face == feed.getOpposite()) continue;
            if (!renderer.renderAllFaces && !block.shouldSideBeRendered(world,
                    x + face.offsetX, y + face.offsetY, z + face.offsetZ, face.ordinal())) continue;
            tess.setBrightness(block.getMixedBrightnessForBlock(world,
                    x + face.offsetX, y + face.offsetY, z + face.offsetZ));
            float shade = face.offsetY != 0 ? (face.offsetY > 0 ? 1.0f : 0.5f)
                    : (face.offsetX != 0 ? 0.6f : 0.8f);
            // Brass pixel arrow, matching the adapter trim; all six base textures stay intact.
            tess.setColorOpaque_F(0.76f * shade, 0.62f * shade, 0.32f * shade);
            strip(tess, white, x, y, z, face, feed, 1, -3, 0);
            strip(tess, white, x, y, z, face, feed, 3, 0, 1);
            strip(tess, white, x, y, z, face, feed, 2, 1, 2);
            strip(tess, white, x, y, z, face, feed, 1, 2, 3);
        }
        return rendered;
    }

    private static void strip(Tessellator tess, IIcon icon, int x, int y, int z,
            ForgeDirection face, ForgeDirection feed, int halfWidth, int start, int end) {
        // right = feed cross face, so right cross feed points out of the face.
        int rx = feed.offsetY * face.offsetZ - feed.offsetZ * face.offsetY;
        int ry = feed.offsetZ * face.offsetX - feed.offsetX * face.offsetZ;
        int rz = feed.offsetX * face.offsetY - feed.offsetY * face.offsetX;
        vertex(tess, icon, x, y, z, face, feed, rx, ry, rz, -halfWidth, start);
        vertex(tess, icon, x, y, z, face, feed, rx, ry, rz, halfWidth, start);
        vertex(tess, icon, x, y, z, face, feed, rx, ry, rz, halfWidth, end);
        vertex(tess, icon, x, y, z, face, feed, rx, ry, rz, -halfWidth, end);
    }

    private static void vertex(Tessellator tess, IIcon icon, int x, int y, int z,
            ForgeDirection face, ForgeDirection feed, int rx, int ry, int rz, int across, int along) {
        // Offset avoids z-fighting, and 1/16 steps follow the original pixel grid.
        tess.addVertexWithUV(x + 0.5 + face.offsetX * 0.502 + (rx * across + feed.offsetX * along) / 16.0,
                y + 0.5 + face.offsetY * 0.502 + (ry * across + feed.offsetY * along) / 16.0,
                z + 0.5 + face.offsetZ * 0.502 + (rz * across + feed.offsetZ * along) / 16.0,
                icon.getInterpolatedU(8), icon.getInterpolatedV(8));
    }
}

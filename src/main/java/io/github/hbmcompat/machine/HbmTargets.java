package io.github.hbmcompat.machine;

import net.minecraft.block.Block;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

import com.hbm.blocks.BlockDummyable;

/**
 * Resolves the real machine tile behind an HBM multiblock face.
 *
 * <p>All four v1 target machines (assembly machine, chemical plant, arc welder,
 * soldering station) are {@link BlockDummyable} multiblocks: the block an adjacent
 * device actually touches is almost always a dummy segment, whose tile is a bare
 * {@code TileEntityDummy} rather than the {@code TileEntityMachine*} core. Reading
 * {@code world.getTileEntity(x, y, z)} at the neighbour position therefore misses the
 * driver-bearing core and every {@code instanceof} check silently fails.
 *
 * <p>{@link BlockDummyable#findCore} walks the segment chain (opposite the stored
 * orientation metadata) back to the core and returns its coordinates; called on the
 * core itself it returns immediately. Routing every neighbour lookup through it fixes
 * the adapter and both fluid buses with one shared code path.
 */
public final class HbmTargets {

    private HbmTargets() {}

    /**
     * Returns the tile at (x, y, z), or — when that position is part of an HBM
     * {@link BlockDummyable} multiblock — the multiblock's core tile. Returns null if
     * the world/position is unloaded or the core cannot be resolved.
     */
    public static TileEntity resolveCore(World world, int x, int y, int z) {
        if (world == null || !world.blockExists(x, y, z)) {
            return null;
        }

        Block block = world.getBlock(x, y, z);
        if (block instanceof BlockDummyable) {
            int[] core = ((BlockDummyable) block).findCore(world, x, y, z);
            if (core == null) {
                return null;
            }
            return world.getTileEntity(core[0], core[1], core[2]);
        }

        return world.getTileEntity(x, y, z);
    }
}

package io.github.hbmcompat.part;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.tileentity.machine.TileEntityChimneyBase;
import com.hbm.tileentity.machine.TileEntityChimneyBrick;
import com.hbm.tileentity.machine.TileEntityChimneyIndustrial;

/** Direct consumption by HBM smokestacks, which expose no receiving tanks. */
final class ChimneyFluidSink {

    private ChimneyFluidSink() {}

    static boolean supports(TileEntity target) {
        return target instanceof TileEntityChimneyBrick || target instanceof TileEntityChimneyIndustrial;
    }

    // The bus host is two blocks from the core, facing a base-side proxy port.
    static boolean isPort(TileEntity target, int x, int y, int z, ForgeDirection facing) {
        return supports(target) && facing != null && facing.offsetY == 0
                && facing != ForgeDirection.UNKNOWN && y == target.yCoord
                && x + facing.offsetX * 2 == target.xCoord
                && z + facing.offsetZ * 2 == target.zCoord;
    }

    static long limit(TileEntity target, FluidType type, ForgeDirection facing, long budget) {
        if (!supports(target) || facing == null || !((TileEntityChimneyBase) target)
                .canConnect(type, facing.getOpposite())) return 0;
        TileEntityChimneyBase chimney = (TileEntityChimneyBase) target;
        return Math.max(0, Math.min(Integer.MAX_VALUE, Math.min(budget,
                Math.min(chimney.getDemand(type, 0), chimney.getReceiverSpeed(type, 0)))));
    }
}

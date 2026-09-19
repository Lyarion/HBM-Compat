package io.github.hbmcompat.client;

import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;

import codechicken.nei.api.API;
import io.github.hbmcompat.CompatConfig;

/** Optional cosmetic filtering, independent of fluid registration and compatibility. */
public final class BobFluidDisplayCompat {
    private BobFluidDisplayCompat() {}

    public static void hideFluidBlocks() {
        if (!CompatConfig.hideBobFluidBlocks) return;
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            Block block = fluid.getBlock();
            if (!(block instanceof IFluidBlock)) continue;
            String name = String.valueOf(Block.blockRegistry.getNameForObject(block));
            if (name.startsWith("bobfluidtranslator:") && name.endsWith("_block")) {
                ItemStack item = new ItemStack(block);
                if (item.getItem() != null) API.hideItem(item);
            }
        }
    }
}

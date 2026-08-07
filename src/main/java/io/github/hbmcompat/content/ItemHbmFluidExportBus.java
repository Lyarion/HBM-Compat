package io.github.hbmcompat.content;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.glodblock.github.common.item.ItemFluidExportBus;

import io.github.hbmcompat.part.PartHbmFluidExportBus;

public final class ItemHbmFluidExportBus extends ItemFluidExportBus {

    public static final String REGISTRY_NAME = "hbm_fluid_export_bus";

    public ItemHbmFluidExportBus() {
        setUnlocalizedName(REGISTRY_NAME);
    }

    @Nullable
    @Override
    public PartHbmFluidExportBus createPartFromItemStack(ItemStack stack) {
        return new PartHbmFluidExportBus(stack);
    }
}

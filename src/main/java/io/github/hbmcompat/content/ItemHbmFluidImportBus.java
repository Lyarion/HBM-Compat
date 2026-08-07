package io.github.hbmcompat.content;

import javax.annotation.Nullable;

import net.minecraft.item.ItemStack;

import com.glodblock.github.common.item.ItemFluidImportBus;

import io.github.hbmcompat.part.PartHbmFluidImportBus;

public final class ItemHbmFluidImportBus extends ItemFluidImportBus {

    public static final String REGISTRY_NAME = "hbm_fluid_import_bus";

    public ItemHbmFluidImportBus() {
        setUnlocalizedName(REGISTRY_NAME);
    }

    @Nullable
    @Override
    public PartHbmFluidImportBus createPartFromItemStack(ItemStack stack) {
        return new PartHbmFluidImportBus(stack);
    }
}

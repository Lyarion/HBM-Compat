package io.github.hbmcompat.client;

import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;

/**
 * Stitches an {@link HbmFluidSprite} onto the block atlas for every fluid this
 * mod registers, so each Forge fluid renders with HBM's own GUI swatch
 * ({@code hbm:textures/gui/fluids/<name>.png}) and tint — the same art HBM uses
 * in-game. Nothing is copied into this mod's assets; the sprite reads HBM's PNG
 * from HBM's jar at load time (see {@link HbmFluidSprite}).
 */
@SideOnly(Side.CLIENT)
public final class HbmFluidTextureHandler {

    private static final int BLOCK_ATLAS = 0;

    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        if (event.map.getTextureType() != BLOCK_ATLAS) {
            return;
        }

        CompatTextures.registerAll(event.map);

        for (String forgeName : HbmForgeFluidRegistry.getOurFluids()) {
            Fluid fluid = FluidRegistry.getFluid(forgeName);
            if (fluid == null) {
                continue;
            }

            FluidType hbm = HbmForgeFluidRegistry.getHbmFluid(fluid);
            ResourceLocation source = (hbm != null && hbm != Fluids.NONE) ? hbm.getTexture() : null;
            int tint = hbm != null ? hbm.getTint() : 0xffffff;
            int color = hbm != null ? hbm.getColor() : 0xffffff;

            String iconName = HbmCompat.MODID + ":fluids/" + forgeName;
            stitchFluid(event.map, fluid, iconName, source, tint, color);
        }
    }

    /**
     * Builds an {@link HbmFluidSprite}, registers it under {@code iconName}, and
     * points the fluid's still + flow icons at it (a static swatch needs no
     * separate flow animation). If the atlas already holds that name (e.g. a
     * duplicate reload), reuses the existing entry.
     */
    private static void stitchFluid(TextureMap map, Fluid fluid, String iconName,
            ResourceLocation source, int tint, int color) {
        HbmFluidSprite sprite = new HbmFluidSprite(iconName, source, tint, color);
        TextureAtlasSprite entry = map.setTextureEntry(iconName, sprite) ? sprite
                : map.getTextureExtry(iconName);
        if (entry != null) {
            fluid.setIcons(entry);
        }
    }
}

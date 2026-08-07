package io.github.hbmcompat.client;

import java.awt.image.BufferedImage;
import java.io.IOException;

import javax.imageio.ImageIO;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.IResource;
import net.minecraft.client.resources.IResourceManager;
import net.minecraft.util.ResourceLocation;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * A block-atlas sprite that loads HBM's own fluid GUI swatch
 * ({@code hbm:textures/gui/fluids/<name>.png}) at stitch time and bakes in
 * HBM's {@code guiTint}, reproducing exactly how HBM renders that fluid.
 *
 * <p>Nothing is copied into this mod's assets: the PNG is read straight from
 * HBM's jar through the resource manager, so we redistribute none of HBM's art
 * (HBM is GPLv3 — this keeps us clean of any redistribution obligation while
 * still honouring the request to reuse HBM's textures). If the source texture
 * is missing, a solid swatch is synthesized from the fluid's own colour so the
 * fluid still renders sensibly instead of showing the missing-texture checker.
 *
 * <p>Modelled on HBM's {@code com.hbm.render.icon.TextureAtlasSpriteMutatable},
 * which proves this custom-loader pattern works on this exact MC/Forge version.
 */
@SideOnly(Side.CLIENT)
public final class HbmFluidSprite extends TextureAtlasSprite {

    private static final int SIZE = 16;

    /** HBM's fluid swatch, e.g. {@code hbm:textures/gui/fluids/diesel.png}; may be null. */
    private final ResourceLocation source;
    /** HBM's {@code guiTint} (0xRRGGBB); {@code 0xffffff} is identity (no tint). */
    private final int tint;
    /** Fallback fluid colour (0xRRGGBB) used to synthesize a swatch when {@link #source} is absent. */
    private final int fallbackColor;

    HbmFluidSprite(String iconName, ResourceLocation source, int tint, int fallbackColor) {
        super(iconName);
        this.source = source;
        this.tint = tint;
        this.fallbackColor = fallbackColor;
    }

    @Override
    public boolean hasCustomLoader(IResourceManager manager, ResourceLocation location) {
        return true;
    }

    /**
     * @return {@code false} — we populated the sprite ourselves, so vanilla must
     *         NOT try to load it again (and must still stitch it).
     */
    @Override
    public boolean load(IResourceManager manager, ResourceLocation location) {
        BufferedImage image = null;

        if (source != null) {
            try {
                IResource resource = manager.getResource(source);
                image = ImageIO.read(resource.getInputStream());
            } catch (IOException e) {
                // HBM ships no dedicated swatch for this fluid (many custom/foreign
                // fluids point at a shared *_base texture); fall back to a colour swatch.
                image = null;
            } catch (RuntimeException e) {
                image = null;
            }
        }

        if (image == null) {
            image = solidSwatch(fallbackColor);
        } else {
            applyTint(image, tint);
        }

        // The array passed to loadSprite is indexed by MIPMAP LEVEL, not by animation
        // frame: loadSprite sizes framesTextureData from images.length, and TextureMap
        // later calls generateMipmaps(mipmapLevels), which indexes levels 1..mipmapLevels
        // unconditionally. A one-element array therefore crashes with
        // ArrayIndexOutOfBoundsException: 1 as soon as mipmapping is enabled.
        // Allocate 1 + mipmapLevels and leave the higher levels null — vanilla
        // downsamples level 0 to fill them. Same allocation TextureMap and HBM's
        // TextureAtlasSpriteMutatable use.
        BufferedImage[] levels = new BufferedImage[1 + mipmapLevels()];
        levels[0] = image;

        // Anisotropic filtering must match the atlas: when it is on, loadSprite pads
        // every sprite by 16px, and a sprite that opted out would stitch misaligned.
        loadSprite(levels, null, anisotropicFiltering() > 1.0F);
        return false;
    }

    /** The block atlas' mipmap level, clamped defensively — a negative value would size the array to 0. */
    private static int mipmapLevels() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? Math.max(0, mc.gameSettings.mipmapLevels) : 0;
    }

    /** The block atlas' anisotropic filtering level ({@code 1} means off). */
    private static float anisotropicFiltering() {
        Minecraft mc = Minecraft.getMinecraft();
        return mc != null ? mc.gameSettings.anisotropicFiltering : 1;
    }

    /** Multiplies every pixel's RGB by {@code tint} (0xRRGGBB), leaving alpha intact. */
    private static void applyTint(BufferedImage image, int tint) {
        if ((tint & 0xffffff) == 0xffffff) {
            return; // identity tint, nothing to do
        }
        int tr = (tint >> 16) & 0xff;
        int tg = (tint >> 8) & 0xff;
        int tb = tint & 0xff;

        int w = image.getWidth();
        int h = image.getHeight();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = image.getRGB(x, y);
                int a = (argb >> 24) & 0xff;
                int r = ((argb >> 16) & 0xff) * tr / 255;
                int g = ((argb >> 8) & 0xff) * tg / 255;
                int b = (argb & 0xff) * tb / 255;
                image.setRGB(x, y, (a << 24) | (r << 16) | (g << 8) | b);
            }
        }
    }

    /** A fully-opaque {@value #SIZE}×{@value #SIZE} swatch of {@code color} (0xRRGGBB). */
    private static BufferedImage solidSwatch(int color) {
        BufferedImage image = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        int argb = 0xff000000 | (color & 0xffffff);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                image.setRGB(x, y, argb);
            }
        }
        return image;
    }
}

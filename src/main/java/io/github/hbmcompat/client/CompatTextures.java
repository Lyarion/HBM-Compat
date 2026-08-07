package io.github.hbmcompat.client;

import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.IIcon;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import io.github.hbmcompat.HbmCompat;

@SideOnly(Side.CLIENT)
public enum CompatTextures {

    HBM_FLUID_IMPORT("fluid_import_face"),
    HBM_FLUID_EXPORT("fluid_export_face");

    private final String name;
    private IIcon icon;

    CompatTextures(String name) {
        this.name = name;
    }

    public IIcon getIcon() {
        return icon;
    }

    public void register(TextureMap map) {
        icon = map.registerIcon(HbmCompat.MODID + ":" + name);
    }

    public static void registerAll(TextureMap map) {
        for (CompatTextures texture : values()) {
            texture.register(map);
        }
    }
}

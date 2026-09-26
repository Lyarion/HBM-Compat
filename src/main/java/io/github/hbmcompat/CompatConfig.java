package io.github.hbmcompat;

import java.io.File;
import java.util.Locale;

import net.minecraftforge.common.config.Configuration;

/** Startup-only settings: changing fluid identities requires a full restart. */
public final class CompatConfig {
    public static boolean preferBob = false;
    public static boolean hideBobFluidBlocks = false;

    private CompatConfig() {}

    public static void load(File file) {
        Configuration config = new Configuration(file);
        config.load();
        String mode = config.getString("fluidMapping", "fluids", "legacy",
                "auto: reuse Bob's actual mappings when installed, otherwise use HBM-Compat names. "
                        + "legacy (default): use HBM-Compat names (duplicates with Bob may remain). "
                        + "Restart required; use legacy for existing worlds until old ME fluids/patterns are migrated. "
                        + "This setting does NOT migrate saved fluids or patterns.").trim().toLowerCase(Locale.ROOT);
        if (!"auto".equals(mode) && !"legacy".equals(mode)) {
            throw new IllegalArgumentException("Invalid hbmcompat fluids.fluidMapping: " + mode
                    + "; expected auto or legacy");
        }
        preferBob = "auto".equals(mode);
        hideBobFluidBlocks = config.getBoolean("hideBobFluidBlocks", "client", false,
                "Hide Bob's fluid block items in NEI; keep the normal NEI fluid displays. "
                        + "Only affects display, never registration. Restart required.");
        if (config.hasChanged()) config.save();
    }
}

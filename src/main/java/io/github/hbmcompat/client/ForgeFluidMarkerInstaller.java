package io.github.hbmcompat.client;

import java.util.LinkedHashMap;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;

import codechicken.nei.api.API;
import codechicken.nei.recipe.StackInfo;
import com.hbm.inventory.fluid.FluidType;
import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.fluid.HbmFluidAccess;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;

final class ForgeFluidMarkerInstaller {

    private static boolean installed;

    private ForgeFluidMarkerInstaller() {}

    static synchronized void install() {
        if (installed) {
            return;
        }

        Map<Fluid, FluidType> fluids = new LinkedHashMap<Fluid, FluidType>();
        for (FluidType hbmFluid : HbmFluidAccess.getAllFluidTypes()) {
            Fluid forgeFluid = HbmForgeFluidRegistry.getForgeFluid(hbmFluid);
            if (forgeFluid != null && !fluids.containsKey(forgeFluid)) {
                fluids.put(forgeFluid, hbmFluid);
            }
        }

        int count = 0;
        for (Map.Entry<Fluid, FluidType> entry : fluids.entrySet()) {
            // ItemFluidPacket display stacks are not round-trippable through NEI's
            // StackInfo.withAmount on the target pack. A click or attempted drag
            // therefore supplies null to NEIClientUtils.giveStack and crashes.
            // HBM's own registered icon item is a safe carrier; its fill tag is
            // converted back to the mapped Forge fluid by our StackInfo handler.
            ItemStack marker = HbmFluidAccess.makeFluidIcon(entry.getValue(), 1000);
            ItemStack dragProbe = StackInfo.withAmount(marker, 1000);
            FluidStack fluidProbe = dragProbe == null ? null : StackInfo.getFluid(dragProbe);
            if (dragProbe == null || fluidProbe == null || fluidProbe.getFluid() != entry.getKey()) {
                HbmCompat.LOG.warn(
                        "Skipped unsafe NEI drag marker for HBM fluid {}",
                        HbmFluidAccess.getFluidName(entry.getValue()));
                continue;
            }

            API.addItemListEntry(marker);
            count++;
        }

        installed = true;
        HbmCompat.LOG.info("Registered {} draggable HBM-to-Forge fluid markers in the NEI item panel", count);
    }
}

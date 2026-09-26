package io.github.hbmcompat.client;

import net.minecraftforge.client.event.GuiScreenEvent;

import appeng.api.config.Settings;
import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.container.AEBaseContainer;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.hbmcompat.ae2.TileHbmAdapter;

/**
 * Hides the BLOCK / SMART_BLOCK buttons on the HBM processing adapter's GUI.
 *
 * <p>The adapter opens AE2's stock {@code GuiBridge.GUI_INTERFACE}, and {@link GuiInterface}
 * unconditionally adds both blocking toggles at the top-left. The adapter uses its own input-only
 * feeding gate and factory allocation buttons instead of AE's whole-inventory blocking gate.
 * Both settings stay registered — {@code ContainerInterface} reads them unconditionally
 * and unregistering would crash — they are merely not shown.
 *
 * <p>Only this adapter's GUI is touched; a real ME Interface keeps its buttons.
 */
public final class HbmBlockingButtonHider {

    @SubscribeEvent
    public void onGuiInit(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiInterface)) {
            return;
        }
        if (!isAdapterGui((GuiInterface) event.gui)) {
            return;
        }
        for (Object entry : event.buttonList) {
            if (!(entry instanceof GuiImgButton)) {
                continue;
            }
            GuiImgButton button = (GuiImgButton) entry;
            Settings setting = button.getSetting();
            if (setting == Settings.BLOCK || setting == Settings.SMART_BLOCK) {
                // Clears both visible and enabled, so the button cannot be clicked either.
                button.setVisibility(false);
            }
        }
    }

    /** True if the open interface GUI belongs to an HBM processing adapter rather than an ME Interface. */
    private boolean isAdapterGui(GuiInterface gui) {
        if (!(gui.inventorySlots instanceof AEBaseContainer)) {
            return false;
        }
        return ((AEBaseContainer) gui.inventorySlots).getTarget() instanceof TileHbmAdapter;
    }
}

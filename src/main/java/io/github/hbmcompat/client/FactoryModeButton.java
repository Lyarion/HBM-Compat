package io.github.hbmcompat.client;

import java.util.concurrent.atomic.AtomicReference;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.StatCollector;
import net.minecraftforge.client.event.GuiScreenEvent;
import appeng.client.gui.implementations.GuiInterface;
import appeng.client.gui.widgets.GuiImgButton;
import appeng.client.gui.widgets.ITooltip;
import appeng.api.config.Settings;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.hbmcompat.ae2.TileHbmAdapter;
import io.github.hbmcompat.network.FactoryModeNetwork;
import io.github.hbmcompat.network.FactoryModeNetwork.State;

/** Uses the vacant blocking-button position without changing AE's blocking settings. */
public final class FactoryModeButton {
    private static final AtomicReference<State> INCOMING = new AtomicReference<State>();
    private State state;
    private GuiInterface screen;
    private ModeButton button;

    public static void receive(State message) { INCOMING.set(message); }

    @SubscribeEvent public void open(net.minecraftforge.client.event.GuiOpenEvent event) {
        if (event.gui != screen) {
            screen = null;
            button = null;
            state = null;
            INCOMING.set(null);
        }
    }

    @SubscribeEvent public void init(GuiScreenEvent.InitGuiEvent.Post event) {
        if (!(event.gui instanceof GuiInterface)) return;
        GuiInterface gui = (GuiInterface) event.gui;
        if (FactoryModeNetwork.adapter(gui.inventorySlots) == null) return;
        screen = gui;
        state = null;
        button = null;
        for (Object entry : event.buttonList) {
            if (entry instanceof GuiImgButton && ((GuiImgButton) entry).getSetting() == Settings.BLOCK) {
                GuiButton original = (GuiButton) entry;
                button = new ModeButton(original.xPosition, original.yPosition);
                break;
            }
        }
        if (button != null) event.buttonList.add(button);
        FactoryModeNetwork.CHANNEL.sendToServer(new FactoryModeNetwork.Request(gui.inventorySlots.windowId,
                FactoryModeNetwork.adapter(gui.inventorySlots), false, false));
    }

    @SubscribeEvent public void draw(GuiScreenEvent.DrawScreenEvent.Pre event) {
        if (event.gui != screen || button == null) return;
        State update = INCOMING.getAndSet(null);
        TileHbmAdapter tile = FactoryModeNetwork.adapter(screen.inventorySlots);
        if (update != null && update.matches(screen.inventorySlots, tile)) state = update;
        button.visible = state != null && state.factory;
        button.enabled = button.visible;
        button.variety = state != null && state.variety;
        button.displayString = StatCollector.translateToLocal(button.variety
                ? "hbmcompat.factory.variety.short" : "hbmcompat.factory.parallel.short");
    }

    @SubscribeEvent public void click(GuiScreenEvent.ActionPerformedEvent.Pre event) {
        if (event.gui != screen || event.button != button || state == null || !state.factory) return;
        event.setCanceled(true);
        TileHbmAdapter tile = FactoryModeNetwork.adapter(screen.inventorySlots);
        if (tile != null) FactoryModeNetwork.CHANNEL.sendToServer(
                new FactoryModeNetwork.Request(screen.inventorySlots.windowId, tile, !state.variety, true));
    }

    private static final class ModeButton extends GuiButton implements ITooltip {
        private boolean variety;
        ModeButton(int x, int y) {
            super(0x4842, x, y, 16, 16, "");
            visible = false; enabled = false;
        }
        @Override public String getMessage() {
            return StatCollector.translateToLocal("hbmcompat.factory.title") + "\n"
                    + StatCollector.translateToLocal(variety ? "hbmcompat.factory.variety" : "hbmcompat.factory.parallel")
                    + "\n" + StatCollector.translateToLocal(variety
                            ? "hbmcompat.factory.switch.parallel" : "hbmcompat.factory.switch.variety")
                    + "\n" + StatCollector.translateToLocal("hbmcompat.factory.independent");
        }
        @Override public int xPos() { return xPosition; }
        @Override public int yPos() { return yPosition; }
        @Override public int getWidth() { return width; }
        @Override public int getHeight() { return height; }
        @Override public boolean isVisible() { return visible; }
    }
}

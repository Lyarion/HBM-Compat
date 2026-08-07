package io.github.hbmcompat.pattern;

import java.util.WeakHashMap;
import java.util.Map;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

import appeng.api.parts.IPatternTerminal;
import appeng.api.parts.IPatternTerminal.PatternEncodeListener;
import appeng.container.implementations.ContainerPatternTerm;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;

/** Installs AE2's official per-terminal encode callback while a player has the terminal open. */
public final class HbmPatternEncodeHandler {

    private final Map<EntityPlayer, Registration> registrations =
            new WeakHashMap<EntityPlayer, Registration>();

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.worldObj.isRemote) {
            return;
        }

        Registration current = registrations.get(event.player);
        if (!(event.player.openContainer instanceof ContainerPatternTerm)) {
            if (current != null) {
                current.remove();
                registrations.remove(event.player);
            }
            return;
        }

        final ContainerPatternTerm container = (ContainerPatternTerm) event.player.openContainer;
        if (current != null && current.container == container) {
            return;
        }
        if (current != null) {
            current.remove();
        }

        final IPatternTerminal terminal = container.getPatternTerminal();
        final World world = event.player.worldObj;
        PatternEncodeListener listener = new PatternEncodeListener() {

            @Override
            public void onEncoded(IPatternTerminal encodedAt, ItemStack pattern) {
                HbmPatternMetadata.writeUniqueMatch(pattern, world);
            }
        };
        terminal.addPatternEncodeListeners(listener);
        registrations.put(event.player, new Registration(container, terminal, listener));
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        Registration registration = registrations.remove(event.player);
        if (registration != null) {
            registration.remove();
        }
    }

    private static final class Registration {

        private final ContainerPatternTerm container;
        private final IPatternTerminal terminal;
        private final PatternEncodeListener listener;

        private Registration(
                ContainerPatternTerm container,
                IPatternTerminal terminal,
                PatternEncodeListener listener) {
            this.container = container;
            this.terminal = terminal;
            this.listener = listener;
        }

        private void remove() {
            terminal.removePatternEncodeListeners(listener);
        }
    }
}

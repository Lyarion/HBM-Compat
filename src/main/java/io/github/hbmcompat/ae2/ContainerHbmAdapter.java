package io.github.hbmcompat.ae2;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraftforge.common.util.ForgeDirection;

import appeng.container.PrimaryGui;
import appeng.container.implementations.ContainerInterface;

public final class ContainerHbmAdapter extends ContainerInterface {
    private final TileHbmAdapter adapter;

    public ContainerHbmAdapter(InventoryPlayer player, TileHbmAdapter adapter) {
        super(player, adapter);
        this.adapter = adapter;
        AdapterUpgradeSlots.install(this, adapter);
    }

    @Override public PrimaryGui createPrimaryGui() {
        // AE2's priority sub-GUI uses this callback to return to our mod's GUI handler.
        return new PrimaryGui(null, adapter.getPrimaryGuiIcon(), adapter, ForgeDirection.UNKNOWN) {
            @Override public void open(EntityPlayer player) {
                AdapterGuiHandler.open(player, adapter);
            }
        };
    }
}

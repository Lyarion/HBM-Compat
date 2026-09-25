package io.github.hbmcompat.proxy;

import net.minecraftforge.common.MinecraftForge;

import io.github.hbmcompat.client.HbmBlockingButtonHider;
import io.github.hbmcompat.client.HbmFluidTextureHandler;
import io.github.hbmcompat.client.HbmPatternTooltipHandler;
import io.github.hbmcompat.client.HbmProcessorInstaller;

public final class ClientProxy extends CommonProxy {

    @Override
    public Object createAdapterGui(net.minecraft.entity.player.InventoryPlayer player,
            io.github.hbmcompat.ae2.TileHbmAdapter tile) {
        return new io.github.hbmcompat.client.GuiHbmAdapter(player, tile);
    }

    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new io.github.hbmcompat.client.FactoryModeButton());
        MinecraftForge.EVENT_BUS.register(new HbmFluidTextureHandler());
    }

    @Override
    public void postInit() {
        io.github.hbmcompat.client.BobFluidDisplayCompat.hideFluidBlocks();
    }

    @Override
    public void receiveFactoryState(io.github.hbmcompat.network.FactoryModeNetwork.State state) {
        io.github.hbmcompat.client.FactoryModeButton.receive(state);
    }

    @Override
    public void init() {
        super.init();
        HbmProcessorInstaller installer = new HbmProcessorInstaller();
        installer.installInitialProcessor();
        MinecraftForge.EVENT_BUS.register(installer);
        MinecraftForge.EVENT_BUS.register(new HbmPatternTooltipHandler());
        MinecraftForge.EVENT_BUS.register(new HbmBlockingButtonHider());
    }
}

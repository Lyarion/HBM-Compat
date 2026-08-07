package io.github.hbmcompat.proxy;

import net.minecraftforge.common.MinecraftForge;

import io.github.hbmcompat.client.HbmBlockingButtonHider;
import io.github.hbmcompat.client.HbmFluidTextureHandler;
import io.github.hbmcompat.client.HbmPatternTooltipHandler;
import io.github.hbmcompat.client.HbmProcessorInstaller;

public final class ClientProxy extends CommonProxy {

    @Override
    public void preInit() {
        MinecraftForge.EVENT_BUS.register(new HbmFluidTextureHandler());
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

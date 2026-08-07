package io.github.hbmcompat.proxy;

import codechicken.nei.recipe.StackInfo;
import cpw.mods.fml.common.FMLCommonHandler;
import io.github.hbmcompat.HbmCompat;
import io.github.hbmcompat.fluid.HbmFluidStackStringifyHandler;
import io.github.hbmcompat.pattern.HbmPatternEncodeHandler;

public class CommonProxy {

    public void preInit() {}

    public void init() {
        FMLCommonHandler.instance().bus().register(new HbmPatternEncodeHandler());
        for (Object handler : StackInfo.stackStringifyHandlers) {
            if (handler instanceof HbmFluidStackStringifyHandler) {
                return;
            }
        }

        StackInfo.stackStringifyHandlers.add(new HbmFluidStackStringifyHandler());
        HbmCompat.LOG.info("Installed the HBM Forge-fluid StackInfo bridge");
    }
}

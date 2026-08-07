package io.github.hbmcompat.client;

import codechicken.nei.recipe.IRecipeHandler;

final class HbmHandlerSupport {

    private HbmHandlerSupport() {}

    static boolean isHbmHandler(IRecipeHandler handler) {
        return handler != null && handler.getClass().getName().startsWith("com.hbm.");
    }
}

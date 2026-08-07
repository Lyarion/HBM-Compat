package io.github.hbmcompat.client;

import java.util.ArrayList;
import java.util.List;

import com.github.vfyjxf.nee.processor.IRecipeProcessor;
import com.github.vfyjxf.nee.processor.RecipeProcessor;

import codechicken.nei.event.NEIConfigsLoadedEvent;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import io.github.hbmcompat.HbmCompat;

public final class HbmProcessorInstaller {

    public void installInitialProcessor() {
        synchronized (RecipeProcessor.class) {
            if (!containsHbmProcessor(RecipeProcessor.recipeProcessors)) {
                RecipeProcessor.recipeProcessors.add(0, new HbmRecipeProcessor());
            }
        }
    }

    @SubscribeEvent
    public void onNeiConfigsLoaded(NEIConfigsLoadedEvent event) {
        ForgeFluidMarkerInstaller.install();

        synchronized (RecipeProcessor.class) {
            List<IRecipeProcessor> installed = new ArrayList<IRecipeProcessor>();
            installed.add(new HbmRecipeProcessor());

            int guarded = 0;
            for (IRecipeProcessor original : RecipeProcessor.recipeProcessors) {
                IRecipeProcessor processor = unwrap(original);
                if (processor instanceof HbmRecipeProcessor) {
                    continue;
                }

                if (processor.getAllOverlayIdentifier().contains(RecipeProcessor.NULL_IDENTIFIER)) {
                    installed.add(new HbmFallbackGuardProcessor(processor));
                    guarded++;
                } else {
                    installed.add(processor);
                }
            }

            RecipeProcessor.recipeProcessors = installed;
            HbmCompat.LOG.info(
                    "Installed HBM recipe processor first and guarded {} null-overlay fallback processors",
                    guarded);
        }
    }

    private boolean containsHbmProcessor(List<IRecipeProcessor> processors) {
        for (IRecipeProcessor processor : processors) {
            if (unwrap(processor) instanceof HbmRecipeProcessor) {
                return true;
            }
        }
        return false;
    }

    private IRecipeProcessor unwrap(IRecipeProcessor processor) {
        return processor instanceof HbmFallbackGuardProcessor
                ? ((HbmFallbackGuardProcessor) processor).getDelegate()
                : processor;
    }
}

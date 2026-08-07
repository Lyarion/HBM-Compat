package io.github.hbmcompat.client;

import java.util.Collections;
import java.util.List;
import java.util.Set;

import com.github.vfyjxf.nee.processor.IRecipeProcessor;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;

final class HbmFallbackGuardProcessor implements IRecipeProcessor {

    private final IRecipeProcessor delegate;

    HbmFallbackGuardProcessor(IRecipeProcessor delegate) {
        this.delegate = delegate;
    }

    IRecipeProcessor getDelegate() {
        return delegate;
    }

    @Override
    public Set<String> getAllOverlayIdentifier() {
        return delegate.getAllOverlayIdentifier();
    }

    @Override
    public String getRecipeProcessorId() {
        return delegate.getRecipeProcessorId();
    }

    @Override
    public List<PositionedStack> getRecipeInput(IRecipeHandler recipe, int recipeIndex, String identifier) {
        return HbmHandlerSupport.isHbmHandler(recipe) ? Collections.<PositionedStack>emptyList()
                : delegate.getRecipeInput(recipe, recipeIndex, identifier);
    }

    @Override
    public List<PositionedStack> getRecipeOutput(IRecipeHandler recipe, int recipeIndex, String identifier) {
        return HbmHandlerSupport.isHbmHandler(recipe) ? Collections.<PositionedStack>emptyList()
                : delegate.getRecipeOutput(recipe, recipeIndex, identifier);
    }

    @Override
    public boolean mergeStacks(IRecipeHandler recipe, int recipeIndex, String identifier) {
        return delegate.mergeStacks(recipe, recipeIndex, identifier);
    }
}

package io.github.hbmcompat.client;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import net.minecraft.client.Minecraft;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.EnumChatFormatting;

import com.github.vfyjxf.nee.processor.IRecipeProcessor;
import com.github.vfyjxf.nee.processor.RecipeProcessor;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import io.github.hbmcompat.HbmCompat;

public final class HbmRecipeProcessor implements IRecipeProcessor {

    public static final String PROCESSOR_ID = "NEE_HBM_FLUID_COMPAT";
    private static final Set<String> IDENTIFIERS = Collections.unmodifiableSet(
            new HashSet<String>(Arrays.asList(RecipeProcessor.NULL_IDENTIFIER, "ntmAnvil")));

    private final HbmRecipeExtractor extractor = new HbmRecipeExtractor();
    private IRecipeHandler cachedHandler;
    private int cachedIndex = -1;
    private ExtractionResult cachedResult;
    private String lastReportedProblem;
    private long lastReportTime;

    @Override
    public Set<String> getAllOverlayIdentifier() {
        return IDENTIFIERS;
    }

    @Override
    public String getRecipeProcessorId() {
        return PROCESSOR_ID;
    }

    @Override
    public List<PositionedStack> getRecipeInput(IRecipeHandler recipe, int recipeIndex, String identifier) {
        ExtractionResult result = getResult(recipe, recipeIndex);
        return result.isSuccess() ? result.inputs : Collections.<PositionedStack>emptyList();
    }

    @Override
    public List<PositionedStack> getRecipeOutput(IRecipeHandler recipe, int recipeIndex, String identifier) {
        ExtractionResult result = getResult(recipe, recipeIndex);
        return result.isSuccess() ? result.outputs : Collections.<PositionedStack>emptyList();
    }

    @Override
    public boolean mergeStacks(IRecipeHandler recipe, int recipeIndex, String identifier) {
        return false;
    }

    private ExtractionResult getResult(IRecipeHandler recipe, int recipeIndex) {
        if (!HbmHandlerSupport.isHbmHandler(recipe)) {
            return ExtractionResult.failure("not an HBM handler");
        }

        if (recipe != cachedHandler || recipeIndex != cachedIndex || cachedResult == null) {
            cachedHandler = recipe;
            cachedIndex = recipeIndex;
            cachedResult = extractor.extract(recipe, recipeIndex);
            if (!cachedResult.isSuccess()) {
                reportProblem(recipe, cachedResult.error);
            }
        }
        return cachedResult;
    }

    private void reportProblem(IRecipeHandler recipe, String problem) {
        String message = recipe.getClass().getSimpleName() + ": " + problem;
        long now = System.currentTimeMillis();
        if (message.equals(lastReportedProblem) && now - lastReportTime < 2000L) {
            return;
        }

        lastReportedProblem = message;
        lastReportTime = now;
        HbmCompat.LOG.warn("Rejected HBM recipe transfer: {}", message);

        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft != null && minecraft.thePlayer != null) {
            minecraft.thePlayer.addChatMessage(new ChatComponentText(
                    EnumChatFormatting.RED + "[HBM Compat] Cannot transfer recipe: " + problem));
        }
    }
}

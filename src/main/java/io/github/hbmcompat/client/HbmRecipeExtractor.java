package io.github.hbmcompat.client;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.Fluid;

import codechicken.nei.PositionedStack;
import codechicken.nei.recipe.IRecipeHandler;
import codechicken.nei.recipe.TemplateRecipeHandler;
import io.github.hbmcompat.fluid.HbmFluidStackStringifyHandler;
import io.github.hbmcompat.fluid.HbmFluidAccess;
import io.github.hbmcompat.fluid.HbmForgeFluidRegistry;

final class HbmRecipeExtractor {

    private static final String UNIVERSAL_HANDLER = "com.hbm.handler.nei.NEIUniversalHandler";
    private static final String GENERIC_HANDLER = "com.hbm.handler.nei.NEIGenericRecipeHandler";
    private static final String ANVIL_HANDLER = "com.hbm.handler.nei.AnvilRecipeHandler";
    private static final String CHANCE_OUTPUT = "com.hbm.inventory.recipes.loader.GenericRecipes$ChanceOutput";
    private static final String MULTI_OUTPUT = "com.hbm.inventory.recipes.loader.GenericRecipes$ChanceOutputMulti";
    private static final Map<String, RecipeSchema> SPECIAL_SCHEMAS = createSpecialSchemas();

    static final int MAX_INPUTS = 16;
    static final int MAX_OUTPUTS = 4;

    ExtractionResult extract(IRecipeHandler handler, int recipeIndex) {
        RecipeSchema specialSchema = findSpecialSchema(handler);
        if (!inherits(handler, UNIVERSAL_HANDLER) && !inherits(handler, GENERIC_HANDLER)
                && !inherits(handler, ANVIL_HANDLER) && specialSchema == null) {
            return ExtractionResult.failure("unsupported HBM NEI handler: " + handler.getClass().getSimpleName());
        }

        TemplateRecipeHandler template = (TemplateRecipeHandler) handler;
        if (recipeIndex < 0 || recipeIndex >= template.arecipes.size()) {
            return ExtractionResult.failure("recipe index is out of range");
        }

        Object cachedRecipe = template.arecipes.get(recipeIndex);
        try {
            String probabilityError = validateDeterministicOutputs(handler, cachedRecipe, recipeIndex);
            if (probabilityError != null) {
                return ExtractionResult.failure(probabilityError);
            }

            List<PositionedStack> inputs = readPositionedStacks(
                    cachedRecipe,
                    specialSchema == null ? new String[] { "input" } : specialSchema.inputFields);
            List<PositionedStack> outputs = readPositionedStacks(
                    cachedRecipe,
                    specialSchema == null ? new String[] { "output" } : specialSchema.outputFields);

            if (inputs.isEmpty() || outputs.isEmpty()) {
                return ExtractionResult.failure("recipe has no input or output");
            }
            if (inputs.size() > MAX_INPUTS) {
                return ExtractionResult.failure("recipe has " + inputs.size() + " inputs; terminal limit is " + MAX_INPUTS);
            }
            if (outputs.size() > MAX_OUTPUTS) {
                return ExtractionResult.failure("recipe has " + outputs.size() + " outputs; terminal limit is " + MAX_OUTPUTS);
            }
            for (PositionedStack output : outputs) {
                if (output != null && output.getChance() < PositionedStack.CHANCE_FULL) {
                    return ExtractionResult.failure("probabilistic NEI outputs are not supported");
                }
            }

            List<PositionedStack> convertedInputs = convertStacks(inputs);
            List<PositionedStack> convertedOutputs = convertStacks(outputs);
            return ExtractionResult.success(convertedInputs, convertedOutputs);
        } catch (RecipeRejectedException e) {
            return ExtractionResult.failure(e.getMessage());
        } catch (ReflectiveOperationException e) {
            return ExtractionResult.failure("HBM cached recipe layout is incompatible: " + e.getClass().getSimpleName());
        } catch (RuntimeException e) {
            return ExtractionResult.failure("failed to read HBM recipe: " + e.getClass().getSimpleName());
        }
    }

    private String validateDeterministicOutputs(IRecipeHandler handler, Object cachedRecipe, int recipeIndex)
            throws ReflectiveOperationException {
        if (inherits(handler, GENERIC_HANDLER)) {
            Object recipe = readField(cachedRecipe, "recipe");
            Object outputItems = recipe == null ? null : readField(recipe, "outputItem");
            if (outputItems instanceof Object[]) {
                for (Object output : (Object[]) outputItems) {
                    if (inherits(output, CHANCE_OUTPUT)) {
                        if (numberField(output, "chance") < 1F) {
                            return "probabilistic item outputs are not supported";
                        }
                    } else if (inherits(output, MULTI_OUTPUT)) {
                        Object poolObject = readField(output, "pool");
                        if (!(poolObject instanceof List<?>)) {
                            return "unreadable weighted item output";
                        }
                        List<?> pool = (List<?>) poolObject;
                        if (pool.size() != 1 || numberField(pool.get(0), "chance") < 1F) {
                            return "weighted or probabilistic item outputs are not supported";
                        }
                    } else if (invokeBoolean(output, "possibleMultiOutput") || invoke(output, "getSingle") == null) {
                        return "non-deterministic item outputs are not supported";
                    }
                }
            }
        } else if (inherits(handler, ANVIL_HANDLER)) {
            Object recipe = readField(cachedRecipe, "recipe");
            Object outputs = recipe == null ? null : readField(recipe, "output");
            if (outputs instanceof Iterable<?>) {
                for (Object output : (Iterable<?>) outputs) {
                    if (numberField(output, "chance") < 1F) {
                        return "probabilistic anvil outputs are not supported";
                    }
                }
            }
        }
        return null;
    }

    private List<PositionedStack> readPositionedStacks(Object cachedRecipe, String... fieldNames)
            throws ReflectiveOperationException, RecipeRejectedException {
        List<PositionedStack> result = new ArrayList<PositionedStack>();
        for (String fieldName : fieldNames) {
            appendPositionedStacks(result, readField(cachedRecipe, fieldName), fieldName);
        }
        return result;
    }

    private void appendPositionedStacks(List<PositionedStack> result, Object value, String fieldName)
            throws RecipeRejectedException {
        if (value instanceof PositionedStack) {
            result.add((PositionedStack) value);
            return;
        }
        if (value instanceof PositionedStack[]) {
            result.addAll(Arrays.asList((PositionedStack[]) value));
            return;
        }
        if (value instanceof Iterable<?>) {
            for (Object entry : (Iterable<?>) value) {
                if (!(entry instanceof PositionedStack)) {
                    throw new RecipeRejectedException("unexpected entry in HBM " + fieldName + " list");
                }
                result.add((PositionedStack) entry);
            }
            return;
        }
        throw new RecipeRejectedException("HBM recipe has no readable " + fieldName + " stack");
    }

    private List<PositionedStack> convertStacks(List<PositionedStack> source) throws RecipeRejectedException {
        List<PositionedStack> result = new ArrayList<PositionedStack>(source.size());
        for (PositionedStack positioned : source) {
            if (positioned == null || positioned.items == null || positioned.items.length == 0) {
                throw new RecipeRejectedException("recipe contains an empty stack");
            }

            ItemStack[] alternatives = new ItemStack[positioned.items.length];
            for (int i = 0; i < positioned.items.length; i++) {
                alternatives[i] = convertStack(positioned.items[i]);
            }

            PositionedStack converted = new PositionedStack(alternatives, positioned.relx, positioned.rely, false);
            converted.setChance(positioned.getChance());
            result.add(converted);
        }
        return result;
    }

    private ItemStack convertStack(ItemStack source) throws RecipeRejectedException {
        if (source == null || source.getItem() == null) {
            throw new RecipeRejectedException("recipe contains a null item");
        }

        ItemStack copy = source.copy();
        if (!HbmFluidAccess.isFluidIcon(copy)) {
            return copy;
        }

        int pressure = HbmFluidAccess.getPressure(copy);
        if (pressure != 0) {
            throw new RecipeRejectedException("pressurized HBM fluids are not supported (" + pressure + " PU)");
        }

        int amount = HbmFluidAccess.getQuantity(copy);
        if (amount <= 0) {
            throw new RecipeRejectedException("HBM fluid has no positive amount");
        }

        com.hbm.inventory.fluid.FluidType hbmFluid = HbmFluidAccess.getFluidType(copy);
        if (hbmFluid == null) {
            throw new RecipeRejectedException("HBM fluid id " + copy.getItemDamage() + " is invalid");
        }

        Fluid forgeFluid = HbmForgeFluidRegistry.getForgeFluid(hbmFluid);
        if (forgeFluid == null) {
            throw new RecipeRejectedException(
                    "No Forge fluid registered for " + HbmFluidAccess.getFluidName(hbmFluid));
        }

        copy.stackSize = amount;
        HbmFluidStackStringifyHandler.markVirtualStack(copy);
        return copy;
    }

    private Object readField(Object instance, String name) throws ReflectiveOperationException {
        Class<?> type = instance.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(instance);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private boolean inherits(Object instance, String className) {
        if (instance == null) {
            return false;
        }
        Class<?> type = instance instanceof Class<?> ? (Class<?>) instance : instance.getClass();
        while (type != null) {
            if (type.getName().equals(className)) {
                return true;
            }
            type = type.getSuperclass();
        }
        return false;
    }

    private RecipeSchema findSpecialSchema(IRecipeHandler handler) {
        for (Map.Entry<String, RecipeSchema> entry : SPECIAL_SCHEMAS.entrySet()) {
            if (inherits(handler, entry.getKey())) {
                return entry.getValue();
            }
        }
        return null;
    }

    private static Map<String, RecipeSchema> createSpecialSchemas() {
        Map<String, RecipeSchema> schemas = new LinkedHashMap<String, RecipeSchema>();
        addSchema(schemas, "AlloyFurnaceRecipeHandler", fields("input1", "input2"), fields("result"));
        addSchema(schemas, "BookRecipeHandler", fields("input"), fields("result"));
        addSchema(schemas, "CrucibleAlloyingHandler", fields("inputs"), fields("outputs"));
        addSchema(schemas, "CrucibleCastingHandler", fields("input"), fields("output"));
        addSchema(schemas, "CrucibleSmeltingHandler", fields("input"), fields("outputs"));
        addSchema(schemas, "CyclotronRecipeHandler", fields("input1", "input2"), fields("result"));
        addSchema(schemas, "FluidRecipeHandler", fields("input"), fields("result"));
        addSchema(schemas, "GasCentrifugeRecipeHandler", fields("input"), fields("output"));
        addSchema(schemas, "PressRecipeHandler", fields("input"), fields("result"));
        addSchema(schemas, "RadiolysisRecipeHandler", fields("input"), fields("output1", "output2"));
        addSchema(
                schemas,
                "RefineryRecipeHandler",
                fields("input"),
                fields("result1", "result2", "result3", "result4", "result5"));
        addSchema(schemas, "ShredderRecipeHandler", fields("input"), fields("result"));
        addSchema(schemas, "SmithingRecipeHandler", fields("input1", "input2"), fields("output"));
        return schemas;
    }

    private static void addSchema(
            Map<String, RecipeSchema> schemas,
            String handlerSimpleName,
            String[] inputFields,
            String[] outputFields) {
        schemas.put("com.hbm.handler.nei." + handlerSimpleName, new RecipeSchema(inputFields, outputFields));
    }

    private static String[] fields(String... names) {
        return names;
    }

    private float numberField(Object instance, String name) throws ReflectiveOperationException {
        Object value = readField(instance, name);
        if (!(value instanceof Number)) {
            throw new IllegalStateException(name + " is not numeric");
        }
        return ((Number) value).floatValue();
    }

    private Object invoke(Object instance, String name) throws ReflectiveOperationException {
        Method method = instance.getClass().getMethod(name);
        return method.invoke(instance);
    }

    private boolean invokeBoolean(Object instance, String name) throws ReflectiveOperationException {
        Object result = invoke(instance, name);
        return result instanceof Boolean && ((Boolean) result).booleanValue();
    }

    private static final class RecipeRejectedException extends Exception {

        private static final long serialVersionUID = 1L;

        RecipeRejectedException(String message) {
            super(message);
        }
    }

    private static final class RecipeSchema {

        final String[] inputFields;
        final String[] outputFields;

        RecipeSchema(String[] inputFields, String[] outputFields) {
            this.inputFields = inputFields;
            this.outputFields = outputFields;
        }
    }
}

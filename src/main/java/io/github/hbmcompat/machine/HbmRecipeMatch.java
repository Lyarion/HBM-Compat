package io.github.hbmcompat.machine;

public final class HbmRecipeMatch {

    private final IHbmMachineDriver driver;
    private final Object recipe;
    private final String recipeName;

    public HbmRecipeMatch(IHbmMachineDriver driver, Object recipe, String recipeName) {
        this.driver = driver;
        this.recipe = recipe;
        this.recipeName = recipeName;
    }

    public IHbmMachineDriver getDriver() {
        return driver;
    }

    public Object getRecipe() {
        return recipe;
    }

    public String getRecipeName() {
        return recipeName;
    }
}

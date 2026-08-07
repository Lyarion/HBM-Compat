package io.github.hbmcompat.pattern;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

import appeng.api.implementations.ICraftingPatternItem;
import appeng.api.networking.crafting.ICraftingPatternDetails;
import io.github.hbmcompat.machine.HbmMachineDrivers;
import io.github.hbmcompat.machine.HbmRecipeMatch;

public final class HbmPatternMetadata {

    public static final String MACHINE_KEY = "hbmMachine";
    public static final String RECIPE_KEY = "hbmRecipe";
    public static final String VERSION_KEY = "hbmSelectorVersion";
    public static final int VERSION = 1;

    private HbmPatternMetadata() {}

    public static boolean writeUniqueMatch(ItemStack pattern, World world) {
        clear(pattern);
        if (pattern == null || world == null || !(pattern.getItem() instanceof ICraftingPatternItem)) {
            return false;
        }
        ICraftingPatternDetails details = ((ICraftingPatternItem) pattern.getItem()).getPatternForItem(pattern, world);
        HbmRecipeMatch match = HbmMachineDrivers.uniqueMatch(details);
        if (match == null) {
            return false;
        }
        write(pattern, match);
        return true;
    }

    public static void write(ItemStack pattern, HbmRecipeMatch match) {
        if (pattern == null || match == null) {
            return;
        }
        if (!pattern.hasTagCompound()) {
            pattern.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound tag = pattern.getTagCompound();
        tag.setInteger(VERSION_KEY, VERSION);
        tag.setString(MACHINE_KEY, match.getDriver().getMachineId());
        tag.setString(RECIPE_KEY, match.getRecipeName());
    }

    public static boolean agrees(ItemStack pattern, HbmRecipeMatch match) {
        if (pattern == null || match == null || !pattern.hasTagCompound()) {
            return true;
        }
        NBTTagCompound tag = pattern.getTagCompound();
        if (!tag.hasKey(MACHINE_KEY) && !tag.hasKey(RECIPE_KEY)) {
            return true;
        }
        return match.getDriver().getMachineId().equals(tag.getString(MACHINE_KEY))
                && match.getRecipeName().equals(tag.getString(RECIPE_KEY));
    }

    public static void clear(ItemStack pattern) {
        if (pattern == null || !pattern.hasTagCompound()) {
            return;
        }
        NBTTagCompound tag = pattern.getTagCompound();
        tag.removeTag(VERSION_KEY);
        tag.removeTag(MACHINE_KEY);
        tag.removeTag(RECIPE_KEY);
    }
}

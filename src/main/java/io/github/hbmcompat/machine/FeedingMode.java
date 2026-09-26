package io.github.hbmcompat.machine;

import net.minecraft.nbt.NBTTagCompound;

public enum FeedingMode {
    CONTINUOUS, SINGLE_BATCH;

    public void write(NBTTagCompound data) { data.setString("hbmFeedingMode", name()); }

    public static FeedingMode read(NBTTagCompound data) {
        return CONTINUOUS.name().equals(data.getString("hbmFeedingMode")) ? CONTINUOUS : SINGLE_BATCH;
    }
}

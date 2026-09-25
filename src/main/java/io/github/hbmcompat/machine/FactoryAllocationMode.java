package io.github.hbmcompat.machine;

public enum FactoryAllocationMode {
    PARALLEL_FIRST,
    VARIETY_FIRST;

    public static FactoryAllocationMode read(net.minecraft.nbt.NBTTagCompound data) {
        return decode(data.getString("hbmFactoryAllocation"));
    }

    public void write(net.minecraft.nbt.NBTTagCompound data) {
        data.setString("hbmFactoryAllocation", name());
    }

    public static FactoryAllocationMode decode(String value) {
        return "VARIETY_FIRST".equals(value) ? VARIETY_FIRST : PARALLEL_FIRST;
    }

    public FactoryAllocationMode next() {
        return this == PARALLEL_FIRST ? VARIETY_FIRST : PARALLEL_FIRST;
    }
}

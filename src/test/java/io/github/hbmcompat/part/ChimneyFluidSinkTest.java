package io.github.hbmcompat.part;

import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;

import com.hbm.inventory.fluid.FluidType;
import com.hbm.inventory.fluid.Fluids;
import com.hbm.render.util.EnumSymbol;
import com.hbm.tileentity.machine.TileEntityChimneyBase;
import com.hbm.tileentity.machine.TileEntityChimneyBrick;
import com.hbm.tileentity.machine.TileEntityChimneyIndustrial;
import org.junit.Test;
import org.junit.BeforeClass;
import org.junit.AfterClass;

import static org.junit.Assert.*;

public class ChimneyFluidSinkTest {

    private static FluidType[] original;

    @BeforeClass
    public static void initializeFluidTypes() {
        // HBM assigns these during mod pre-init, which plain JUnit does not run.
        original = new FluidType[] {Fluids.NONE, Fluids.WATER, Fluids.SMOKE,
                Fluids.SMOKE_LEADED, Fluids.SMOKE_POISON};
        if (Fluids.NONE == null) Fluids.NONE = new FluidType("NONE", 0, 0, 0, 0, EnumSymbol.NONE);
        if (Fluids.WATER == null) Fluids.WATER = new FluidType("WATER", 0, 0, 0, 0, EnumSymbol.NONE);
        if (Fluids.SMOKE == null) Fluids.SMOKE = smoke("SMOKE");
        if (Fluids.SMOKE_LEADED == null) Fluids.SMOKE_LEADED = smoke("SMOKE_LEADED");
        if (Fluids.SMOKE_POISON == null) Fluids.SMOKE_POISON = smoke("SMOKE_POISON");
    }

    private static FluidType smoke(String name) {
        return new FluidType(name, 0x808080, 0, 0, 0, EnumSymbol.NONE)
                .addTraits(Fluids.GASEOUS, Fluids.NOID, Fluids.NOCON);
    }

    @AfterClass
    public static void restoreFluidTypes() {
        Fluids.NONE = original[0];
        Fluids.WATER = original[1];
        Fluids.SMOKE = original[2];
        Fluids.SMOKE_LEADED = original[3];
        Fluids.SMOKE_POISON = original[4];
    }

    @Test
    public void acceptsAllSmokeTypesWithoutAnIdentifierOrTank() {
        for (TileEntityChimneyBase chimney : chimneys()) {
            assertTrue(ChimneyFluidSink.supports(chimney));
            assertEquals(0, chimney.getAllTanks().length);
            for (FluidType type : new FluidType[] {Fluids.SMOKE, Fluids.SMOKE_LEADED, Fluids.SMOKE_POISON}) {
                assertTrue(type.hasNoID());
                assertEquals(8000, ChimneyFluidSink.limit(chimney, type, ForgeDirection.EAST, 8000));
                assertEquals(1000000, ChimneyFluidSink.limit(chimney, type, ForgeDirection.EAST, Long.MAX_VALUE));
            }
        }
    }

    @Test
    public void rejectsOtherFluidsAndVerticalConnectionsBeforeExtraction() {
        for (TileEntityChimneyBase chimney : chimneys()) {
            assertEquals(0, ChimneyFluidSink.limit(chimney, Fluids.WATER, ForgeDirection.EAST, 8000));
            assertEquals(0, ChimneyFluidSink.limit(chimney, Fluids.NONE, ForgeDirection.EAST, 8000));
            assertEquals(0, ChimneyFluidSink.limit(chimney, Fluids.SMOKE, ForgeDirection.UP, 8000));
            assertEquals(0, ChimneyFluidSink.limit(chimney, Fluids.SMOKE, ForgeDirection.DOWN, 8000));
            assertEquals(0, ChimneyFluidSink.limit(chimney, Fluids.SMOKE, ForgeDirection.EAST, 0));
        }
        assertFalse(ChimneyFluidSink.supports(new TileEntity()));
        assertFalse(ChimneyFluidSink.supports(null));
    }

    @Test
    public void hbmConsumesEveryValidatedSmokeAmountAndCollectsAsh() {
        for (TileEntityChimneyBase chimney : chimneys()) {
            long total = 0;
            for (FluidType type : new FluidType[] {Fluids.SMOKE, Fluids.SMOKE_LEADED, Fluids.SMOKE_POISON}) {
                long amount = ChimneyFluidSink.limit(chimney, type, ForgeDirection.EAST, 8000);
                // No registered world pollution data in JUnit; HBM still collects ash.
                assertEquals(0, chimney.transferFluid(type, 0, amount));
                total += amount;
                assertEquals(total, chimney.ashTick);
                assertEquals(chimney instanceof TileEntityChimneyIndustrial ? total : 0, chimney.sootTick);
            }
            assertEquals(8000, chimney.transferFluid(Fluids.WATER, 0, 8000));
            assertEquals(total, chimney.ashTick);
        }
    }

    @Test
    public void requiresTheBaseSideCenterPortOnEverySide() {
        for (TileEntityChimneyBase chimney : chimneys()) {
            chimney.xCoord = 10;
            chimney.yCoord = 20;
            chimney.zCoord = -30;
            for (ForgeDirection facing : new ForgeDirection[] {
                    ForgeDirection.NORTH, ForgeDirection.SOUTH, ForgeDirection.EAST, ForgeDirection.WEST}) {
                int x = chimney.xCoord - facing.offsetX * 2;
                int z = chimney.zCoord - facing.offsetZ * 2;
                assertTrue(ChimneyFluidSink.isPort(chimney, x, 20, z, facing));
                assertFalse(ChimneyFluidSink.isPort(chimney, x, 21, z, facing));
                assertFalse(ChimneyFluidSink.isPort(chimney, x + facing.offsetZ, 20,
                        z + facing.offsetX, facing));
                assertFalse(ChimneyFluidSink.isPort(chimney, x, 20, z, facing.getOpposite()));
            }
            assertFalse(ChimneyFluidSink.isPort(chimney, 10, 22, -30, ForgeDirection.DOWN));
            assertFalse(ChimneyFluidSink.isPort(chimney, 10, 20, -30, ForgeDirection.UNKNOWN));
        }
    }

    private TileEntityChimneyBase[] chimneys() {
        return new TileEntityChimneyBase[] {new TileEntityChimneyBrick(), new TileEntityChimneyIndustrial()};
    }
}

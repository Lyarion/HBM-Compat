package io.github.hbmcompat.fluid;

import org.junit.Test;
import static org.junit.Assert.*;

public class FluidMappingTableTest {
    @Test
    public void bobIsPreferredAndExistingLegacyIsAccepted() {
        FluidMappingTable<String, Object> table = new FluidMappingTable<String, Object>();
        Object bob = new Object();
        table.prefer("DEUTERIUM", "deuterium_fluid", bob);
        assertTrue(table.alias("DEUTERIUM", "deuterium"));
        assertSame(bob, table.output("DEUTERIUM"));
        assertEquals("DEUTERIUM", table.input("deuterium_fluid"));
        assertEquals("DEUTERIUM", table.input("deuterium"));
        assertNull(table.input("not_registered"));
    }

    @Test
    public void customBobMappingAndSharedWaterAreNotGuessedFromSuffixes() {
        FluidMappingTable<String, Object> table = new FluidMappingTable<String, Object>();
        Object mapped = new Object();
        table.prefer("WATZ", "mud_fluid", mapped);
        table.prefer("WATER", "water", new Object());
        assertEquals("WATZ", table.input("mud_fluid"));
        assertNull(table.input("watz_fluid"));
        assertEquals("WATER", table.input("water"));
    }

    @Test
    public void legacyModeCanAcceptBobWithoutChangingOutput() {
        FluidMappingTable<String, Object> table = new FluidMappingTable<String, Object>();
        Object legacy = new Object();
        table.prefer("OXYGEN", "oxygen", legacy);
        table.alias("OXYGEN", "oxygen_fluid");
        assertSame(legacy, table.output("OXYGEN"));
        assertEquals("OXYGEN", table.input("oxygen_fluid"));
    }

    @Test
    public void legacyAliasCannotOverrideAnExplicitMappingForAnotherType() {
        FluidMappingTable<String, Object> table = new FluidMappingTable<String, Object>();
        table.prefer("OXYGEN", "custom_oxygen", new Object());
        table.prefer("HYDROGEN", "oxygen", new Object());
        assertFalse(table.alias("OXYGEN", "oxygen"));
        assertEquals("HYDROGEN", table.input("oxygen"));
    }

    @Test
    public void ambiguousAliasesAreRejectedRegardlessOfOrder() {
        FluidMappingTable<String, Object> table = new FluidMappingTable<String, Object>();
        assertTrue(table.alias("A", "ambiguous"));
        assertFalse(table.alias("B", "ambiguous"));
        assertNull(table.input("ambiguous"));
        assertFalse(table.alias("A", "ambiguous"));
    }

    @Test(expected = IllegalStateException.class)
    public void twoPreferredTypesCannotSilentlyShareAForgeIdentity() {
        FluidMappingTable<String, Object> table = new FluidMappingTable<String, Object>();
        table.prefer("A", "shared", new Object());
        table.prefer("B", "shared", new Object());
    }
}

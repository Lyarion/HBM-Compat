package io.github.hbmcompat.part;

import static org.junit.Assert.assertEquals;

import java.util.Random;

import org.junit.Test;

import appeng.api.config.SchedulingMode;

public class ExportSlotSchedulerTest {

    @Test
    public void defaultModeUsesConfigurationOrder() {
        ExportSlotScheduler scheduler = new ExportSlotScheduler(new Random(1L));

        assertEquals(0, scheduler.slotForIteration(SchedulingMode.DEFAULT, 0, 5));
        assertEquals(1, scheduler.slotForIteration(SchedulingMode.DEFAULT, 1, 5));
        assertEquals(4, scheduler.slotForIteration(SchedulingMode.DEFAULT, 4, 5));

        scheduler.finishTick(SchedulingMode.DEFAULT, 3, 5);
        assertEquals(0, scheduler.getNextSlot());
    }

    @Test
    public void roundRobinAdvancesByInspectedSlots() {
        ExportSlotScheduler scheduler = new ExportSlotScheduler(new Random(1L));

        assertEquals(0, scheduler.slotForIteration(SchedulingMode.ROUNDROBIN, 0, 5));
        scheduler.finishTick(SchedulingMode.ROUNDROBIN, 1, 5);

        assertEquals(1, scheduler.slotForIteration(SchedulingMode.ROUNDROBIN, 0, 5));
        assertEquals(2, scheduler.slotForIteration(SchedulingMode.ROUNDROBIN, 1, 5));
        scheduler.finishTick(SchedulingMode.ROUNDROBIN, 2, 5);

        assertEquals(3, scheduler.getNextSlot());
        assertEquals(3, scheduler.slotForIteration(SchedulingMode.ROUNDROBIN, 0, 5));
    }

    @Test
    public void roundRobinFullSweepReturnsToSameSlot() {
        ExportSlotScheduler scheduler = new ExportSlotScheduler(new Random(1L));
        scheduler.setNextSlot(3);

        scheduler.finishTick(SchedulingMode.ROUNDROBIN, 5, 5);

        assertEquals(3, scheduler.getNextSlot());
    }

    @Test
    public void roundRobinNormalizesPersistedCursorAfterCapacityShrinks() {
        ExportSlotScheduler scheduler = new ExportSlotScheduler(new Random(1L));
        scheduler.setNextSlot(8);

        assertEquals(3, scheduler.slotForIteration(SchedulingMode.ROUNDROBIN, 0, 5));
        scheduler.finishTick(SchedulingMode.ROUNDROBIN, 1, 5);

        assertEquals(4, scheduler.getNextSlot());
    }

    @Test
    public void randomModeUsesASelectionForEveryIteration() {
        ExportSlotScheduler scheduler = new ExportSlotScheduler(new SequenceRandom(4, 1, 1));

        assertEquals(4, scheduler.slotForIteration(SchedulingMode.RANDOM, 0, 5));
        assertEquals(1, scheduler.slotForIteration(SchedulingMode.RANDOM, 1, 5));
        assertEquals(1, scheduler.slotForIteration(SchedulingMode.RANDOM, 2, 5));
    }

    private static final class SequenceRandom extends Random {

        private static final long serialVersionUID = 1L;
        private final int[] values;
        private int index;

        private SequenceRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[index++] % bound;
        }
    }
}

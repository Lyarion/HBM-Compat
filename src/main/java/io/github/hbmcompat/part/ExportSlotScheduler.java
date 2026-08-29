package io.github.hbmcompat.part;

import java.util.Random;

import appeng.api.config.SchedulingMode;

/**
 * Selects export-bus configuration slots using AE2's scheduling semantics.
 *
 * <p>The transfer loop owns the budget and decides when to stop. This class only
 * maps each loop iteration to a slot and advances the round-robin cursor after
 * the loop, which keeps HBM-specific tank handling separate from scheduling.
 */
final class ExportSlotScheduler {

    private final Random random;
    private int nextSlot;

    ExportSlotScheduler(Random random) {
        if (random == null) {
            throw new IllegalArgumentException("random must not be null");
        }
        this.random = random;
    }

    int slotForIteration(SchedulingMode mode, int iteration, int slotCount) {
        requireSlotCount(slotCount);
        if (iteration < 0) {
            throw new IllegalArgumentException("iteration must not be negative");
        }

        if (mode == SchedulingMode.RANDOM) {
            // Match PartBaseExportBus: random selections are made with replacement.
            return random.nextInt(slotCount);
        }
        if (mode == SchedulingMode.ROUNDROBIN) {
            return positiveModulo((long) nextSlot + iteration, slotCount);
        }
        return iteration;
    }

    void finishTick(SchedulingMode mode, int inspectedSlots, int slotCount) {
        requireSlotCount(slotCount);
        if (inspectedSlots < 0) {
            throw new IllegalArgumentException("inspectedSlots must not be negative");
        }
        if (mode == SchedulingMode.ROUNDROBIN) {
            nextSlot = positiveModulo((long) nextSlot + inspectedSlots, slotCount);
        }
    }

    int getNextSlot() {
        return nextSlot;
    }

    void setNextSlot(int nextSlot) {
        this.nextSlot = Math.max(0, nextSlot);
    }

    private static void requireSlotCount(int slotCount) {
        if (slotCount <= 0) {
            throw new IllegalArgumentException("slotCount must be positive");
        }
    }

    private static int positiveModulo(long value, int divisor) {
        return (int) ((value % divisor + divisor) % divisor);
    }
}

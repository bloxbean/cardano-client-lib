package com.bloxbean.cardano.client.common.model;

import java.util.Objects;

public class SlotConfig {

    private int slotLength;
    private long zeroSlot;
    private long zeroTime;

    public SlotConfig(int slotLength, long zeroSlot, long zeroTime) {
        this.slotLength = slotLength;
        this.zeroSlot = zeroSlot;
        this.zeroTime = zeroTime;
    }

    public int getSlotLength() {
        return slotLength;
    }

    public long getZeroSlot() {
        return zeroSlot;
    }

    public long getZeroTime() {
        return zeroTime;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SlotConfig that = (SlotConfig) o;
        return slotLength == that.slotLength && zeroSlot == that.zeroSlot && zeroTime == that.zeroTime;
    }

    @Override
    public int hashCode() {
        return Objects.hash(slotLength, zeroSlot, zeroTime);
    }
}

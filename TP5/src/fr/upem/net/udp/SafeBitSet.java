package fr.upem.net.udp;

import java.util.BitSet;

public class SafeBitSet {
    private final static Object lock = new Object();
    private final BitSet bitSet;

    public SafeBitSet(long size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }
        bitSet = new BitSet((int)size);
    }

    /**
     * Set the bit at the specified index to true
     * @param index of the bit to set
     */
    public void set(long index) {
        synchronized (lock) {
            bitSet.set((int) index);
        }
    }

    public boolean get(long index) {
        synchronized (lock) {
            return bitSet.get((int) index);
        }
    }
    /**
     * Set the bit at the specified index to false
     * @param index of the bit to set
     */
    public void clear(long index) {
        synchronized (lock) {
            bitSet.clear((int) index);
        }
    }

    /**
     * @return the number of bytes set to true in the set
     */
    public int getCardinality() {
        synchronized (lock) {
            return bitSet.cardinality();
        }
    }
}

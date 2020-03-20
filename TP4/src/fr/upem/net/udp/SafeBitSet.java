package fr.upem.net.udp;

import java.util.BitSet;

public class SafeBitSet {
    private final static Object lock = new Object();
    private final BitSet bitSet;

    public SafeBitSet(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be greater than 0");
        }
        bitSet = new BitSet(size);
    }

    /**
     * Set the bit at the specified index to true
     * @param index of the bit to set
     */
    public void set(int index) {
        synchronized (lock) {
            bitSet.set(index);
        }
    }

    public boolean get(int index) {
        synchronized (lock) {
            return bitSet.get(index);
        }
    }
    /**
     * Set the bit at the specified index to false
     * @param index of the bit to set
     */
    public void clear(int index) {
        synchronized (lock) {
            bitSet.clear(index);
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

package com.zawa.client.ai.zebra;

import java.util.Arrays;

final class ZebraTranspositionTable {
    static final int EXACT = 0;
    static final int UPPER = 1;
    static final int LOWER = 2;

    private final long[] keys;
    private final long[] values;
    private final int bucketMask;
    private final int ways;
    private int generation = 1;

    ZebraTranspositionTable(int bits) {
        this(bits, 1);
    }

    ZebraTranspositionTable(int bits, int ways) {
        if (ways != 1 && ways != 2) {
            throw new IllegalArgumentException("Transposition ways must be 1 or 2");
        }
        int size = 1 << bits;
        keys = new long[size];
        values = new long[size];
        this.ways = ways;
        bucketMask = size / ways - 1;
    }

    void beginSearch() {
        generation = (generation + 1) & 0xFF;
        if (generation == 0) {
            Arrays.fill(keys, 0L);
            Arrays.fill(values, 0L);
            generation = 1;
        }
    }

    void clear() {
        Arrays.fill(keys, 0L);
        Arrays.fill(values, 0L);
        generation = 1;
    }

    long probe(long key) {
        int base = ((int) key & bucketMask) * ways;
        for (int way = 0; way < ways; way++) {
            int index = base + way;
            if (keys[index] == key) {
                return values[index];
            }
        }
        return 0L;
    }

    void store(long key, int depth, int score, int bound, int move) {
        int base = ((int) key & bucketMask) * ways;
        int index = -1;
        int weakestQuality = Integer.MAX_VALUE;
        for (int way = 0; way < ways; way++) {
            int candidate = base + way;
            long previous = values[candidate];
            if (keys[candidate] == key) {
                index = candidate;
                break;
            }
            int agePenalty = generation(previous) == generation ? 128 : 0;
            int quality = agePenalty + depth(previous);
            if (quality < weakestQuality) {
                weakestQuality = quality;
                index = candidate;
            }
        }
        long previous = values[index];
        if (keys[index] != key && generation(previous) == generation && depth(previous) > depth + 2) {
            return;
        }
        long packed = score & 0xFFFFL;
        packed |= (long) (move + 1) << 16;
        packed |= (long) Math.min(depth, 127) << 23;
        packed |= (long) bound << 30;
        packed |= (long) generation << 32;
        keys[index] = key;
        values[index] = packed;
    }

    static int score(long value) {
        return (short) value;
    }

    static int move(long value) {
        return (int) ((value >>> 16) & 0x7F) - 1;
    }

    static int depth(long value) {
        return (int) ((value >>> 23) & 0x7F);
    }

    static int bound(long value) {
        return (int) ((value >>> 30) & 0x3);
    }

    private static int generation(long value) {
        return (int) ((value >>> 32) & 0xFF);
    }
}

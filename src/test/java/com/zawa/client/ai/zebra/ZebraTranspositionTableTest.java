package com.zawa.client.ai.zebra;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ZebraTranspositionTableTest {
    @Test
    void twoWayBucketKeepsTwoCollidingPositions() {
        ZebraTranspositionTable table = new ZebraTranspositionTable(4, 2);
        table.beginSearch();
        long firstKey = 1L;
        long secondKey = 9L;
        table.store(firstKey, 8, 123, ZebraTranspositionTable.EXACT, 17);
        table.store(secondKey, 7, -456, ZebraTranspositionTable.LOWER, 42);

        long first = table.probe(firstKey);
        long second = table.probe(secondKey);
        assertEquals(123, ZebraTranspositionTable.score(first));
        assertEquals(17, ZebraTranspositionTable.move(first));
        assertEquals(-456, ZebraTranspositionTable.score(second));
        assertEquals(42, ZebraTranspositionTable.move(second));
    }
}

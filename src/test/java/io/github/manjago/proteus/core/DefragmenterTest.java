package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Defragmenter (ISA v1.2 Position-Independent Code).
 */
class DefragmenterTest {
    
    private static final int SOUP_SIZE = 1000;
    
    private AtomicIntegerArray soup;
    private FreeListMemoryManager memoryManager;
    private Defragmenter defragmenter;
    
    @BeforeEach
    void setUp() {
        soup = new AtomicIntegerArray(SOUP_SIZE);
        memoryManager = new FreeListMemoryManager(SOUP_SIZE);
        defragmenter = new Defragmenter(soup, memoryManager);
    }
    
    @Test
    @DisplayName("needsDefragmentation returns false when memory is empty")
    void noDefragNeededWhenEmpty() {
        assertFalse(defragmenter.needsDefragmentation(14, 0.5));
    }
    
    @Test
    @DisplayName("needsDefragmentation returns false when not fragmented")
    void noDefragNeededWhenNotFragmented() {
        // Allocate one block - no fragmentation
        memoryManager.allocate(100);
        
        assertFalse(defragmenter.needsDefragmentation(14, 0.5));
    }
    
    @Test
    @DisplayName("needsDefragmentation returns true when highly fragmented")
    void defragNeededWhenFragmented() {
        // Create fragmentation: allocate small blocks, free alternating ones
        List<Integer> addresses = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            int addr = memoryManager.allocate(10);
            addresses.add(addr);
        }
        // Free every other block to create gaps
        for (int i = 0; i < addresses.size(); i += 2) {
            memoryManager.free(addresses.get(i), 10);
        }
        
        // Now memory is fragmented with small free blocks
        assertTrue(memoryManager.getFragmentation() > 0.5);
        
        // If largest free block is smaller than required, need defrag
        if (memoryManager.getLargestFreeBlock() < 50) {
            assertTrue(defragmenter.needsDefragmentation(50, 0.5));
        }
    }
    
    @Test
    @DisplayName("defragment compacts organisms to beginning of memory")
    void defragmentCompacts() {
        // Create 3 organisms with gaps between them
        // Org1 at 0, Org2 at 100, Org3 at 200 (with gaps)
        List<Organism> organisms = new ArrayList<>();
        
        // Write distinctive patterns to soup
        for (int i = 0; i < 10; i++) soup.set(0 + i, 1000 + i);    // Org1 data
        for (int i = 0; i < 10; i++) soup.set(100 + i, 2000 + i);  // Org2 data
        for (int i = 0; i < 10; i++) soup.set(200 + i, 3000 + i);  // Org3 data
        
        Organism org1 = new Organism(0, 0, 10, -1, 0);
        Organism org2 = new Organism(1, 100, 10, 0, 100);
        Organism org3 = new Organism(2, 200, 10, 0, 200);
        
        organisms.add(org1);
        organisms.add(org2);
        organisms.add(org3);
        
        // Simulate allocations
        memoryManager.allocate(10);   // org1 at 0
        memoryManager.allocate(90);   // gap 10-99 (allocated but "freed" below)
        memoryManager.allocate(10);   // org2 at 100
        memoryManager.allocate(90);   // gap 110-199
        memoryManager.allocate(10);   // org3 at 200
        
        // Free the gaps (simulating dead organisms)
        memoryManager.free(10, 90);
        memoryManager.free(110, 90);
        
        // Defragment
        int moved = defragmenter.defragment(organisms);
        
        // Org1 stays at 0 (already compact)
        assertEquals(0, org1.getStartAddr());
        assertEquals(0, org1.getState().getStartAddr());
        
        // Org2 moves from 100 to 10
        assertEquals(10, org2.getStartAddr());
        assertEquals(10, org2.getState().getStartAddr());
        
        // Org3 moves from 200 to 20
        assertEquals(20, org3.getStartAddr());
        assertEquals(20, org3.getState().getStartAddr());
        
        // Verify data was copied correctly
        for (int i = 0; i < 10; i++) {
            assertEquals(1000 + i, soup.get(0 + i),  "Org1 data at offset " + i);
            assertEquals(2000 + i, soup.get(10 + i), "Org2 data at offset " + i);
            assertEquals(3000 + i, soup.get(20 + i), "Org3 data at offset " + i);
        }
        
        // Only 2 organisms moved (org1 was already in place)
        assertEquals(2, moved);
        
        // Memory should now have one large free block at the end
        assertEquals(1, memoryManager.getFreeBlockCount());
        assertEquals(SOUP_SIZE - 30, memoryManager.getLargestFreeBlock());
    }
    
    @Test
    @DisplayName("defragment preserves relative IP correctly")
    void defragmentPreservesRelativeIp() {
        // Create organism at address 100 with IP pointing to instruction 5
        for (int i = 0; i < 14; i++) soup.set(100 + i, 0xDEAD0000 + i);
        
        Organism org = new Organism(0, 100, 14, -1, 0);
        org.getState().setIp(5);  // Relative IP = 5
        
        // Before: absolute IP = 100 + 5 = 105
        assertEquals(105, org.getState().getAbsoluteIp());
        
        List<Organism> organisms = new ArrayList<>();
        organisms.add(org);
        
        // Defragment (org moves from 100 to 0)
        defragmenter.defragment(organisms);
        
        // After: startAddr = 0, but relative IP still 5
        assertEquals(0, org.getStartAddr());
        assertEquals(5, org.getState().getIp());
        assertEquals(5, org.getState().getAbsoluteIp());  // Now 0 + 5 = 5
        
        // Verify genome was copied
        for (int i = 0; i < 14; i++) {
            assertEquals(0xDEAD0000 + i, soup.get(i));
        }
    }
    
    @Test
    @DisplayName("defragment handles empty list")
    void defragmentEmptyList() {
        int moved = defragmenter.defragment(new ArrayList<>());
        assertEquals(0, moved);
    }
    
    @Test
    @DisplayName("defragment handles single organism already at start")
    void defragmentSingleOrganismAtStart() {
        for (int i = 0; i < 10; i++) soup.set(i, 1000 + i);
        
        Organism org = new Organism(0, 0, 10, -1, 0);
        List<Organism> organisms = List.of(org);
        
        // Simulate allocation
        memoryManager.allocate(10);
        
        int moved = defragmenter.defragment(new ArrayList<>(organisms));
        
        assertEquals(0, moved);  // Nothing to move
        assertEquals(0, org.getStartAddr());
    }
    
    @Test
    @DisplayName("rebuild creates single free block")
    void rebuildCreatesOneFreeBlock() {
        // Fragment memory
        memoryManager.allocate(10);
        memoryManager.allocate(20);
        memoryManager.free(0, 10);  // Create hole
        
        assertTrue(memoryManager.getFreeBlockCount() >= 2);
        
        // Rebuild with used memory ending at 50
        memoryManager.rebuild(50);
        
        assertEquals(1, memoryManager.getFreeBlockCount());
        assertEquals(SOUP_SIZE - 50, memoryManager.getFreeMemory());
        assertEquals(SOUP_SIZE - 50, memoryManager.getLargestFreeBlock());
    }
    
    @Test
    @DisplayName("statistics track correctly")
    void statisticsTrack() {
        // Create fragmented setup
        for (int i = 0; i < 10; i++) soup.set(100 + i, i);
        
        Organism org = new Organism(0, 100, 10, -1, 0);
        List<Organism> organisms = new ArrayList<>();
        organisms.add(org);
        
        assertEquals(0, defragmenter.getDefragmentationCount());
        assertEquals(0, defragmenter.getTotalOrganismsMoved());
        
        defragmenter.defragment(organisms);
        
        assertEquals(1, defragmenter.getDefragmentationCount());
        assertEquals(1, defragmenter.getTotalOrganismsMoved());
        assertEquals(10, defragmenter.getTotalCellsCompacted());
    }
}

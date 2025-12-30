package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class FreeListMemoryManagerTest {
    
    private FreeListMemoryManager mm;
    private static final int SOUP_SIZE = 1000;
    
    @BeforeEach
    void setUp() {
        mm = new FreeListMemoryManager(SOUP_SIZE);
    }
    
    // ========== Basic Allocation ==========
    
    @Nested
    @DisplayName("Basic Allocation")
    class BasicAllocation {
        
        @Test
        @DisplayName("Initial state has all memory free")
        void initialStateAllFree() {
            assertEquals(SOUP_SIZE, mm.getFreeMemory());
            assertEquals(SOUP_SIZE, mm.getLargestFreeBlock());
            assertEquals(0, mm.getUsedMemory());
            assertEquals(1, mm.getFreeBlockCount());
            assertEquals(0.0, mm.getFragmentation(), 0.001);
        }
        
        @Test
        @DisplayName("First allocation starts at address 0")
        void firstAllocationAtZero() {
            int addr = mm.allocate(100);
            assertEquals(0, addr);
        }
        
        @Test
        @DisplayName("Sequential allocations are contiguous")
        void sequentialAllocationsContiguous() {
            int addr1 = mm.allocate(100);
            int addr2 = mm.allocate(50);
            int addr3 = mm.allocate(25);
            
            assertEquals(0, addr1);
            assertEquals(100, addr2);
            assertEquals(150, addr3);
        }
        
        @Test
        @DisplayName("Allocation reduces free memory")
        void allocationReducesFreeMemory() {
            mm.allocate(100);
            assertEquals(SOUP_SIZE - 100, mm.getFreeMemory());
            assertEquals(100, mm.getUsedMemory());
        }
        
        @Test
        @DisplayName("Allocation of exact size removes block")
        void exactAllocationRemovesBlock() {
            mm.allocate(SOUP_SIZE);
            assertEquals(0, mm.getFreeMemory());
            assertEquals(0, mm.getFreeBlockCount());
        }
        
        @Test
        @DisplayName("Allocation fails when no space")
        void allocationFailsWhenNoSpace() {
            mm.allocate(SOUP_SIZE);
            int addr = mm.allocate(1);
            assertEquals(-1, addr);
        }
        
        @Test
        @DisplayName("Allocation fails when block too small")
        void allocationFailsWhenBlockTooSmall() {
            mm.allocate(SOUP_SIZE - 10);  // Leave only 10 free
            int addr = mm.allocate(20);   // Request 20
            assertEquals(-1, addr);
        }
        
        @Test
        @DisplayName("Invalid allocation size returns -1")
        void invalidAllocationSize() {
            assertEquals(-1, mm.allocate(0));
            assertEquals(-1, mm.allocate(-5));
        }
    }
    
    // ========== Free and Coalesce ==========
    
    @Nested
    @DisplayName("Free and Coalesce")
    class FreeAndCoalesce {
        
        @Test
        @DisplayName("Free returns memory to pool")
        void freeReturnsMemory() {
            int addr = mm.allocate(100);
            mm.free(addr, 100);
            
            assertEquals(SOUP_SIZE, mm.getFreeMemory());
        }
        
        @Test
        @DisplayName("Free creates new block in middle")
        void freeCreatesBlockInMiddle() {
            mm.allocate(100);  // addr 0
            mm.allocate(100);  // addr 100
            mm.allocate(100);  // addr 200
            
            mm.free(100, 100);  // Free middle block
            
            assertEquals(SOUP_SIZE - 200, mm.getFreeMemory());
            assertEquals(2, mm.getFreeBlockCount());  // Middle + end
        }
        
        @Test
        @DisplayName("Coalesce merges adjacent blocks - right neighbor")
        void coalesceMergesRightNeighbor() {
            mm.allocate(100);  // addr 0
            mm.allocate(100);  // addr 100
            // Free block at addr 200, size 800
            
            mm.free(100, 100);  // Free addr 100
            
            // Should merge with block at 200
            assertEquals(SOUP_SIZE - 100, mm.getFreeMemory());
            assertEquals(900, mm.getLargestFreeBlock());  // 100 + 800 merged
            assertEquals(1, mm.getFreeBlockCount());
        }
        
        @Test
        @DisplayName("Coalesce merges adjacent blocks - left neighbor")
        void coalesceMergesLeftNeighbor() {
            mm.allocate(100);  // addr 0
            mm.allocate(100);  // addr 100
            mm.allocate(100);  // addr 200
            
            mm.free(0, 100);      // Free first block → FreeList: [0-99], [300-999]
            mm.free(100, 100);    // Free second block — should merge with first
            
            // FreeList should be: [0-199, size=200], [300-999, size=700]
            assertEquals(2, mm.getFreeBlockCount());
            assertEquals(SOUP_SIZE - 100, mm.getFreeMemory());  // 900 free
            
            // Verify merged block can be allocated
            int addr = mm.allocate(200);
            assertEquals(0, addr);  // The merged block [0-199] should be used
        }
        
        @Test
        @DisplayName("Coalesce merges both neighbors")
        void coalesceMergesBothNeighbors() {
            mm.allocate(100);  // addr 0
            mm.allocate(100);  // addr 100
            mm.allocate(100);  // addr 200
            mm.allocate(100);  // addr 300
            
            mm.free(0, 100);    // Free first
            mm.free(200, 100);  // Free third
            // Now: free[0-99], used[100-199], free[200-299], used[300-399], free[400-999]
            assertEquals(3, mm.getFreeBlockCount());
            
            mm.free(100, 100);  // Free second - should merge with first AND third
            
            // Should have: free[0-299], used[300-399], free[400-999]
            assertEquals(2, mm.getFreeBlockCount());
            assertEquals(SOUP_SIZE - 100, mm.getFreeMemory());  // 900 free
            
            // Verify: can allocate 300 from the merged block
            int addr = mm.allocate(300);
            assertEquals(0, addr);  // Merged block [0-299] should be used
        }
        
        @Test
        @DisplayName("Free all memory returns to initial state")
        void freeAllReturnsToInitial() {
            int a1 = mm.allocate(100);
            int a2 = mm.allocate(200);
            int a3 = mm.allocate(300);
            
            mm.free(a2, 200);
            mm.free(a1, 100);
            mm.free(a3, 300);
            
            assertEquals(SOUP_SIZE, mm.getFreeMemory());
            assertEquals(SOUP_SIZE, mm.getLargestFreeBlock());
            assertEquals(1, mm.getFreeBlockCount());
        }
    }
    
    // ========== First Fit Behavior ==========
    
    @Nested
    @DisplayName("First Fit Behavior")
    class FirstFitBehavior {
        
        @Test
        @DisplayName("First Fit allocates from first suitable block")
        void firstFitAllocatesFromFirst() {
            mm.allocate(100);  // addr 0
            mm.allocate(100);  // addr 100
            mm.allocate(100);  // addr 200
            
            mm.free(0, 100);   // Free first block
            mm.free(200, 100); // Free third block
            // FreeList: [0-99], [200-299], [300-999]
            
            int addr = mm.allocate(50);
            assertEquals(0, addr);  // Should allocate from first block
        }
        
        @Test
        @DisplayName("First Fit skips blocks that are too small")
        void firstFitSkipsSmallBlocks() {
            mm.allocate(100);  // addr 0
            mm.allocate(100);  // addr 100
            mm.allocate(100);  // addr 200
            mm.allocate(100);  // addr 300 — чтобы не сливались!
            
            mm.free(0, 100);   // Free 100 cells at 0
            mm.free(200, 100); // Free 100 cells at 200
            // FreeList: [0-99, size=100], [200-299, size=100], [400-999, size=600]
            
            int addr = mm.allocate(150);  // Need 150, first two blocks too small
            assertEquals(400, addr);  // Should allocate from end block
        }
        
        @Test
        @DisplayName("Reuse freed blocks")
        void reuseFreedBlocks() {
            int a1 = mm.allocate(100);
            mm.allocate(100);  // addr 100
            
            mm.free(a1, 100);  // Free first block
            
            int a3 = mm.allocate(50);  // Should reuse freed space
            assertEquals(0, a3);
            
            int a4 = mm.allocate(50);  // Should use remainder
            assertEquals(50, a4);
        }
    }
    
    // ========== Fragmentation ==========
    
    @Nested
    @DisplayName("Fragmentation")
    class Fragmentation {
        
        @Test
        @DisplayName("No fragmentation when all free memory is contiguous")
        void noFragmentationWhenContiguous() {
            mm.allocate(100);
            // One free block of 900
            assertEquals(0.0, mm.getFragmentation(), 0.001);
        }
        
        @Test
        @DisplayName("High fragmentation when memory is scattered")
        void highFragmentationWhenScattered() {
            // Create checkerboard pattern: used-free-used-free-...
            for (int i = 0; i < 10; i++) {
                mm.allocate(50);  // Allocate
                mm.allocate(50);  // Allocate (will free)
            }
            // Free every other block
            for (int i = 0; i < 10; i++) {
                mm.free(50 + i * 100, 50);
            }
            
            // Free memory = 500 (10 blocks of 50)
            // Largest block = 50
            // Fragmentation = 1 - 50/500 = 0.9
            assertEquals(500, mm.getFreeMemory());
            assertEquals(50, mm.getLargestFreeBlock());
            assertEquals(0.9, mm.getFragmentation(), 0.001);
        }
        
        @Test
        @DisplayName("Fragmentation is 0 when no free memory")
        void zeroFragmentationWhenNoFreeMemory() {
            mm.allocate(SOUP_SIZE);
            assertEquals(0.0, mm.getFragmentation(), 0.001);
        }
    }
    
    // ========== Edge Cases ==========
    
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Constructor rejects non-positive size")
        void constructorRejectsNonPositiveSize() {
            assertThrows(IllegalArgumentException.class, 
                         () -> new FreeListMemoryManager(0));
            assertThrows(IllegalArgumentException.class, 
                         () -> new FreeListMemoryManager(-100));
        }
        
        @Test
        @DisplayName("Free ignores invalid addresses")
        void freeIgnoresInvalidAddresses() {
            int before = mm.getFreeMemory();
            
            mm.free(-1, 100);           // Negative address
            mm.free(SOUP_SIZE, 100);    // Beyond soup
            mm.free(0, 0);              // Zero size
            mm.free(0, -10);            // Negative size
            
            assertEquals(before, mm.getFreeMemory());
        }
        
        @Test
        @DisplayName("Many small allocations and frees")
        void manySmallAllocationsAndFrees() {
            // Allocate many small blocks
            int[] addresses = new int[100];
            for (int i = 0; i < 100; i++) {
                addresses[i] = mm.allocate(10);
                assertNotEquals(-1, addresses[i]);
            }
            
            // Free all
            for (int i = 0; i < 100; i++) {
                mm.free(addresses[i], 10);
            }
            
            // Should coalesce back to one block
            assertEquals(SOUP_SIZE, mm.getFreeMemory());
            assertEquals(1, mm.getFreeBlockCount());
        }
        
        @Test
        @DisplayName("toString provides useful info")
        void toStringProvidesInfo() {
            mm.allocate(100);
            String str = mm.toString();
            
            assertTrue(str.contains("total=" + SOUP_SIZE));
            assertTrue(str.contains("free=" + (SOUP_SIZE - 100)));
            assertTrue(str.contains("used=100"));
        }
    }
    
}

package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class BitmapMemoryManagerTest {
    
    private BitmapMemoryManager mm;
    private static final int SIZE = 1000;
    
    @BeforeEach
    void setUp() {
        mm = new BitmapMemoryManager(SIZE);
    }
    
    @Nested
    @DisplayName("Basic allocation")
    class BasicAllocation {
        
        @Test
        @DisplayName("Initial state: all memory free")
        void initialState() {
            assertEquals(SIZE, mm.getFreeMemory());
            assertEquals(0, mm.getUsedMemory());
            assertEquals(SIZE, mm.getLargestFreeBlock());
            assertEquals(1, mm.getFreeBlockCount());
        }
        
        @Test
        @DisplayName("Simple allocation works")
        void simpleAllocation() {
            int addr = mm.allocate(100);
            
            assertEquals(0, addr);
            assertEquals(100, mm.getUsedMemory());
            assertEquals(900, mm.getFreeMemory());
        }
        
        @Test
        @DisplayName("Multiple allocations are contiguous")
        void multipleAllocations() {
            int addr1 = mm.allocate(100);
            int addr2 = mm.allocate(100);
            int addr3 = mm.allocate(100);
            
            assertEquals(0, addr1);
            assertEquals(100, addr2);
            assertEquals(200, addr3);
            assertEquals(300, mm.getUsedMemory());
        }
        
        @Test
        @DisplayName("Allocation fails when not enough space")
        void allocationFailsWhenFull() {
            mm.allocate(SIZE);
            
            int addr = mm.allocate(1);
            
            assertEquals(-1, addr);
        }
        
        @Test
        @DisplayName("Allocation fails for invalid size")
        void allocationFailsForInvalidSize() {
            assertEquals(-1, mm.allocate(0));
            assertEquals(-1, mm.allocate(-1));
            assertEquals(-1, mm.allocate(SIZE + 1));
        }
    }
    
    @Nested
    @DisplayName("Free operations")
    class FreeOperations {
        
        @Test
        @DisplayName("Free releases memory")
        void freeReleasesMemory() {
            int addr = mm.allocate(100);
            mm.free(addr, 100);
            
            assertEquals(SIZE, mm.getFreeMemory());
            assertEquals(0, mm.getUsedMemory());
        }
        
        @Test
        @DisplayName("Double free is safe (no-op)")
        void doubleFreeIsSafe() {
            int addr = mm.allocate(100);
            mm.free(addr, 100);
            mm.free(addr, 100);  // Second free should be no-op
            
            assertEquals(SIZE, mm.getFreeMemory());
            assertEquals(0, mm.getUsedMemory());
        }
        
        @Test
        @DisplayName("Free of invalid address is safe")
        void freeInvalidAddressIsSafe() {
            mm.free(-1, 100);
            mm.free(SIZE + 100, 100);
            
            assertEquals(SIZE, mm.getFreeMemory());  // Nothing changed
        }
        
        @Test
        @DisplayName("Partial free works")
        void partialFree() {
            int addr = mm.allocate(100);
            mm.free(addr, 50);  // Free only half
            
            assertEquals(SIZE - 50, mm.getFreeMemory());
            assertEquals(50, mm.getUsedMemory());
        }
        
        @Test
        @DisplayName("Free creates hole that can be reused")
        void freeCreatesReusableHole() {
            mm.allocate(100);  // [0, 100)
            mm.allocate(100);  // [100, 200)
            mm.allocate(100);  // [200, 300)
            
            mm.free(100, 100);  // Free middle block
            
            // Should be able to allocate in the hole
            int addr = mm.allocate(100);
            assertEquals(100, addr);
        }
    }
    
    @Nested
    @DisplayName("Fragmentation")
    class Fragmentation {
        
        @Test
        @DisplayName("No fragmentation initially")
        void noInitialFragmentation() {
            assertEquals(0.0, mm.getFragmentation(), 0.001);
        }
        
        @Test
        @DisplayName("Fragmentation increases with holes")
        void fragmentationWithHoles() {
            // Create pattern: used-free-used-free-used
            mm.allocate(100);  // [0, 100) used
            mm.allocate(100);  // [100, 200) used
            mm.allocate(100);  // [200, 300) used
            
            mm.free(100, 100);  // [100, 200) free - creates fragmentation
            
            // Free memory = 100 (hole) + 700 (end) = 800
            // Largest block = 700
            // Fragmentation = 1 - 700/800 = 0.125
            double frag = mm.getFragmentation();
            assertTrue(frag > 0, "Should have some fragmentation");
            assertTrue(frag < 0.2, "Fragmentation should be modest");
        }
        
        @Test
        @DisplayName("getFreeBlockCount reflects holes")
        void freeBlockCount() {
            mm.allocate(100);
            mm.allocate(100);
            mm.allocate(100);
            
            assertEquals(1, mm.getFreeBlockCount());  // One block at end
            
            mm.free(100, 100);  // Create hole
            
            assertEquals(2, mm.getFreeBlockCount());  // Hole + end
        }
    }
    
    @Nested
    @DisplayName("Memory accounting - NO NEGATIVE VALUES")
    class MemoryAccounting {
        
        @Test
        @DisplayName("Used memory is never negative")
        void usedMemoryNeverNegative() {
            // Allocate and free multiple times
            for (int i = 0; i < 100; i++) {
                int addr = mm.allocate(10);
                if (addr >= 0) {
                    mm.free(addr, 10);
                }
            }
            
            assertTrue(mm.getUsedMemory() >= 0, "Used memory should never be negative");
            assertTrue(mm.getFreeMemory() <= SIZE, "Free memory should not exceed total");
        }
        
        @Test
        @DisplayName("Aggressive double-free doesn't cause negative")
        void aggressiveDoubleFree() {
            int addr1 = mm.allocate(100);
            int addr2 = mm.allocate(100);
            
            // Free same memory multiple times
            mm.free(addr1, 100);
            mm.free(addr1, 100);
            mm.free(addr1, 100);
            
            mm.free(addr2, 200);  // Wrong size
            mm.free(addr2, 100);
            
            assertTrue(mm.getUsedMemory() >= 0);
            assertEquals(mm.getTotalMemory(), mm.getUsedMemory() + mm.getFreeMemory());
        }
        
        @Test
        @DisplayName("Total memory is constant")
        void totalMemoryConstant() {
            mm.allocate(100);
            mm.allocate(200);
            mm.free(0, 100);
            mm.allocate(50);
            
            assertEquals(SIZE, mm.getTotalMemory());
            assertEquals(SIZE, mm.getUsedMemory() + mm.getFreeMemory());
        }
    }
    
    @Nested
    @DisplayName("Rebuild for defragmentation")
    class Rebuild {
        
        @Test
        @DisplayName("Rebuild clears all memory")
        void rebuildClearsAll() {
            mm.allocate(100);
            mm.allocate(200);
            mm.allocate(300);
            
            mm.rebuild(0);
            
            assertEquals(SIZE, mm.getFreeMemory());
            assertEquals(0, mm.getUsedMemory());
        }
        
        @Test
        @DisplayName("markUsed after rebuild")
        void markUsedAfterRebuild() {
            mm.allocate(100);
            mm.rebuild(0);
            mm.markUsed(0, 50);
            mm.markUsed(50, 50);
            
            assertEquals(100, mm.getUsedMemory());
            assertEquals(SIZE - 100, mm.getFreeMemory());
        }
    }
    
    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {
        
        @Test
        @DisplayName("Allocate exact size")
        void allocateExactSize() {
            int addr = mm.allocate(SIZE);
            
            assertEquals(0, addr);
            assertEquals(0, mm.getFreeMemory());
            assertEquals(SIZE, mm.getUsedMemory());
        }
        
        @Test
        @DisplayName("Free at boundary")
        void freeAtBoundary() {
            int addr = mm.allocate(SIZE);
            mm.free(addr, SIZE);
            
            assertEquals(SIZE, mm.getFreeMemory());
        }
        
        @Test
        @DisplayName("Small soup size")
        void smallSoupSize() {
            BitmapMemoryManager small = new BitmapMemoryManager(10);
            
            assertEquals(10, small.getTotalMemory());
            assertEquals(10, small.allocate(10));
            assertEquals(-1, small.allocate(1));
        }
    }
}

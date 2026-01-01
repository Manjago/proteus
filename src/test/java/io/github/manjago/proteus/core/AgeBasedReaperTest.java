package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import static org.junit.jupiter.api.Assertions.*;

class AgeBasedReaperTest {
    
    private BitmapMemoryManager memoryManager;
    private AgeBasedReaper reaper;
    
    private static final int SOUP_SIZE = 1000;
    private static final int ORGANISM_SIZE = 13;
    
    @BeforeEach
    void setUp() {
        memoryManager = new BitmapMemoryManager(SOUP_SIZE);
        reaper = new AgeBasedReaper(memoryManager);
    }
    
    /**
     * Helper to create an organism and allocate its memory.
     */
    private Organism createOrganism(int id, int parentId, long birthCycle) {
        int addr = memoryManager.allocate(ORGANISM_SIZE);
        assertNotEquals(-1, addr, "Should be able to allocate");
        return new Organism(id, addr, ORGANISM_SIZE, parentId, birthCycle);
    }
    
    // ========== Registration ==========
    
    @Nested
    @DisplayName("Registration")
    class Registration {
        
        @Test
        @DisplayName("Register adds organism to queue")
        void registerAddsToQueue() {
            Organism org = createOrganism(0, -1, 0);
            reaper.register(org);
            
            assertEquals(1, reaper.getQueueSize());
        }
        
        @Test
        @DisplayName("Register multiple organisms")
        void registerMultiple() {
            for (int i = 0; i < 10; i++) {
                Organism org = createOrganism(i, -1, i * 10);
                reaper.register(org);
            }
            
            assertEquals(10, reaper.getQueueSize());
        }
        
        @Test
        @DisplayName("Register null is ignored")
        void registerNullIgnored() {
            reaper.register(null);
            assertEquals(0, reaper.getQueueSize());
        }
        
        @Test
        @DisplayName("Unregister uses lazy deletion (organism stays in queue until reaped)")
        void unregisterUsesLazyDeletion() {
            Organism org = createOrganism(0, -1, 0);
            reaper.register(org);
            
            // Kill the organism (simulating death)
            org.kill();
            memoryManager.free(org.getStartAddr(), org.getSize());
            
            // Unregister - with lazy deletion, still in queue but marked dead
            reaper.unregister(org);
            
            // getQueueSize() filters dead organisms, so should be 0
            assertEquals(0, reaper.getQueueSize());
            
            // But reap() should skip it and return null (no alive organisms)
            assertNull(reaper.reap());
        }
        
        @Test
        @DisplayName("Unregister non-existent is safe")
        void unregisterNonExistent() {
            Organism org = createOrganism(0, -1, 0);
            // Don't register, just unregister
            reaper.unregister(org);
            
            assertEquals(0, reaper.getQueueSize());
        }
    }
    
    // ========== Reaping ==========
    
    @Nested
    @DisplayName("Reaping")
    class Reaping {
        
        @Test
        @DisplayName("Reap kills oldest organism")
        void reapKillsOldest() {
            Organism old = createOrganism(0, -1, 100);
            Organism young = createOrganism(1, 0, 200);
            
            reaper.register(old);
            reaper.register(young);
            
            Organism victim = reaper.reap();
            
            assertEquals(old, victim);
            assertFalse(old.isAlive());
            assertTrue(young.isAlive());
        }
        
        @Test
        @DisplayName("Reap frees memory")
        void reapFreesMemory() {
            Organism org = createOrganism(0, -1, 0);
            int freeBefore = memoryManager.getFreeMemory();
            
            reaper.register(org);
            reaper.reap();
            
            int freeAfter = memoryManager.getFreeMemory();
            assertEquals(freeBefore + ORGANISM_SIZE, freeAfter);
        }
        
        @Test
        @DisplayName("Reap increments counter")
        void reapIncrementsCounter() {
            Organism org = createOrganism(0, -1, 0);
            reaper.register(org);
            
            assertEquals(0, reaper.getReapCount());
            reaper.reap();
            assertEquals(1, reaper.getReapCount());
        }
        
        @Test
        @DisplayName("Reap empty queue returns null")
        void reapEmptyReturnsNull() {
            Organism victim = reaper.reap();
            assertNull(victim);
        }
        
        @Test
        @DisplayName("Reap skips already dead organisms")
        void reapSkipsDead() {
            Organism dead = createOrganism(0, -1, 100);
            Organism alive = createOrganism(1, 0, 200);
            
            dead.kill(); // Kill before registering (died from errors)
            
            reaper.register(dead);
            reaper.register(alive);
            
            Organism victim = reaper.reap();
            
            assertEquals(alive, victim); // Should reap the alive one
            assertEquals(1, reaper.getReapCount());
        }
        
        @Test
        @DisplayName("Reap order is by birth cycle (FIFO)")
        void reapOrderIsFifo() {
            Organism org1 = createOrganism(1, -1, 100);
            Organism org2 = createOrganism(2, -1, 50);  // Born earlier
            Organism org3 = createOrganism(3, -1, 150);
            
            reaper.register(org1);
            reaper.register(org2);
            reaper.register(org3);
            
            // Should reap in order: org2 (50), org1 (100), org3 (150)
            assertEquals(org2, reaper.reap());
            assertEquals(org1, reaper.reap());
            assertEquals(org3, reaper.reap());
        }
    }
    
    // ========== ReapUntilFree ==========
    
    @Nested
    @DisplayName("ReapUntilFree")
    class ReapUntilFreeTests {
        
        @Test
        @DisplayName("ReapUntilFree kills enough to get sufficient total free memory")
        void reapUntilFreeKillsEnough() {
            // Fill memory almost completely: 76 organisms × 13 = 988 cells
            // Leaves only 12 free cells at the end
            for (int i = 0; i < 76; i++) {
                Organism org = createOrganism(i, -1, i);
                reaper.register(org);
            }
            
            // Only 12 cells free, need 100
            assertEquals(SOUP_SIZE - 76 * ORGANISM_SIZE, memoryManager.getFreeMemory());
            
            // Try to allocate more than available
            int addr = memoryManager.allocate(100);
            assertEquals(-1, addr); // Should fail
            
            // Reap until totalFree >= 100 (new logic stops when defrag could help)
            int killed = reaper.reapUntilFree(100);
            
            // Should kill enough to get totalFree >= 100
            // With 12 initial free + killed*13 >= 100, need at least 7 kills
            assertTrue(killed >= 7, "Should kill at least 7 organisms, killed: " + killed);
            assertTrue(memoryManager.getFreeMemory() >= 100,
                    "Total free should be >= 100, was: " + memoryManager.getFreeMemory());
            
            // Note: largestBlock might still be < 100 if blocks don't coalesce,
            // but defragmentation would consolidate them
        }
        
        @Test
        @DisplayName("ReapUntilFree returns 0 if already enough space")
        void reapUntilFreeNoActionIfEnough() {
            Organism org = createOrganism(0, -1, 0);
            reaper.register(org);
            
            // Plenty of space available
            int killed = reaper.reapUntilFree(10);
            
            assertEquals(0, killed);
            assertTrue(org.isAlive());
        }
        
        @Test
        @DisplayName("ReapUntilFree stops when total free memory is enough (defrag can help)")
        void reapUntilFreeStopsWhenTotalFreeEnough() {
            // Use organism size 10 so 1000 divides evenly into 100 organisms
            final int ORG_SIZE = 10;
            
            // Create 100 organisms that fill memory exactly
            Organism[] orgs = new Organism[100];
            for (int i = 0; i < 100; i++) {
                int addr = memoryManager.allocate(ORG_SIZE);
                assertNotEquals(-1, addr, "Should allocate organism " + i);
                orgs[i] = new Organism(i, addr, ORG_SIZE, -1, i);
                reaper.register(orgs[i]);
            }
            
            // Memory is full
            assertEquals(0, memoryManager.getFreeMemory());
            
            // Kill odd indices: creates 50 free blocks of 10 cells each
            // Each separated by an alive organism (even indices)
            for (int i = 1; i < 100; i += 2) {
                orgs[i].kill();
                reaper.unregister(orgs[i]);
                memoryManager.free(orgs[i].getStartAddr(), orgs[i].getSize());
            }
            
            // Now: 50 alive orgs, 50 free blocks of 10 cells = 500 free
            // Largest block = 10 (no merging because separated by alive orgs)
            assertEquals(500, memoryManager.getFreeMemory());
            assertEquals(10, memoryManager.getLargestFreeBlock(), 
                         "Largest block should be 10 (fragmented)");
            
            // Request 25 cells - with new logic, reaper stops when totalFree >= size
            // because defragmentation can consolidate the free memory
            int killed = reaper.reapUntilFree(25);
            
            // Should NOT kill anyone - totalFree (500) >= size (25)
            assertEquals(0, killed, "Should not kill anyone when totalFree >= size");
            
            // Largest block is still 10, but that's OK - defragmentation will fix it
            assertEquals(10, memoryManager.getLargestFreeBlock());
        }
        
        @Test
        @DisplayName("ReapUntilFree kills when total free is insufficient")
        void reapUntilFreeKillsWhenTotalFreeInsufficient() {
            // Fill memory completely
            final int ORG_SIZE = 100;
            Organism[] orgs = new Organism[10];
            for (int i = 0; i < 10; i++) {
                int addr = memoryManager.allocate(ORG_SIZE);
                orgs[i] = new Organism(i, addr, ORG_SIZE, -1, i);
                reaper.register(orgs[i]);
            }
            
            assertEquals(0, memoryManager.getFreeMemory());
            
            // Request 150 cells - need to kill at least 2 organisms
            int killed = reaper.reapUntilFree(150);
            
            // Should kill enough to get totalFree >= 150
            assertTrue(killed >= 2, "Should kill at least 2 organisms");
            assertTrue(memoryManager.getFreeMemory() >= 150, 
                       "Total free should be at least 150");
        }
    }
    
    // ========== Statistics ==========
    
    @Nested
    @DisplayName("Statistics")
    class Statistics {
        
        @Test
        @DisplayName("GetOldestAge returns max age of alive organisms")
        void getOldestAgeReturnsMax() {
            Organism org1 = createOrganism(1, -1, 0);
            Organism org2 = createOrganism(2, -1, 100);
            
            // Simulate some aging
            for (int i = 0; i < 500; i++) org1.getState().incrementAge();
            for (int i = 0; i < 300; i++) org2.getState().incrementAge();
            
            reaper.register(org1);
            reaper.register(org2);
            
            assertEquals(500, reaper.getOldestAge());
        }
        
        @Test
        @DisplayName("GetAverageAgeAtDeath tracks correctly")
        void getAverageAgeAtDeathTracks() {
            Organism org1 = createOrganism(1, -1, 0);
            Organism org2 = createOrganism(2, -1, 100);
            
            // Age them differently
            for (int i = 0; i < 100; i++) org1.getState().incrementAge();
            for (int i = 0; i < 200; i++) org2.getState().incrementAge();
            
            reaper.register(org1);
            reaper.register(org2);
            
            reaper.reap(); // Kill org1 (age 100)
            reaper.reap(); // Kill org2 (age 200)
            
            assertEquals(150.0, reaper.getAverageAgeAtDeath(), 0.001);
        }
        
        @Test
        @DisplayName("ToString provides useful info")
        void toStringProvidesInfo() {
            Organism org = createOrganism(0, -1, 0);
            reaper.register(org);
            
            String str = reaper.toString();
            
            assertTrue(str.contains("reaped=0"));
            assertTrue(str.contains("queueSize=1"));
        }
    }
    
    // ========== Cleanup ==========
    
    @Nested
    @DisplayName("Cleanup")
    class Cleanup {
        
        @Test
        @DisplayName("getRawQueueSize includes dead organisms")
        void getRawQueueSizeIncludesDead() {
            Organism org1 = createOrganism(0, -1, 0);
            Organism org2 = createOrganism(1, -1, 1);
            reaper.register(org1);
            reaper.register(org2);
            
            // Kill one but don't remove from queue (lazy deletion)
            org1.kill();
            memoryManager.free(org1.getStartAddr(), org1.getSize());
            reaper.unregister(org1);
            
            // Queue still has both
            assertEquals(2, reaper.getRawQueueSize());
            // But only one is alive
            assertEquals(1, reaper.getQueueSize());
        }
        
        @Test
        @DisplayName("cleanup removes dead organisms")
        void cleanupRemovesDead() {
            Organism org1 = createOrganism(0, -1, 0);
            Organism org2 = createOrganism(1, -1, 1);
            Organism org3 = createOrganism(2, -1, 2);
            reaper.register(org1);
            reaper.register(org2);
            reaper.register(org3);
            
            // Kill two
            org1.kill();
            org3.kill();
            
            assertEquals(3, reaper.getRawQueueSize());
            
            // Cleanup
            int removed = reaper.cleanup();
            
            assertEquals(2, removed);
            assertEquals(1, reaper.getRawQueueSize());
            assertEquals(1, reaper.getQueueSize());
        }
        
        @Test
        @DisplayName("cleanup on empty queue returns 0")
        void cleanupEmptyQueue() {
            assertEquals(0, reaper.cleanup());
        }
        
        @Test
        @DisplayName("cleanup with all alive returns 0")
        void cleanupAllAlive() {
            Organism org1 = createOrganism(0, -1, 0);
            Organism org2 = createOrganism(1, -1, 1);
            reaper.register(org1);
            reaper.register(org2);
            
            assertEquals(0, reaper.cleanup());
            assertEquals(2, reaper.getRawQueueSize());
        }
    }
}

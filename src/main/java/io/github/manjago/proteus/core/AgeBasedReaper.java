package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * Age-based Reaper implementation.
 * 
 * Kills the oldest organisms first (FIFO by birth time).
 * This is the classic Tierra approach - organisms that have lived
 * longest are reaped first, giving younger organisms a chance to reproduce.
 * 
 * Evolutionary pressure: organisms that reproduce quickly before
 * getting reaped will have more descendants.
 */
public class AgeBasedReaper implements Reaper {
    
    private static final Logger log = LoggerFactory.getLogger(AgeBasedReaper.class);
    
    private final MemoryManager memoryManager;
    
    // Priority queue sorted by birth cycle (oldest first = smallest birthCycle)
    private final PriorityQueue<Organism> queue;
    
    private int reapCount = 0;
    private long totalAgeAtDeath = 0;
    
    /**
     * Create a new Reaper.
     * 
     * @param memoryManager the memory manager to free memory through
     */
    public AgeBasedReaper(MemoryManager memoryManager) {
        this.memoryManager = memoryManager;
        // Oldest first: sort by birthCycle ascending
        this.queue = new PriorityQueue<>(Comparator.comparingLong(Organism::getBirthCycle));
    }
    
    @Override
    public void register(Organism organism) {
        if (organism == null) {
            log.warn("Attempted to register null organism");
            return;
        }
        queue.add(organism);
        log.debug("Registered {} (queue size: {})", organism.toShortString(), queue.size());
    }
    
    @Override
    public void unregister(Organism organism) {
        if (organism == null) {
            return;
        }
        boolean removed = queue.remove(organism);
        if (removed) {
            log.debug("Unregistered {} (queue size: {})", organism.toShortString(), queue.size());
        }
    }
    
    @Override
    public Organism reap() {
        // Find the oldest ALIVE organism
        Organism victim = null;
        while (!queue.isEmpty()) {
            Organism candidate = queue.poll();
            if (candidate.isAlive()) {
                victim = candidate;
                break;
            }
            // Skip already dead organisms (died from errors)
        }
        
        if (victim == null) {
            log.debug("No organisms to reap");
            return null;
        }
        
        // Kill it
        victim.kill();
        long age = victim.getAge();
        totalAgeAtDeath += age;
        reapCount++;
        
        // Free pending allocation (memory allocated for child but not spawned)
        CpuState state = victim.getState();
        if (state.hasPendingAllocation()) {
            memoryManager.free(state.getPendingAllocAddr(), state.getPendingAllocSize());
            state.clearPendingAllocation();
        }
        
        // Free its memory (only if size is valid)
        if (victim.getSize() > 0) {
            memoryManager.free(victim.getStartAddr(), victim.getSize());
        }
        
        log.debug("Reaped {} (age={}, freed {} cells)", 
                  victim.toShortString(), age, victim.getSize());
        
        return victim;
    }
    
    @Override
    public int reapUntilFree(int size) {
        int killed = 0;
        int freedMemory = 0;
        int maxKills = 100;  // Safety limit to prevent killing everyone
        
        // Keep reaping until we have enough contiguous memory
        // OR enough total free memory (defragmentation can help)
        while (memoryManager.getLargestFreeBlock() < size && 
               memoryManager.getFreeMemory() < size &&  // Stop if defrag could help
               !queue.isEmpty() && 
               killed < maxKills) {
            Organism victim = reap();
            if (victim != null) {
                killed++;
                freedMemory += victim.getSize();
            } else {
                break; // No more organisms to reap
            }
        }
        
        if (killed > 0) {
            log.debug("Reaped {} organisms to free {} cells (needed {}, largestBlock={}, totalFree={})", 
                      killed, freedMemory, size, 
                      memoryManager.getLargestFreeBlock(),
                      memoryManager.getFreeMemory());
        }
        
        return killed;
    }
    
    @Override
    public int getReapCount() {
        return reapCount;
    }
    
    @Override
    public int getQueueSize() {
        // Count only alive organisms
        return (int) queue.stream().filter(Organism::isAlive).count();
    }
    
    @Override
    public int getOldestAge() {
        // Find oldest alive organism
        return queue.stream()
                .filter(Organism::isAlive)
                .mapToInt(o -> (int) o.getAge())
                .max()
                .orElse(0);
    }
    
    /**
     * Get average age at death for reaped organisms.
     * 
     * @return average age, or 0 if none reaped yet
     */
    public double getAverageAgeAtDeath() {
        if (reapCount == 0) {
            return 0;
        }
        return (double) totalAgeAtDeath / reapCount;
    }
    
    @Override
    public String toString() {
        return String.format("Reaper[reaped=%d, queueSize=%d, avgAge=%.0f]",
                reapCount, getQueueSize(), getAverageAgeAtDeath());
    }
}

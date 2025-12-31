package io.github.manjago.proteus.core;

/**
 * Reaper - the "grim reaper" that kills organisms to free memory.
 * 
 * In Tierra-style artificial life, the Reaper is essential for:
 * - Freeing memory from old organisms
 * - Creating evolutionary pressure (survival of the fittest)
 * - Preventing population stagnation
 * 
 * Without a Reaper, memory fills up and evolution stops.
 */
public interface Reaper {
    
    /**
     * Register a newly spawned organism with the Reaper.
     * Called when SPAWN creates a new organism.
     * 
     * @param organism the new organism to track
     */
    void register(Organism organism);
    
    /**
     * Unregister an organism that died from other causes (e.g., too many errors).
     * This prevents the Reaper from trying to kill an already dead organism.
     * 
     * @param organism the organism that died
     */
    void unregister(Organism organism);
    
    /**
     * Kill one organism (the oldest/worst) and free its memory.
     * 
     * @return the killed organism, or null if no organisms to reap
     */
    Organism reap();
    
    /**
     * Keep reaping until at least 'size' cells are freed.
     * Used when allocate() fails and we need to make room.
     * 
     * @param size minimum amount of memory to free
     * @return number of organisms killed
     */
    int reapUntilFree(int size);
    
    /**
     * Get the total number of organisms killed by the Reaper.
     * 
     * @return reap count
     */
    int getReapCount();
    
    /**
     * Get the number of organisms currently tracked.
     * 
     * @return queue size
     */
    int getQueueSize();
    
    /**
     * Get the age of the oldest organism in the queue.
     * 
     * @return oldest age, or 0 if queue is empty
     */
    int getOldestAge();
    
    /**
     * Get raw queue size including dead organisms (for lazy deletion implementations).
     * Used to monitor memory pressure.
     * 
     * @return raw queue size
     */
    default int getRawQueueSize() {
        return getQueueSize();  // Default: same as alive count
    }
    
    /**
     * Clean up dead organisms from internal data structures.
     * Called periodically to prevent memory accumulation from lazy deletion.
     * 
     * @return number of dead organisms removed
     */
    default int cleanup() {
        return 0;  // Default: no-op for non-lazy implementations
    }
}

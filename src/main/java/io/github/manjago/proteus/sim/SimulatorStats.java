package io.github.manjago.proteus.sim;

/**
 * Snapshot of simulation statistics.
 */
public record SimulatorStats(
    long totalCycles,
    int totalSpawns,
    int rejectedSpawns,
    int deathsByErrors,
    int deathsByReaper,
    int failedAllocations,
    long aliveCount,
    int maxAlive,
    int totalOrganisms,
    int memoryUsed,
    int memoryFree,
    int memoryLeak,
    int largestFreeBlock,
    double fragmentation,
    int totalMutations,
    int defragmentations,
    int reaperQueueSize,      // Raw queue size (includes dead for lazy deletion)
    long heapUsedMB,          // JVM heap used in MB
    long heapMaxMB            // JVM heap max in MB
) {
    
    /**
     * Calculate cycles per spawn (reproduction efficiency).
     */
    public double cyclesPerSpawn() {
        return totalSpawns > 0 ? (double) totalCycles / totalSpawns : 0;
    }
    
    /**
     * Calculate death rate (deaths per 1000 cycles).
     */
    public double deathRatePer1000() {
        int totalDeaths = deathsByErrors + deathsByReaper;
        return totalCycles > 0 ? totalDeaths * 1000.0 / totalCycles : 0;
    }
    
    /**
     * Calculate mutation rate per spawn.
     */
    public double mutationsPerSpawn() {
        return totalSpawns > 0 ? (double) totalMutations / totalSpawns : 0;
    }
    
    /**
     * Get heap usage percentage.
     */
    public double heapUsagePercent() {
        return heapMaxMB > 0 ? 100.0 * heapUsedMB / heapMaxMB : 0;
    }
    
    @Override
    public String toString() {
        return String.format("""
            === Simulation Statistics ===
            Cycles:           %,d
            Spawns:           %,d (%.1f cycles/spawn)
            Rejected spawns:  %,d
            Deaths:
              By errors:      %,d
              By reaper:      %,d
              Total:          %,d (%.2f per 1K cycles)
            Allocations failed: %d
            
            Population:
              Alive:          %,d
              Max alive:      %,d
              Total ever:     %,d
            
            Memory:
              Used:           %,d / %,d (%.1f%%)
              Largest free:   %,d
              Fragmentation:  %.1f%%
              Defragmentations: %,d
              Leak detected:  %,d cells
            
            Mutations:        %,d total (%.2f per spawn)
            
            JVM:
              Heap:           %,d / %,d MB (%.1f%%)
              Reaper queue:   %,d
            """,
            totalCycles,
            totalSpawns, cyclesPerSpawn(),
            rejectedSpawns,
            deathsByErrors,
            deathsByReaper,
            deathsByErrors + deathsByReaper, deathRatePer1000(),
            failedAllocations,
            aliveCount,
            maxAlive,
            totalOrganisms,
            memoryUsed, memoryUsed + memoryFree,
            100.0 * memoryUsed / (memoryUsed + memoryFree),
            largestFreeBlock,
            fragmentation * 100,
            defragmentations,
            memoryLeak,
            totalMutations, mutationsPerSpawn(),
            heapUsedMB, heapMaxMB, heapUsagePercent(),
            reaperQueueSize
        );
    }
}

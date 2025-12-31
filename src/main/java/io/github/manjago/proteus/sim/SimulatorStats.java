package io.github.manjago.proteus.sim;

/**
 * Snapshot of simulation statistics.
 */
public record SimulatorStats(
    long totalCycles,
    int totalSpawns,
    int deathsByErrors,
    int deathsByReaper,
    int failedAllocations,
    long aliveCount,
    int totalOrganisms,
    int memoryUsed,
    int memoryFree,
    int largestFreeBlock,
    double fragmentation,
    int totalMutations
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
    
    @Override
    public String toString() {
        return String.format("""
            === Simulation Statistics ===
            Cycles:           %,d
            Spawns:           %,d (%.1f cycles/spawn)
            Deaths:
              By errors:      %,d
              By reaper:      %,d
              Total:          %,d (%.2f per 1K cycles)
            Allocations failed: %d
            
            Population:
              Alive:          %,d
              Total ever:     %,d
            
            Memory:
              Used:           %,d / %,d (%.1f%%)
              Largest free:   %,d
              Fragmentation:  %.1f%%
            
            Mutations:        %,d total (%.2f per spawn)
            """,
            totalCycles,
            totalSpawns, cyclesPerSpawn(),
            deathsByErrors,
            deathsByReaper,
            deathsByErrors + deathsByReaper, deathRatePer1000(),
            failedAllocations,
            aliveCount,
            totalOrganisms,
            memoryUsed, memoryUsed + memoryFree,
            100.0 * memoryUsed / (memoryUsed + memoryFree),
            largestFreeBlock,
            fragmentation * 100,
            totalMutations, mutationsPerSpawn()
        );
    }
}

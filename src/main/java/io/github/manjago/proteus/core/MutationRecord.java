package io.github.manjago.proteus.core;

/**
 * Record of a single mutation event.
 * 
 * Captures what happened when a COPY instruction mutated a value.
 */
public record MutationRecord(
    long cycle,           // When the mutation occurred
    int sourceAddr,       // Address being copied FROM
    int destAddr,         // Address being copied TO (where mutation landed)
    int originalValue,    // Original instruction value
    int mutatedValue      // Mutated instruction value
) {
    
    /**
     * Check if this mutation affects a given memory range.
     */
    public boolean affectsRange(int start, int size) {
        return destAddr >= start && destAddr < start + size;
    }
    
    /**
     * Get the offset within an organism's genome.
     */
    public int offsetFrom(int startAddr) {
        return destAddr - startAddr;
    }
    
    @Override
    public String toString() {
        return String.format("Mutation[cycle=%d, %d→%d, 0x%08X→0x%08X]",
                cycle, sourceAddr, destAddr, originalValue, mutatedValue);
    }
}

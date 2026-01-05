package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;

/**
 * Memory defragmenter for the soup.
 * 
 * Compacts all living organisms to the beginning of memory,
 * eliminating fragmentation. This is safe with ISA v1.2's
 * position-independent code - organisms continue executing
 * correctly after being moved.
 * 
 * <p><b>Threading:</b> Single-threaded by design for determinism.
 * 
 * Works exclusively with BitmapMemoryManager for guaranteed
 * memory accounting correctness.
 * 
 * Algorithm:
 * 1. Sort alive organisms by their current startAddr
 * 2. Clear all memory ownership (rebuild)
 * 3. Compact each organism to the lowest available address
 * 4. Mark each organism's new position as used
 */
public class Defragmenter {
    
    private static final Logger log = LoggerFactory.getLogger(Defragmenter.class);
    
    private final int[] soup;
    private final BitmapMemoryManager memoryManager;
    
    // Statistics
    private int defragmentations = 0;
    private int totalMoved = 0;
    private long totalBytesCompacted = 0;
    
    public Defragmenter(int[] soup, BitmapMemoryManager memoryManager) {
        this.soup = soup;
        this.memoryManager = memoryManager;
    }
    
    /**
     * Check if defragmentation is needed.
     * 
     * Triggers when:
     * - Fragmentation > threshold AND
     * - Largest free block < minimum required size
     * 
     * @param minRequiredSize minimum size needed for allocation (e.g., 14 for ancestor)
     * @param fragmentationThreshold trigger when fragmentation exceeds this (0.0-1.0)
     * @return true if defragmentation should be performed
     */
    public boolean needsDefragmentation(int minRequiredSize, double fragmentationThreshold) {
        double fragmentation = memoryManager.getFragmentation();
        int largestFree = memoryManager.getLargestFreeBlock();
        
        return fragmentation > fragmentationThreshold && largestFree < minRequiredSize;
    }
    
    /**
     * Perform defragmentation on the given list of alive organisms.
     * 
     * Uses two-pass approach:
     * 1. First pass: validate all organisms can be compacted
     * 2. Second pass: actually move them (only if first pass succeeds)
     * 
     * @param aliveOrganisms list of living organisms (will be sorted in place)
     * @return number of organisms that were actually moved
     */
    public int defragment(List<Organism> aliveOrganisms) {
        if (aliveOrganisms.isEmpty()) {
            log.debug("No organisms to defragment");
            return 0;
        }
        
        long startTime = System.nanoTime();
        double fragBefore = memoryManager.getFragmentation();
        int blocksBefore = memoryManager.getFreeBlockCount();
        
        // Sort by current address for stable compaction
        aliveOrganisms.sort(Comparator.comparingInt(Organism::getStartAddr));
        
        int soupSize = memoryManager.getTotalMemory();
        
        // === PASS 1: Validate that all organisms can fit ===
        int totalSize = 0;
        for (Organism org : aliveOrganisms) {
            int size = org.getSize();
            int oldAddr = org.getStartAddr();
            
            // Skip invalid organisms (they won't be moved)
            if (size <= 0 || size > 1000 || oldAddr < 0 || oldAddr + size > soupSize) {
                log.warn("Invalid organism found: id={}, addr={}, size={} - aborting defrag", 
                        org.getId(), oldAddr, size);
                return 0;  // Abort - can't safely defragment with invalid organisms
            }
            
            totalSize += size;
        }
        
        // Check if all organisms fit
        if (totalSize > soupSize) {
            log.debug("Cannot defragment: total organism size {} exceeds soup size {}", 
                    totalSize, soupSize);
            return 0;
        }
        
        // === PASS 2: Actually move organisms ===
        int movedCount = 0;
        int nextFreeAddr = 0;
        
        // Clear all ownership first
        memoryManager.rebuild(0);
        
        for (Organism org : aliveOrganisms) {
            int oldAddr = org.getStartAddr();
            int size = org.getSize();
            
            if (oldAddr != nextFreeAddr) {
                // Need to move this organism
                moveOrganism(org, oldAddr, nextFreeAddr, size);
                movedCount++;
                totalBytesCompacted += size;
            }
            
            // Mark new position as used and update organism's allocId
            int newAllocId = memoryManager.markUsed(nextFreeAddr, size);
            org.setAllocId(newAllocId);
            
            nextFreeAddr += size;
        }
        
        defragmentations++;
        totalMoved += movedCount;
        
        long elapsed = System.nanoTime() - startTime;
        double fragAfter = memoryManager.getFragmentation();
        
        log.info("Defragmentation #{}: moved {} organisms in {}ms, " +
                 "fragmentation {}% -> {}%, blocks {} -> {}", 
                 defragmentations, movedCount, 
                 String.format("%.2f", elapsed / 1_000_000.0),
                 String.format("%.1f", fragBefore * 100), 
                 String.format("%.1f", fragAfter * 100), 
                 blocksBefore, memoryManager.getFreeBlockCount());
        
        return movedCount;
    }
    
    /**
     * Move an organism's genome from oldAddr to newAddr.
     */
    private void moveOrganism(Organism org, int oldAddr, int newAddr, int size) {
        // Copy genome to new location
        // Note: we copy forward (low to high) since newAddr < oldAddr during compaction
        for (int i = 0; i < size; i++) {
            int instruction = soup[oldAddr + i];
            soup[newAddr + i] = instruction;
        }
        
        // Update organism's address (this also updates CpuState.startAddr)
        org.setStartAddr(newAddr);
        
        log.trace("Moved organism #{} from {} to {} (size {})", 
                  org.getId(), oldAddr, newAddr, size);
    }
    
    // ========== Statistics ==========
    
    public int getDefragmentationCount() {
        return defragmentations;
    }
    
    public int getTotalOrganismsMoved() {
        return totalMoved;
    }
    
    public long getTotalCellsCompacted() {
        return totalBytesCompacted;
    }
    
    @Override
    public String toString() {
        return String.format("Defragmenter[runs=%d, moved=%d, cells=%d]",
                defragmentations, totalMoved, totalBytesCompacted);
    }
}

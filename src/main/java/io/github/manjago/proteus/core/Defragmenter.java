package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Memory defragmenter for the soup.
 * 
 * Compacts all living organisms to the beginning of memory,
 * eliminating fragmentation. This is safe with ISA v1.2's
 * position-independent code - organisms continue executing
 * correctly after being moved.
 * 
 * Algorithm:
 * 1. Sort alive organisms by their current startAddr
 * 2. Compact each organism to the lowest available address
 * 3. Update each organism's startAddr (and CpuState.startAddr)
 * 4. Rebuild the memory manager's free list
 */
public class Defragmenter {
    
    private static final Logger log = LoggerFactory.getLogger(Defragmenter.class);
    
    private final AtomicIntegerArray soup;
    private final MemoryManager memoryManager;
    
    // Statistics
    private int defragmentations = 0;
    private int totalMoved = 0;
    private long totalBytesCompacted = 0;
    
    public Defragmenter(AtomicIntegerArray soup, MemoryManager memoryManager) {
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
     * @param minRequiredSize minimum size needed for allocation (e.g., Adam.SIZE)
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
        
        int movedCount = 0;
        int nextFreeAddr = 0;
        int soupSize = memoryManager.getTotalMemory();
        
        for (Organism org : aliveOrganisms) {
            int oldAddr = org.getStartAddr();
            int size = org.getSize();
            
            // Sanity check: skip organisms with invalid size or address
            if (size <= 0 || size > 1000 || oldAddr < 0 || oldAddr + size > soupSize) {
                log.warn("Skipping invalid organism in defrag: id={}, addr={}, size={}", 
                        org.getId(), oldAddr, size);
                continue;
            }
            
            // Safety: ensure we don't overflow
            if (nextFreeAddr + size > soupSize) {
                log.error("Defragmentation overflow! nextFreeAddr={}, size={}, soupSize={}", 
                        nextFreeAddr, size, soupSize);
                break;
            }
            
            if (oldAddr != nextFreeAddr) {
                // Need to move this organism
                moveOrganism(org, oldAddr, nextFreeAddr, size);
                movedCount++;
                totalBytesCompacted += size;
            }
            
            nextFreeAddr += size;
        }
        
        // Rebuild free list with single contiguous block at the end
        memoryManager.rebuild(nextFreeAddr);
        
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
            int instruction = soup.get(oldAddr + i);
            soup.set(newAddr + i, instruction);
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

package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Bitmap-based memory manager with single source of truth.
 * 
 * Instead of tracking free blocks (which can get out of sync),
 * we track ownership of each cell. This makes it impossible to:
 * - Double-free (freeing already free cell is no-op)
 * - Lose memory (every cell is either free or has an owner)
 * - Get negative memory usage
 * 
 * Memory calculation is always correct: count non-free cells.
 * 
 * Trade-off: O(n) scan for allocation, but this is acceptable
 * for soup sizes up to ~1M cells.
 */
public class BitmapMemoryManager implements MemoryManager {
    
    private static final Logger log = LoggerFactory.getLogger(BitmapMemoryManager.class);
    
    /** Marker for free cells */
    private static final int FREE = -1;
    
    /** 
     * Ownership array: FREE (-1) or allocation ID (>= 0).
     * Each cell knows who owns it.
     */
    private final int[] ownership;
    
    private final int totalSize;
    
    /** Counter for unique allocation IDs */
    private int nextAllocationId = 0;
    
    /** Statistics */
    private int totalAllocations = 0;
    private int totalFrees = 0;
    
    public BitmapMemoryManager(int size) {
        if (size <= 0) {
            throw new IllegalArgumentException("Size must be positive: " + size);
        }
        this.totalSize = size;
        this.ownership = new int[size];
        
        // Initialize all cells as free
        for (int i = 0; i < size; i++) {
            ownership[i] = FREE;
        }
        
        log.debug("BitmapMemoryManager created with {} cells", size);
    }
    
    /** Next-fit: position to start searching from */
    private int nextFitPosition = 0;
    
    @Override
    public int allocate(int size) {
        if (size <= 0 || size > totalSize) {
            return -1;
        }
        
        // Next-fit scan: start from last position, wrap around if needed
        int start = findFreeBlock(size, nextFitPosition);
        if (start == -1 && nextFitPosition > 0) {
            // Wrap around: try from beginning
            start = findFreeBlock(size, 0);
        }
        
        if (start == -1) {
            log.trace("Allocation failed: no contiguous block of size {}", size);
            return -1;
        }
        
        // SANITY CHECK: verify the block is actually free
        for (int i = start; i < start + size; i++) {
            if (ownership[i] != FREE) {
                log.error("BUG: findFreeBlock returned non-free block! addr={}, cell={}, owner={}", 
                        start, i, ownership[i]);
                return -1;  // Don't corrupt!
            }
        }
        
        // Mark cells as owned by this allocation
        int allocId = nextAllocationId++;
        for (int i = start; i < start + size; i++) {
            ownership[i] = allocId;
        }
        
        // Update next-fit position to after this allocation
        nextFitPosition = start + size;
        if (nextFitPosition >= totalSize) {
            nextFitPosition = 0;
        }
        
        totalAllocations++;
        log.trace("Allocated {} cells at addr {} (allocId={})", size, start, allocId);
        
        return start;
    }
    
    /**
     * Get the allocId of the last successful allocation.
     * Used to track ownership of pending allocations.
     */
    @Override
    public int getLastAllocId() {
        return nextAllocationId - 1;
    }
    
    /**
     * Free only cells that belong to specific allocId.
     * Used when spawn is rejected but part of pending memory was overwritten.
     * 
     * @param addr start address
     * @param size expected size
     * @param allocId the allocation ID to free
     * @return number of cells actually freed
     */
    public int freeByAllocId(int addr, int size, int allocId) {
        if (addr < 0 || size <= 0 || allocId < 0) {
            return 0;
        }
        
        int freed = 0;
        int end = Math.min(addr + size, totalSize);
        
        for (int i = addr; i < end; i++) {
            if (ownership[i] == allocId) {
                ownership[i] = FREE;
                freed++;
            }
        }
        
        if (freed > 0) {
            totalFrees++;
            if (addr < nextFitPosition) {
                nextFitPosition = addr;
            }
            log.trace("freeByAllocId({}, {}, {}): freed {} cells", addr, size, allocId, freed);
        }
        
        return freed;
    }
    
    /**
     * Find contiguous free block of given size, starting from specified position.
     * 
     * @param size required size
     * @param startFrom position to start scanning from
     * @return start address, or -1 if not found before end of array
     */
    private int findFreeBlock(int size, int startFrom) {
        int consecutiveFree = 0;
        int blockStart = -1;
        
        for (int i = startFrom; i < totalSize; i++) {
            if (ownership[i] == FREE) {
                if (consecutiveFree == 0) {
                    blockStart = i;
                }
                consecutiveFree++;
                
                if (consecutiveFree >= size) {
                    return blockStart;
                }
            } else {
                consecutiveFree = 0;
                blockStart = -1;
            }
        }
        
        return -1;
    }
    
    @Override
    public void free(int addr, int size) {
        if (addr < 0 || size <= 0) {
            log.warn("Invalid free request: addr={}, size={}", addr, size);
            return;
        }
        
        int freed = 0;
        int end = Math.min(addr + size, totalSize);
        
        for (int i = addr; i < end; i++) {
            if (ownership[i] != FREE) {
                ownership[i] = FREE;
                freed++;
            }
            // If already FREE - do nothing (prevents double-free issues)
        }
        
        // Optimization: if freeing before current position, move back
        // This helps reuse holes instead of always allocating forward
        if (freed > 0 && addr < nextFitPosition) {
            nextFitPosition = addr;
        }
        
        if (freed > 0) {
            totalFrees++;
            log.trace("Freed {} cells at addr {} (requested {})", freed, addr, size);
        } else {
            log.trace("Free no-op at addr {} size {} (already free)", addr, size);
        }
    }
    
    /**
     * Safe free: only releases memory if ALL cells in range belong to the SAME owner.
     * This prevents accidentally freeing another organism's memory when pending
     * allocation overlaps with an existing organism.
     * 
     * @param addr start address
     * @param size block size  
     * @return true if freed, false if range has mixed ownership or is already free
     */
    @Override
    public boolean freeIfOwned(int addr, int size) {
        if (addr < 0 || size <= 0 || addr + size > totalSize) {
            return false;
        }
        
        // Check that ALL cells have the SAME allocId (not FREE, not mixed)
        int expectedId = ownership[addr];
        if (expectedId == FREE) {
            log.trace("freeIfOwned({}, {}): first cell already FREE", addr, size);
            return false;  // Already free
        }
        
        for (int i = addr + 1; i < addr + size; i++) {
            if (ownership[i] != expectedId) {
                log.debug("freeIfOwned({}, {}): mixed ownership at cell {} (expected {}, found {})",
                        addr, size, i, expectedId, ownership[i]);
                return false;  // Mixed ownership - don't free!
            }
        }
        
        // All cells belong to same owner - safe to free
        for (int i = addr; i < addr + size; i++) {
            ownership[i] = FREE;
        }
        
        if (addr < nextFitPosition) {
            nextFitPosition = addr;
        }
        
        totalFrees++;
        log.trace("freeIfOwned({}, {}): freed allocId={}", addr, size, expectedId);
        return true;
    }
    
    @Override
    public int getFreeMemory() {
        int count = 0;
        for (int v : ownership) {
            if (v == FREE) {
                count++;
            }
        }
        return count;
    }
    
    @Override
    public int getUsedMemory() {
        return totalSize - getFreeMemory();
    }
    
    @Override
    public int getLargestFreeBlock() {
        int maxBlock = 0;
        int currentBlock = 0;
        
        for (int v : ownership) {
            if (v == FREE) {
                currentBlock++;
                maxBlock = Math.max(maxBlock, currentBlock);
            } else {
                currentBlock = 0;
            }
        }
        
        return maxBlock;
    }
    
    @Override
    public int getTotalMemory() {
        return totalSize;
    }
    
    @Override
    public int getFreeBlockCount() {
        int blocks = 0;
        boolean inFreeBlock = false;
        
        for (int v : ownership) {
            if (v == FREE) {
                if (!inFreeBlock) {
                    blocks++;
                    inFreeBlock = true;
                }
            } else {
                inFreeBlock = false;
            }
        }
        
        return blocks;
    }
    
    @Override
    public void rebuild(int usedEnd) {
        // After defragmentation:
        // - Cells [0, usedEnd) contain moved organisms (already marked by defragmenter)
        // - Cells [usedEnd, totalSize) should be free
        //
        // We clear EVERYTHING first, then the defragmenter will re-mark used cells.
        // This is safer than trying to track which cells changed.
        
        for (int i = 0; i < totalSize; i++) {
            ownership[i] = FREE;
        }
        
        // Reset next-fit position (will be updated by markUsed calls)
        nextFitPosition = 0;
        
        log.debug("Rebuilt: cleared all {} cells", totalSize);
    }
    
    /**
     * Mark a range as used by defragmented organisms.
     * Called by Defragmenter after moving each organism.
     * 
     * @param addr start address
     * @param size block size
     * @return the allocId assigned to this range
     */
    public int markUsed(int addr, int size) {
        if (addr < 0 || size <= 0) return -1;
        
        int allocId = nextAllocationId++;
        int end = Math.min(addr + size, totalSize);
        for (int i = addr; i < end; i++) {
            ownership[i] = allocId;
        }
        
        // Update next-fit position to after this marked region
        nextFitPosition = end;
        if (nextFitPosition >= totalSize) {
            nextFitPosition = 0;
        }
        
        log.trace("Marked [{}, {}) as used (allocId={})", addr, end, allocId);
        return allocId;
    }
    
    /**
     * Check if entire range belongs to a SINGLE owner (same allocId).
     * Used to verify spawn didn't write over someone else's memory.
     * 
     * @param addr start address
     * @param size block size
     * @return true if all cells have same non-FREE owner
     */
    public boolean hasConsistentOwnership(int addr, int size) {
        if (addr < 0 || size <= 0 || addr + size > totalSize) {
            return false;
        }
        
        int expectedId = ownership[addr];
        if (expectedId == FREE) {
            return false;  // First cell is free - not owned
        }
        
        for (int i = addr + 1; i < addr + size; i++) {
            if (ownership[i] != expectedId) {
                return false;  // Mixed ownership
            }
        }
        
        return true;
    }
    
    /**
     * Get owner of a specific cell (for debugging).
     * 
     * @param addr cell address
     * @return FREE (-1) or allocation ID
     */
    public int getOwner(int addr) {
        if (addr < 0 || addr >= totalSize) {
            return FREE;
        }
        return ownership[addr];
    }
    
    /**
     * Check if a range is completely free.
     */
    public boolean isRangeFree(int addr, int size) {
        if (addr < 0 || addr + size > totalSize) {
            return false;
        }
        for (int i = addr; i < addr + size; i++) {
            if (ownership[i] != FREE) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Count how many cells in range are actually marked as used.
     * For debugging memory leaks.
     */
    public int countUsedInRange(int addr, int size) {
        if (addr < 0 || size <= 0) return 0;
        int count = 0;
        int end = Math.min(addr + size, totalSize);
        for (int i = addr; i < end; i++) {
            if (ownership[i] != FREE) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * Get statistics string (for debugging).
     */
    public String getStatsString() {
        return String.format(
            "BitmapMM[total=%d, used=%d, free=%d, blocks=%d, allocs=%d, frees=%d]",
            totalSize, getUsedMemory(), getFreeMemory(), 
            getFreeBlockCount(), totalAllocations, totalFrees
        );
    }
    
    @Override
    public String toString() {
        return getStatsString();
    }
}

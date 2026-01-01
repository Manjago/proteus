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
        
        if (freed > 0) {
            totalFrees++;
            log.trace("Freed {} cells at addr {} (requested {})", freed, addr, size);
        } else {
            log.trace("Free no-op at addr {} size {} (already free)", addr, size);
        }
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
     */
    public void markUsed(int addr, int size) {
        if (addr < 0 || size <= 0) return;
        
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

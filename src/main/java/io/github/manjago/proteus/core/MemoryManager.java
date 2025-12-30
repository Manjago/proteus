package io.github.manjago.proteus.core;

/**
 * Memory manager for the soup (shared memory space).
 * 
 * Responsible for allocating and freeing memory blocks for organisms.
 * Implementations should handle fragmentation and coalescing of free blocks.
 */
public interface MemoryManager {
    
    /**
     * Allocate a contiguous block of memory.
     * 
     * @param size number of cells to allocate
     * @return start address of allocated block, or -1 if allocation failed
     */
    int allocate(int size);
    
    /**
     * Free a previously allocated block of memory.
     * 
     * @param addr start address of the block
     * @param size size of the block
     */
    void free(int addr, int size);
    
    /**
     * Get total free memory available.
     * Note: This may be fragmented across multiple blocks.
     * 
     * @return total free cells
     */
    int getFreeMemory();
    
    /**
     * Get the size of the largest contiguous free block.
     * This determines the maximum size organism that can be allocated.
     * 
     * @return size of largest free block, or 0 if no free memory
     */
    int getLargestFreeBlock();
    
    /**
     * Get total memory capacity.
     * 
     * @return total cells in the soup
     */
    int getTotalMemory();
    
    /**
     * Get used memory (allocated).
     * 
     * @return used cells
     */
    default int getUsedMemory() {
        return getTotalMemory() - getFreeMemory();
    }
    
    /**
     * Calculate fragmentation ratio.
     * 
     * @return 0.0 (no fragmentation) to 1.0 (highly fragmented)
     */
    default double getFragmentation() {
        int freeMemory = getFreeMemory();
        if (freeMemory == 0) {
            return 0.0;
        }
        int largestBlock = getLargestFreeBlock();
        return 1.0 - ((double) largestBlock / freeMemory);
    }
    
    /**
     * Get number of free blocks (for diagnostics).
     * 
     * @return count of separate free blocks
     */
    int getFreeBlockCount();
}

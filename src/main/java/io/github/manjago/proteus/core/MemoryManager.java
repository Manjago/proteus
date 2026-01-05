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
    
    /**
     * Rebuild free list after defragmentation.
     * Sets up a single free block starting at usedEnd.
     * 
     * @param usedEnd address where used memory ends (free space starts here)
     */
    void rebuild(int usedEnd);
    
    /**
     * Safe free: only releases memory if the range belongs to a single owner.
     * Default implementation falls back to regular free().
     * 
     * @param addr start address
     * @param size block size
     * @return true if freed, false if ownership check failed
     */
    default boolean freeIfOwned(int addr, int size) {
        free(addr, size);
        return true;
    }
    
    /**
     * Free only cells that belong to specific allocId.
     * Used when spawn is rejected but part of pending memory was overwritten.
     * Default implementation falls back to freeIfOwned().
     * 
     * @param addr start address
     * @param size expected size
     * @param allocId the allocation ID to free
     * @return number of cells actually freed
     */
    default int freeByAllocId(int addr, int size, int allocId) {
        freeIfOwned(addr, size);
        return size;  // Approximation
    }
    
    /**
     * Get the allocId of the last successful allocation.
     * Used to track ownership for safe cleanup.
     * 
     * @return allocId, or -1 if not available
     */
    default int getLastAllocId() {
        return -1;
    }
    
    /**
     * Mark memory range as used (for checkpoint restore).
     * Unlike allocate(), this marks a SPECIFIC address range.
     * 
     * @param addr start address
     * @param size block size
     * @return the allocId assigned to this range, or -1 if failed
     */
    default int markUsed(int addr, int size) {
        // Default: not supported, subclasses should override
        return -1;
    }
    
    /**
     * Get owner (allocId) at specific address (for diagnostics).
     * 
     * @param addr address to check
     * @return allocId, 0 for free, -1 if not available
     */
    default int getOwnerAt(int addr) {
        return -1;  // Not available by default
    }
    
    /**
     * Check if memory range has consistent ownership (single owner or all free).
     * Used to detect when organism wrote over someone else's memory.
     * 
     * @param addr start address
     * @param size block size
     * @return true if consistent, false if mixed ownership
     */
    default boolean hasConsistentOwnership(int addr, int size) {
        return true;  // Assume consistent by default
    }
}

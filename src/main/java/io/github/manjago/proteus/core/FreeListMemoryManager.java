package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Memory manager using Free List with First Fit allocation strategy.
 * 
 * Features:
 * - First Fit: allocates from the first block that fits
 * - Coalescing: merges adjacent free blocks on free()
 * - O(n) allocate and free operations
 * 
 * The free list is kept sorted by address for efficient coalescing.
 */
public class FreeListMemoryManager implements MemoryManager {
    
    private static final Logger log = LoggerFactory.getLogger(FreeListMemoryManager.class);
    
    private final int totalMemory;
    private final List<FreeBlock> freeList;
    
    /**
     * A contiguous block of free memory.
     */
    private record FreeBlock(int addr, int size) {
        @Override
        public String toString() {
            return String.format("[%d-%d, size=%d]", addr, addr + size - 1, size);
        }
    }
    
    /**
     * Create a new memory manager with all memory initially free.
     * 
     * @param totalMemory total size of the soup
     */
    public FreeListMemoryManager(int totalMemory) {
        if (totalMemory <= 0) {
            throw new IllegalArgumentException("Total memory must be positive");
        }
        this.totalMemory = totalMemory;
        this.freeList = new ArrayList<>();
        // Initially, all memory is one big free block
        this.freeList.add(new FreeBlock(0, totalMemory));
    }
    
    @Override
    public int allocate(int size) {
        if (size <= 0) {
            log.warn("Invalid allocation request: size={}", size);
            return -1;
        }
        
        // First Fit: find first block that fits
        for (int i = 0; i < freeList.size(); i++) {
            FreeBlock block = freeList.get(i);
            
            if (block.size >= size) {
                int allocatedAddr = block.addr;
                
                if (block.size == size) {
                    // Exact fit - remove the block
                    freeList.remove(i);
                } else {
                    // Split the block - replace with remainder
                    FreeBlock remainder = new FreeBlock(block.addr + size, block.size - size);
                    freeList.set(i, remainder);
                }
                
                log.debug("Allocated {} cells at address {}", size, allocatedAddr);
                return allocatedAddr;
            }
        }
        
        log.debug("Allocation failed: requested {} cells, largest block is {}", 
                  size, getLargestFreeBlock());
        return -1;
    }
    
    @Override
    public void free(int addr, int size) {
        if (size <= 0) {
            log.warn("Invalid free request: size={}", size);
            return;
        }
        if (addr < 0 || addr + size > totalMemory) {
            log.warn("Invalid free request: addr={}, size={}, totalMemory={}", 
                     addr, size, totalMemory);
            return;
        }
        
        // Find insertion point (keep list sorted by address)
        int insertIdx = 0;
        for (int i = 0; i < freeList.size(); i++) {
            if (freeList.get(i).addr > addr) {
                break;
            }
            insertIdx = i + 1;
        }
        
        // Create the new free block
        FreeBlock newBlock = new FreeBlock(addr, size);
        freeList.add(insertIdx, newBlock);
        
        log.debug("Freed {} cells at address {}", size, addr);
        
        // Coalesce with neighbors
        coalesce(insertIdx);
    }
    
    /**
     * Merge the block at index with its neighbors if they are adjacent.
     */
    private void coalesce(int index) {
        // First, try to merge with the next block (right neighbor)
        if (index < freeList.size() - 1) {
            FreeBlock current = freeList.get(index);
            FreeBlock next = freeList.get(index + 1);
            
            if (current.addr + current.size == next.addr) {
                // Merge: current absorbs next
                FreeBlock merged = new FreeBlock(current.addr, current.size + next.size);
                freeList.set(index, merged);
                freeList.remove(index + 1);
                log.debug("Coalesced blocks: {} + {} -> {}", current, next, merged);
            }
        }
        
        // Then, try to merge with the previous block (left neighbor)
        if (index > 0) {
            FreeBlock prev = freeList.get(index - 1);
            FreeBlock current = freeList.get(index);
            
            if (prev.addr + prev.size == current.addr) {
                // Merge: prev absorbs current
                FreeBlock merged = new FreeBlock(prev.addr, prev.size + current.size);
                freeList.set(index - 1, merged);
                freeList.remove(index);
                log.debug("Coalesced blocks: {} + {} -> {}", prev, current, merged);
            }
        }
    }
    
    @Override
    public int getFreeMemory() {
        return freeList.stream()
                .mapToInt(FreeBlock::size)
                .sum();
    }
    
    @Override
    public int getLargestFreeBlock() {
        return freeList.stream()
                .mapToInt(FreeBlock::size)
                .max()
                .orElse(0);
    }
    
    @Override
    public int getTotalMemory() {
        return totalMemory;
    }
    
    @Override
    public int getFreeBlockCount() {
        return freeList.size();
    }
    
    /**
     * Get a snapshot of the free list for diagnostics.
     * 
     * @return string representation of free blocks
     */
    public String getFreeListSnapshot() {
        if (freeList.isEmpty()) {
            return "FreeList: [empty]";
        }
        StringBuilder sb = new StringBuilder("FreeList: ");
        for (int i = 0; i < freeList.size(); i++) {
            if (i > 0) sb.append(", ");
            sb.append(freeList.get(i));
        }
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return String.format("MemoryManager[total=%d, free=%d, used=%d, blocks=%d, frag=%.1f%%]",
                totalMemory, getFreeMemory(), getUsedMemory(), 
                getFreeBlockCount(), getFragmentation() * 100);
    }
}

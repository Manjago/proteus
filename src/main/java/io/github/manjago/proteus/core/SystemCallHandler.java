package io.github.manjago.proteus.core;

/**
 * Handler for system calls (ALLOCATE, SPAWN).
 * 
 * These operations require interaction with the simulation environment
 * (memory manager, scheduler), so they are delegated to this interface.
 */
public interface SystemCallHandler {
    
    /**
     * Allocate a block of memory for offspring.
     * 
     * @param requestedSize size of memory block requested
     * @return start address of allocated block, or -1 if allocation failed
     */
    int allocate(int requestedSize);
    
    /**
     * Spawn a new organism.
     * 
     * @param address start address of the new organism's code
     * @param size size of the new organism's genome
     * @param parentState CPU state of the parent (for genealogy tracking)
     * @return true if spawn succeeded, false otherwise
     */
    boolean spawn(int address, int size, CpuState parentState);
    
    /**
     * Free a pending allocation (when ALLOCATE is called again before SPAWN).
     * Default implementation does nothing (for backward compatibility).
     * 
     * @param address address of the pending allocation
     * @param size size of the pending allocation
     * @param allocId allocId for precise cleanup (-1 if unknown)
     */
    default void freePending(int address, int size, int allocId) {
        // Default: no-op for backward compatibility
    }
    
    /**
     * @deprecated Use freePending(int, int, int) instead
     */
    @Deprecated
    default void freePending(int address, int size) {
        freePending(address, size, -1);
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
     * Default no-op handler that fails all system calls.
     * Useful for testing basic CPU operations.
     */
    SystemCallHandler FAILING = new SystemCallHandler() {
        @Override
        public int allocate(int requestedSize) {
            return -1; // Always fail
        }
        
        @Override
        public boolean spawn(int address, int size, CpuState parentState) {
            return false; // Always fail
        }
    };
}

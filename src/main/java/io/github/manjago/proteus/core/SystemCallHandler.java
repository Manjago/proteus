package io.github.manjago.proteus.core;

/**
 * Handler for system calls (ALLOCATE, SPAWN).
 * <p>
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

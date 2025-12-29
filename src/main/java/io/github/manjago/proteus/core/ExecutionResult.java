package io.github.manjago.proteus.core;

/**
 * Result of executing a single instruction.
 */
public enum ExecutionResult {
    
    /** Instruction executed successfully */
    OK(false),
    
    /** IP pointed outside memory bounds */
    ERROR_IP_OUT_OF_BOUNDS(true),
    
    /** Unknown opcode encountered */
    ERROR_UNKNOWN_OPCODE(true),
    
    /** Memory access (LOAD/STORE/COPY) outside bounds */
    ERROR_MEMORY_OUT_OF_BOUNDS(true),
    
    /** ALLOCATE syscall succeeded */
    SYSCALL_ALLOCATE_OK(false),
    
    /** ALLOCATE syscall failed (no free memory) */
    SYSCALL_ALLOCATE_FAILED(false),
    
    /** SPAWN syscall succeeded - new organism created */
    SYSCALL_SPAWN_OK(false),
    
    /** SPAWN syscall failed */
    SYSCALL_SPAWN_FAILED(false);
    
    private final boolean isError;
    
    ExecutionResult(boolean isError) {
        this.isError = isError;
    }
    
    /**
     * @return true if this result indicates an error condition
     */
    public boolean isError() {
        return isError;
    }
    
    /**
     * @return true if this result indicates successful execution
     */
    public boolean isSuccess() {
        return !isError;
    }
}

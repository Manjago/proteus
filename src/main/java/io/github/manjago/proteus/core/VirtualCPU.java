package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicIntegerArray;

import static io.github.manjago.proteus.core.OpCode.*;

/**
 * Virtual CPU interpreter for Proteus ISA v1.2
 * 
 * Executes instructions from shared memory ("soup").
 * Each organism has its own CpuState but shares the memory.
 * 
 * <h2>v1.2 Changes:</h2>
 * <ul>
 *   <li>IP is now relative (absolute = startAddr + IP)</li>
 *   <li>JMP/JMPZ/JLT use IP-relative offsets encoded in instruction</li>
 *   <li>LOAD/STORE use startAddr-relative addressing</li>
 *   <li>Added GETADDR instruction</li>
 * </ul>
 */
public final class VirtualCPU {
    
    private static final Logger log = LoggerFactory.getLogger(VirtualCPU.class);
    
    /** Probability of mutation during COPY instruction (0.0 to 1.0) */
    private final double mutationRate;
    
    /** Random number generator for mutations (GameRng for deterministic save/restore) */
    private final GameRng random;
    
    /** Handler for system calls (ALLOCATE, SPAWN) */
    private final SystemCallHandler syscallHandler;
    
    /** Optional mutation tracker for recording mutations */
    private MutationTracker mutationTracker;
    
    /**
     * Create VirtualCPU with default settings.
     * Mutation rate: 0.1% (0.001)
     */
    public VirtualCPU() {
        this(0.001, new GameRng(System.currentTimeMillis()), SystemCallHandler.FAILING);
    }
    
    /**
     * Create VirtualCPU with custom mutation rate.
     */
    public VirtualCPU(double mutationRate) {
        this(mutationRate, new GameRng(System.currentTimeMillis()), SystemCallHandler.FAILING);
    }
    
    /**
     * Create VirtualCPU with full configuration.
     */
    public VirtualCPU(double mutationRate, GameRng random, SystemCallHandler syscallHandler) {
        this.mutationRate = mutationRate;
        this.random = random;
        this.syscallHandler = syscallHandler;
    }
    
    /**
     * Execute a single instruction (ISA v1.2).
     * 
     * @param state CPU state of the organism
     * @param memory shared memory ("soup")
     * @return execution result
     */
    public ExecutionResult execute(CpuState state, AtomicIntegerArray memory) {
        // v1.2: Use absolute IP for memory access
        int absoluteIp = state.getAbsoluteIp();
        
        // Bounds check for IP
        if (absoluteIp < 0 || absoluteIp >= memory.length()) {
            state.incrementErrors();
            return ExecutionResult.ERROR_IP_OUT_OF_BOUNDS;
        }
        
        int instruction = memory.get(absoluteIp);
        OpCode op = decodeOpCode(instruction);
        
        if (op == null) {
            state.incrementErrors();
            state.advanceIp();
            state.incrementAge();
            return ExecutionResult.ERROR_UNKNOWN_OPCODE;
        }
        
        int r1 = decodeR1(instruction);
        int r2 = decodeR2(instruction);
        int r3 = decodeR3(instruction);
        int r4 = decodeR4(instruction);
        
        ExecutionResult result = executeOp(op, instruction, r1, r2, r3, r4, state, memory);
        
        state.incrementAge();
        return result;
    }
    
    private ExecutionResult executeOp(OpCode op, int instruction, int r1, int r2, int r3, int r4,
                                       CpuState state, AtomicIntegerArray memory) {
        switch (op) {
            case NOP -> {
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case MOV -> {
                state.setRegister(r1, state.getRegister(r2));
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case MOVI -> {
                int immediate = decodeImmediate(instruction);
                state.setRegister(r1, immediate);
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            // v1.2: GETADDR - get organism's absolute start address
            case GETADDR -> {
                state.setRegister(r1, state.getStartAddr());
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case ADD -> {
                int a = state.getRegister(r1);
                int b = state.getRegister(r2);
                state.setRegister(r1, a + b);
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case SUB -> {
                int a = state.getRegister(r1);
                int b = state.getRegister(r2);
                state.setRegister(r1, a - b);
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case INC -> {
                state.setRegister(r1, state.getRegister(r1) + 1);
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case DEC -> {
                state.setRegister(r1, state.getRegister(r1) - 1);
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            // v1.2: LOAD - startAddr-relative addressing
            case LOAD -> {
                int offset = state.getRegister(r2);
                int addr = state.getStartAddr() + offset;  // v1.2: relative!
                if (addr < 0 || addr >= memory.length()) {
                    state.incrementErrors();
                    state.advanceIp();
                    return ExecutionResult.ERROR_MEMORY_OUT_OF_BOUNDS;
                }
                state.setRegister(r1, memory.get(addr));
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            // v1.2: STORE - startAddr-relative addressing
            case STORE -> {
                int offset = state.getRegister(r1);
                int addr = state.getStartAddr() + offset;  // v1.2: relative!
                if (addr < 0 || addr >= memory.length()) {
                    state.incrementErrors();
                    state.advanceIp();
                    return ExecutionResult.ERROR_MEMORY_OUT_OF_BOUNDS;
                }
                memory.set(addr, state.getRegister(r2));
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            // v1.2: JMP - IP-relative jump
            case JMP -> {
                int offset = decodeOffset(instruction);
                state.advanceIp();  // First advance past this instruction
                state.jumpRelative(offset);  // Then apply offset
                return ExecutionResult.OK;
            }
            
            // v1.2: JMPZ - IP-relative jump if R_cond == 0
            case JMPZ -> {
                if (state.getRegister(r1) == 0) {
                    int offset = decodeOffset(instruction);
                    state.advanceIp();
                    state.jumpRelative(offset);
                } else {
                    state.advanceIp();
                }
                return ExecutionResult.OK;
            }
            
            // v1.2: JLT - IP-relative jump if R_a < R_b
            case JLT -> {
                int a = state.getRegister(r1);
                int b = state.getRegister(r2);
                if (a < b) {
                    int offset = decodeOffset(instruction);
                    state.advanceIp();
                    state.jumpRelative(offset);
                } else {
                    state.advanceIp();
                }
                return ExecutionResult.OK;
            }
            
            // COPY uses absolute addresses (for writing to child)
            case COPY -> {
                return executeCopy(r1, r2, state, memory);
            }
            
            case ALLOCATE -> {
                return executeAllocate(r1, r2, state);
            }
            
            case SPAWN -> {
                return executeSpawn(r1, r2, state);
            }
            
            case SEARCH -> {
                return executeSearch(r1, r2, r3, r4, state, memory);
            }
            
            default -> {
                state.incrementErrors();
                state.advanceIp();
                return ExecutionResult.ERROR_UNKNOWN_OPCODE;
            }
        }
    }
    
    /**
     * COPY instruction - copies one memory cell with possible mutation.
     * memory[R_dst] = memory[R_src] (possibly mutated)
     */
    private ExecutionResult executeCopy(int r1, int r2, CpuState state, AtomicIntegerArray memory) {
        int srcAddr = state.getRegister(r1);
        int dstAddr = state.getRegister(r2);
        
        // Bounds check
        if (srcAddr < 0 || srcAddr >= memory.length() ||
            dstAddr < 0 || dstAddr >= memory.length()) {
            state.incrementErrors();
            state.advanceIp();
            return ExecutionResult.ERROR_MEMORY_OUT_OF_BOUNDS;
        }
        
        int originalValue = memory.get(srcAddr);
        int value = originalValue;
        
        // MUTATION happens here!
        if (random.nextDouble() < mutationRate) {
            value = mutate(value);
            log.debug("Mutation at copy from {} to {}: value mutated", srcAddr, dstAddr);
            
            // Record mutation if tracker is set
            if (mutationTracker != null) {
                mutationTracker.record(srcAddr, dstAddr, originalValue, value);
            }
        }
        
        memory.set(dstAddr, value);
        state.advanceIp();
        return ExecutionResult.OK;
    }
    
    /**
     * Apply a random mutation to an instruction.
     * Flips a random bit.
     */
    private int mutate(int value) {
        int bitToFlip = random.nextInt(32);
        return value ^ (1 << bitToFlip);
    }
    
    /**
     * ALLOCATE instruction - request memory for offspring.
     * R_size = requested size, R_addr = result address (or -1 on failure)
     */
    private ExecutionResult executeAllocate(int r1, int r2, CpuState state) {
        int requestedSize = state.getRegister(r1);
        
        // If there's already a pending allocation, free it first (prevents leak)
        if (state.hasPendingAllocation()) {
            log.debug("ALLOCATE while pending exists - freeing old allocation: {} cells at addr {}",
                    state.getPendingAllocSize(), state.getPendingAllocAddr());
            syscallHandler.freePending(state.getPendingAllocAddr(), 
                    state.getPendingAllocSize(), state.getPendingAllocId());
            state.clearPendingAllocation();
        }
        
        int allocatedAddr = syscallHandler.allocate(requestedSize);
        state.setRegister(r2, allocatedAddr);
        
        // Track pending allocation for cleanup if organism dies before SPAWN
        if (allocatedAddr >= 0) {
            int allocId = syscallHandler.getLastAllocId();
            state.setPendingAllocation(allocatedAddr, requestedSize, allocId);
            log.trace("ALLOCATE: {} cells at addr {} (allocId={})", requestedSize, allocatedAddr, allocId);
        }
        
        state.advanceIp();
        
        if (allocatedAddr < 0) {
            return ExecutionResult.SYSCALL_ALLOCATE_FAILED;
        }
        return ExecutionResult.SYSCALL_ALLOCATE_OK;
    }
    
    /**
     * SPAWN instruction - create offspring organism.
     * R_addr = start address, R_size = genome size
     */
    private ExecutionResult executeSpawn(int r1, int r2, CpuState state) {
        int addr = state.getRegister(r1);
        int size = state.getRegister(r2);
        
        boolean success = syscallHandler.spawn(addr, size, state);
        
        // Clear pending allocation - memory ownership transfers to child
        // (or should be freed by spawn handler if spawn failed)
        state.clearPendingAllocation();
        
        state.advanceIp();
        
        if (success) {
            return ExecutionResult.SYSCALL_SPAWN_OK;
        } else {
            return ExecutionResult.SYSCALL_SPAWN_FAILED;
        }
    }
    
    /**
     * SEARCH instruction - find template pattern in memory.
     * Rs (r1) = start address for search
     * Rt (r2) = template address  
     * Rl (r3) = template length
     * Rf (r4) = result (found address, or -1 if not found)
     */
    private ExecutionResult executeSearch(int r1, int r2, int r3, int r4,
                                          CpuState state, AtomicIntegerArray memory) {
        int searchStart = state.getRegister(r1);
        int templateAddr = state.getRegister(r2);
        int templateLen = state.getRegister(r3);
        
        // Validate template bounds
        if (templateAddr < 0 || templateLen <= 0 ||
            templateAddr + templateLen > memory.length()) {
            state.setRegister(r4, -1);
            state.advanceIp();
            return ExecutionResult.OK;
        }
        
        // Validate search start
        if (searchStart < 0) {
            searchStart = 0;
        }
        
        // Read template into local array for comparison
        int[] template = new int[templateLen];
        for (int i = 0; i < templateLen; i++) {
            template[i] = memory.get(templateAddr + i);
        }
        
        // Search forward from searchStart
        int maxSearchAddr = memory.length() - templateLen;
        for (int addr = searchStart; addr <= maxSearchAddr; addr++) {
            if (matchesTemplate(memory, addr, template)) {
                state.setRegister(r4, addr);
                state.advanceIp();
                return ExecutionResult.OK;
            }
        }
        
        // Not found
        state.setRegister(r4, -1);
        state.advanceIp();
        return ExecutionResult.OK;
    }
    
    private boolean matchesTemplate(AtomicIntegerArray memory, int startAddr, int[] template) {
        for (int i = 0; i < template.length; i++) {
            if (memory.get(startAddr + i) != template[i]) {
                return false;
            }
        }
        return true;
    }
    
    // ========== Getters ==========
    
    public double getMutationRate() {
        return mutationRate;
    }
    
    /**
     * Set mutation tracker for recording mutations.
     * 
     * @param tracker the tracker to use, or null to disable tracking
     */
    public void setMutationTracker(MutationTracker tracker) {
        this.mutationTracker = tracker;
    }
    
    /**
     * Get the current mutation tracker.
     */
    public MutationTracker getMutationTracker() {
        return mutationTracker;
    }
}

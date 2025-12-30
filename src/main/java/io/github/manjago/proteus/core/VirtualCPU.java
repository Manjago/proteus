package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static io.github.manjago.proteus.core.OpCode.*;

/**
 * Virtual CPU interpreter for Proteus ISA v1.0
 * 
 * Executes instructions from shared memory ("soup").
 * Each organism has its own CpuState but shares the memory.
 */
public final class VirtualCPU {
    
    private static final Logger log = LoggerFactory.getLogger(VirtualCPU.class);
    
    /** Probability of mutation during COPY instruction (0.0 to 1.0) */
    private final double mutationRate;
    
    /** Random number generator for mutations */
    private final Random random;
    
    /** Handler for system calls (ALLOCATE, SPAWN) */
    private final SystemCallHandler syscallHandler;
    
    /**
     * Create VirtualCPU with default settings.
     * Mutation rate: 0.1% (0.001)
     */
    public VirtualCPU() {
        this(0.001, new Random(), SystemCallHandler.FAILING);
    }
    
    /**
     * Create VirtualCPU with custom mutation rate.
     */
    public VirtualCPU(double mutationRate) {
        this(mutationRate, new Random(), SystemCallHandler.FAILING);
    }
    
    /**
     * Create VirtualCPU with full configuration.
     */
    public VirtualCPU(double mutationRate, Random random, SystemCallHandler syscallHandler) {
        this.mutationRate = mutationRate;
        this.random = random;
        this.syscallHandler = syscallHandler;
    }
    
    /**
     * Execute a single instruction.
     * 
     * @param state CPU state of the organism
     * @param memory shared memory ("soup")
     * @return execution result
     */
    public ExecutionResult execute(CpuState state, AtomicIntegerArray memory) {
        int ip = state.getIp();
        
        // Bounds check for IP
        if (ip < 0 || ip >= memory.length()) {
            state.incrementErrors();
            return ExecutionResult.ERROR_IP_OUT_OF_BOUNDS;
        }
        
        int instruction = memory.get(ip);
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
            
            case LOAD -> {
                int addr = state.getRegister(r2);
                if (addr < 0 || addr >= memory.length()) {
                    state.incrementErrors();
                    state.advanceIp();
                    return ExecutionResult.ERROR_MEMORY_OUT_OF_BOUNDS;
                }
                state.setRegister(r1, memory.get(addr));
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case STORE -> {
                int addr = state.getRegister(r1);
                if (addr < 0 || addr >= memory.length()) {
                    state.incrementErrors();
                    state.advanceIp();
                    return ExecutionResult.ERROR_MEMORY_OUT_OF_BOUNDS;
                }
                memory.set(addr, state.getRegister(r2));
                state.advanceIp();
                return ExecutionResult.OK;
            }
            
            case JMP -> {
                state.setIp(state.getRegister(r1));
                return ExecutionResult.OK;
            }
            
            case JMPZ -> {
                if (state.getRegister(r1) == 0) {
                    state.setIp(state.getRegister(r2));
                } else {
                    state.advanceIp();
                }
                return ExecutionResult.OK;
            }
            
            case JMPN -> {
                if (state.getRegister(r1) != 0) {
                    state.setIp(state.getRegister(r2));
                } else {
                    state.advanceIp();
                }
                return ExecutionResult.OK;
            }
            
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
        
        int value = memory.get(srcAddr);
        
        // MUTATION happens here!
        if (random.nextDouble() < mutationRate) {
            value = mutate(value);
            log.debug("Mutation at copy from {} to {}: value mutated", srcAddr, dstAddr);
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
        int allocatedAddr = syscallHandler.allocate(requestedSize);
        state.setRegister(r2, allocatedAddr);
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
}

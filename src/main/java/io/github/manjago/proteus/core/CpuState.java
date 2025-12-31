package io.github.manjago.proteus.core;

/**
 * CPU state for a single organism (ISA v1.2).
 * 
 * Contains:
 * - 8 general-purpose registers (R0-R7)
 * - Instruction pointer (IP) — now RELATIVE to startAddr
 * - Start address — absolute address of organism's genome in soup
 * 
 * In v1.2, IP is relative: actual address = startAddr + ip
 */
public final class CpuState {
    
    /** Number of general-purpose registers */
    public static final int REGISTER_COUNT = 8;
    
    /** General-purpose registers R0-R7 */
    private final int[] registers;
    
    /** 
     * Instruction pointer — RELATIVE offset from startAddr (v1.2).
     * Actual address in soup = startAddr + ip
     */
    private int ip;
    
    /**
     * Start address — absolute address of this organism's genome (v1.2).
     * Used for relative addressing in LOAD/STORE and GETADDR.
     */
    private int startAddr;
    
    /** Error counter - incremented on invalid operations */
    private int errors;
    
    /** Age - total instructions executed */
    private long age;
    
    public CpuState() {
        this.registers = new int[REGISTER_COUNT];
        this.ip = 0;
        this.startAddr = 0;
        this.errors = 0;
        this.age = 0;
    }
    
    /**
     * Create CpuState with initial start address (v1.2).
     * IP starts at 0 (relative).
     */
    public CpuState(int startAddr) {
        this();
        this.startAddr = startAddr;
    }
    
    /**
     * Copy constructor - creates independent copy of state.
     */
    public CpuState(CpuState other) {
        this.registers = other.registers.clone();
        this.ip = other.ip;
        this.startAddr = other.startAddr;
        this.errors = other.errors;
        this.age = other.age;
    }
    
    // ========== Register Access ==========
    
    public int getRegister(int index) {
        return registers[index & 0x07]; // Mask to 0-7
    }
    
    public void setRegister(int index, int value) {
        registers[index & 0x07] = value;
    }
    
    // ========== IP Access (v1.2: IP is relative) ==========
    
    /**
     * Get relative IP (offset from startAddr).
     */
    public int getIp() {
        return ip;
    }
    
    /**
     * Get absolute IP (startAddr + relative IP).
     * This is the actual address in soup where next instruction is.
     */
    public int getAbsoluteIp() {
        return startAddr + ip;
    }
    
    /**
     * Set relative IP.
     */
    public void setIp(int ip) {
        this.ip = ip;
    }
    
    /**
     * Advance IP by 1 (after executing instruction).
     */
    public void advanceIp() {
        this.ip++;
    }
    
    /**
     * Add offset to IP (for relative jumps in v1.2).
     */
    public void jumpRelative(int offset) {
        this.ip += offset;
    }
    
    // ========== Start Address (v1.2) ==========
    
    /**
     * Get absolute start address of organism's genome.
     */
    public int getStartAddr() {
        return startAddr;
    }
    
    /**
     * Set start address (used during organism creation or defragmentation).
     */
    public void setStartAddr(int startAddr) {
        this.startAddr = startAddr;
    }
    
    // ========== Error Tracking ==========
    
    public int getErrors() {
        return errors;
    }
    
    public void incrementErrors() {
        this.errors++;
    }
    
    public void resetErrors() {
        this.errors = 0;
    }
    
    // ========== Age Tracking ==========
    
    public long getAge() {
        return age;
    }
    
    public void incrementAge() {
        this.age++;
    }
    
    // ========== Utility ==========
    
    /**
     * Reset all registers to zero, keep IP, startAddr, errors and age.
     */
    public void clearRegisters() {
        for (int i = 0; i < REGISTER_COUNT; i++) {
            registers[i] = 0;
        }
    }
    
    /**
     * Full reset - all registers to 0, IP to 0, errors to 0, age to 0.
     * startAddr is preserved.
     */
    public void reset() {
        clearRegisters();
        ip = 0;
        errors = 0;
        age = 0;
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("CpuState{startAddr=").append(startAddr);
        sb.append(", IP=").append(ip);
        sb.append(" (abs=").append(getAbsoluteIp()).append(")");
        sb.append(", R=[");
        for (int i = 0; i < REGISTER_COUNT; i++) {
            if (i > 0) sb.append(", ");
            sb.append(registers[i]);
        }
        sb.append("], errors=").append(errors);
        sb.append(", age=").append(age);
        sb.append('}');
        return sb.toString();
    }
}

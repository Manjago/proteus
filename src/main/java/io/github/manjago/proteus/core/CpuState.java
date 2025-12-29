package io.github.manjago.proteus.core;

import org.jetbrains.annotations.NotNull;

/**
 * CPU state for a single organism.
 * <p>
 * Contains 8 general-purpose registers (R0-R7) and instruction pointer (IP).
 * Each organism has its own CpuState instance.
 */
public final class CpuState {
    
    /** Number of general-purpose registers */
    public static final int REGISTER_COUNT = 8;
    
    /** General-purpose registers R0-R7 */
    private final int[] registers;
    
    /** Instruction pointer - address of NEXT instruction to execute */
    private int ip;
    
    /** Error counter - incremented on invalid operations */
    private int errors;
    
    /** Age - total instructions executed */
    private long age;
    
    public CpuState() {
        this.registers = new int[REGISTER_COUNT];
        this.ip = 0;
        this.errors = 0;
        this.age = 0;
    }
    
    /**
     * Create CpuState with initial IP.
     */
    public CpuState(int initialIp) {
        this();
        this.ip = initialIp;
    }
    
    /**
     * Copy constructor - creates independent copy of state.
     */
    public CpuState(@NotNull CpuState other) {
        this.registers = other.registers.clone();
        this.ip = other.ip;
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
    
    // ========== IP Access ==========
    
    public int getIp() {
        return ip;
    }
    
    public void setIp(int ip) {
        this.ip = ip;
    }
    
    public void advanceIp() {
        this.ip++;
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
     * Reset all registers to zero, keep IP, errors and age.
     */
    public void clearRegisters() {
        for (int i = 0; i < REGISTER_COUNT; i++) {
            registers[i] = 0;
        }
    }
    
    /**
     * Full reset - all registers to 0, IP to 0, errors to 0, age to 0.
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
        sb.append("CpuState{IP=").append(String.format("%04X", ip));
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

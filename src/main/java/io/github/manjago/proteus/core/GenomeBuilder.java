package io.github.manjago.proteus.core;

import java.util.ArrayList;
import java.util.List;

import static io.github.manjago.proteus.core.OpCode.*;

/**
 * Helper for building genomes (programs) manually.
 * 
 * Usage:
 * <pre>
 * int[] genome = GenomeBuilder.create()
 *     .nop()
 *     .inc(0)
 *     .jmpn(0, 1)
 *     .build();
 * </pre>
 */
public final class GenomeBuilder {
    
    private final List<Integer> instructions = new ArrayList<>();
    
    private GenomeBuilder() {}
    
    public static GenomeBuilder create() {
        return new GenomeBuilder();
    }
    
    // ========== Basic ==========
    
    public GenomeBuilder nop() {
        instructions.add(encode(NOP));
        return this;
    }
    
    /**
     * NOP with register field set (useful for markers/templates).
     * The NOP still does nothing, but the instruction encoding is different.
     */
    public GenomeBuilder nop(int r1) {
        instructions.add(encode(NOP, r1));
        return this;
    }
    
    public GenomeBuilder mov(int dst, int src) {
        instructions.add(encode(MOV, dst, src));
        return this;
    }
    
    /**
     * MOVI - load immediate value into register.
     * @param dst destination register (0-7)
     * @param immediate 21-bit unsigned value (0 to 2,097,151)
     */
    public GenomeBuilder movi(int dst, int immediate) {
        instructions.add(encodeImm(MOVI, dst, immediate));
        return this;
    }
    
    /**
     * GETADDR - load organism's absolute start address into register (v1.2).
     * @param dst destination register (0-7)
     */
    public GenomeBuilder getaddr(int dst) {
        instructions.add(encode(GETADDR, dst));
        return this;
    }
    
    // ========== Arithmetic ==========
    
    public GenomeBuilder add(int a, int b) {
        instructions.add(encode(ADD, a, b));
        return this;
    }
    
    public GenomeBuilder sub(int a, int b) {
        instructions.add(encode(SUB, a, b));
        return this;
    }
    
    public GenomeBuilder inc(int r) {
        instructions.add(encode(INC, r));
        return this;
    }
    
    public GenomeBuilder dec(int r) {
        instructions.add(encode(DEC, r));
        return this;
    }
    
    // ========== Memory ==========
    
    public GenomeBuilder load(int dst, int addrReg) {
        instructions.add(encode(LOAD, dst, addrReg));
        return this;
    }
    
    public GenomeBuilder store(int addrReg, int src) {
        instructions.add(encode(STORE, addrReg, src));
        return this;
    }
    
    // ========== Control Flow (v1.2: IP-relative) ==========
    
    /**
     * JMP - relative jump (v1.2).
     * @param offset signed offset from current IP (-131,072 to +131,071)
     */
    public GenomeBuilder jmp(int offset) {
        instructions.add(encodeJump(offset));
        return this;
    }
    
    /**
     * JMPZ - relative jump if register is zero (v1.2).
     * @param condReg condition register (jump if == 0)
     * @param offset signed offset from current IP
     */
    public GenomeBuilder jmpz(int condReg, int offset) {
        instructions.add(encodeJumpZero(condReg, offset));
        return this;
    }
    
    /**
     * JMPN - relative jump if R_a < R_b (v1.2).
     * @param rA first register
     * @param rB second register 
     * @param offset signed offset from current IP (jump if rA < rB)
     */
    public GenomeBuilder jmpn(int rA, int rB, int offset) {
        instructions.add(encodeJumpLess(rA, rB, offset));
        return this;
    }
    
    // ========== System Calls ==========
    
    public GenomeBuilder copy(int srcAddrReg, int dstAddrReg) {
        instructions.add(encode(COPY, srcAddrReg, dstAddrReg));
        return this;
    }
    
    public GenomeBuilder allocate(int sizeReg, int resultReg) {
        instructions.add(encode(ALLOCATE, sizeReg, resultReg));
        return this;
    }
    
    public GenomeBuilder spawn(int addrReg, int sizeReg) {
        instructions.add(encode(SPAWN, addrReg, sizeReg));
        return this;
    }
    
    // ========== Advanced ==========
    
    public GenomeBuilder search(int startReg, int templateAddrReg, int templateLenReg, int resultReg) {
        instructions.add(encode(SEARCH, startReg, templateAddrReg, templateLenReg, resultReg));
        return this;
    }
    
    // ========== Raw ==========
    
    /**
     * Add raw instruction value.
     */
    public GenomeBuilder raw(int instruction) {
        instructions.add(instruction);
        return this;
    }
    
    /**
     * Add a label marker (returns current position for reference).
     */
    public int label() {
        return instructions.size();
    }
    
    // ========== Build ==========
    
    public int[] build() {
        return instructions.stream().mapToInt(Integer::intValue).toArray();
    }
    
    public int size() {
        return instructions.size();
    }
    
    // ========== Utility ==========
    
    /**
     * Disassemble the current genome.
     */
    public String disassemble() {
        return Disassembler.disassemble(build());
    }
    
    @Override
    public String toString() {
        return "GenomeBuilder[" + size() + " instructions]";
    }
}

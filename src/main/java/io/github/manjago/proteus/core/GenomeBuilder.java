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
    
    // ========== Control Flow ==========
    
    public GenomeBuilder jmp(int addrReg) {
        instructions.add(encode(JMP, addrReg));
        return this;
    }
    
    public GenomeBuilder jmpz(int condReg, int addrReg) {
        instructions.add(encode(JMPZ, condReg, addrReg));
        return this;
    }
    
    public GenomeBuilder jmpn(int condReg, int addrReg) {
        instructions.add(encode(JMPN, condReg, addrReg));
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

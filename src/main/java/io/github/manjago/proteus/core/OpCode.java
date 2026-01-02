package io.github.manjago.proteus.core;

/**
 * Instruction Set Architecture (ISA) v1.2 — Position-Independent Code
 * 
 * <h2>Changes in v1.2:</h2>
 * <ul>
 *   <li>JMP/JMPZ/JLT use IP-relative offsets (encoded in instruction)</li>
 *   <li>LOAD/STORE use startAddr-relative addressing</li>
 *   <li>Added GETADDR instruction</li>
 * </ul>
 * 
 * <h2>Standard instruction encoding (32 bits):</h2>
 * <pre>
 * [8 bits: OpCode | 3 bits: R1 | 3 bits: R2 | 3 bits: R3 | 3 bits: R4 | 12 bits: reserved]
 * </pre>
 * 
 * <h2>MOVI encoding (32 bits):</h2>
 * <pre>
 * [8 bits: OpCode | 3 bits: R_dst | 21 bits: immediate (unsigned)]
 * </pre>
 * 
 * <h2>JMP/JMPZ/JLT encoding (32 bits) — v1.2:</h2>
 * <pre>
 * [8 bits: OpCode | 3 bits: R_cond1 | 3 bits: R_cond2 | 18 bits: offset (signed)]
 * </pre>
 */
public enum OpCode {
    
    // ========== Basic ==========
    
    /** No Operation. Consumes 1 CPU cycle. */
    NOP(0x00, 0, "NOP"),
    
    /** Move. R_dst = R_src */
    MOV(0x01, 2, "MOV"),
    
    /** Move Immediate. R_dst = imm21 (21-bit unsigned constant 0-2,097,151) */
    MOVI(0x02, 1, "MOVI"),
    
    /** 
     * Get Address. R_dst = startAddr (v1.2)
     * Returns the absolute start address of this organism's genome.
     */
    GETADDR(0x03, 1, "GETADDR"),
    
    // ========== Arithmetic ==========
    
    /** Add. R_a = R_a + R_b */
    ADD(0x10, 2, "ADD"),
    
    /** Subtract. R_a = R_a - R_b */
    SUB(0x11, 2, "SUB"),
    
    /** Increment. R_a = R_a + 1 */
    INC(0x12, 1, "INC"),
    
    /** Decrement. R_a = R_a - 1 */
    DEC(0x13, 1, "DEC"),
    
    // ========== Memory (startAddr-relative in v1.2) ==========
    
    /** 
     * Load (relative). R_dst = memory[startAddr + R_offset] (v1.2)
     * Address is relative to organism's start address.
     */
    LOAD(0x20, 2, "LOAD"),
    
    /** 
     * Store (relative). memory[startAddr + R_offset] = R_src (v1.2)
     * Address is relative to organism's start address.
     */
    STORE(0x21, 2, "STORE"),
    
    // ========== Control Flow (IP-relative in v1.2) ==========
    
    /** 
     * Unconditional Jump (relative). IP = IP + offset (v1.2)
     * Offset is 18-bit signed, encoded in instruction bits 17-0.
     * operandCount=0 because offset is immediate, not a register.
     */
    JMP(0x30, 0, "JMP"),
    
    /** 
     * Jump if Zero (relative). if (R_cond == 0) IP = IP + offset (v1.2)
     * R_cond in bits 23-21, offset in bits 17-0.
     */
    JMPZ(0x31, 1, "JMPZ"),
    
    /** 
     * Jump if Less Than (relative). if (R_a < R_b) IP = IP + offset (v1.2)
     * R_a in bits 23-21, R_b in bits 20-18, offset in bits 17-0.
     */
    JLT(0x32, 2, "JLT"),
    
    // ========== System Calls (COPY uses absolute addresses) ==========
    
    /** 
     * Copy one memory cell. memory[R_dst] = memory[R_src]
     * Uses ABSOLUTE addresses (for copying to child).
     * MUTATION HAPPENS HERE! 
     */
    COPY(0x40, 2, "COPY"),
    
    /** 
     * Allocate memory block. 
     * Request size in R_size, returns ABSOLUTE start address in R_addr.
     * Returns -1 in R_addr if allocation failed.
     */
    ALLOCATE(0x41, 2, "ALLOCATE"),
    
    /** 
     * Spawn offspring. 
     * Creates new organism at ABSOLUTE address R_addr with genome size R_size.
     */
    SPAWN(0x42, 2, "SPAWN"),
    
    // ========== Advanced ==========
    
    /**
     * Search for template in memory (relative addressing).
     * Rs = start offset for search (relative)
     * Rt = template offset (relative)
     * Rl = template length  
     * Rf = result (found address, or -1 if not found)
     */
    SEARCH(0x50, 4, "SEARCH");
    
    // ========== Fields & Constructor ==========
    
    private final int code;
    private final int operandCount;
    private final String mnemonic;
    
    OpCode(int code, int operandCount, String mnemonic) {
        this.code = code;
        this.operandCount = operandCount;
        this.mnemonic = mnemonic;
    }
    
    public int getCode() {
        return code;
    }
    
    public int getOperandCount() {
        return operandCount;
    }
    
    public String getMnemonic() {
        return mnemonic;
    }
    
    // ========== Lookup ==========
    
    private static final OpCode[] BY_CODE = new OpCode[256];
    
    static {
        for (OpCode op : values()) {
            BY_CODE[op.code] = op;
        }
    }
    
    /**
     * Get OpCode by its numeric code.
     * @param code numeric opcode (0-255)
     * @return OpCode or null if not found
     */
    public static OpCode fromCode(int code) {
        if (code < 0 || code >= BY_CODE.length) {
            return null;
        }
        return BY_CODE[code];
    }
    
    // ========== Instruction Encoding/Decoding ==========
    
    /** Bit positions */
    private static final int OPCODE_SHIFT = 24;
    private static final int R1_SHIFT = 21;
    private static final int R2_SHIFT = 18;
    private static final int R3_SHIFT = 15;
    private static final int R4_SHIFT = 12;
    
    private static final int OPCODE_MASK = 0xFF;
    private static final int REG_MASK = 0x07;
    private static final int IMM21_MASK = 0x1FFFFF; // 21 bits for MOVI (0 to 2,097,151)
    private static final int OFFSET18_MASK = 0x3FFFF; // 18 bits for JMP offset (-131,072 to +131,071)
    
    /**
     * Encode instruction from opcode and register operands.
     */
    public static int encode(OpCode op, int r1, int r2, int r3, int r4) {
        return (op.code << OPCODE_SHIFT)
             | ((r1 & REG_MASK) << R1_SHIFT)
             | ((r2 & REG_MASK) << R2_SHIFT)
             | ((r3 & REG_MASK) << R3_SHIFT)
             | ((r4 & REG_MASK) << R4_SHIFT);
    }
    
    public static int encode(OpCode op, int r1, int r2, int r3) {
        return encode(op, r1, r2, r3, 0);
    }
    
    public static int encode(OpCode op, int r1, int r2) {
        return encode(op, r1, r2, 0, 0);
    }
    
    public static int encode(OpCode op, int r1) {
        return encode(op, r1, 0, 0, 0);
    }
    
    public static int encode(OpCode op) {
        return encode(op, 0, 0, 0, 0);
    }
    
    /**
     * Encode instruction with immediate value (for MOVI).
     * Format: [opcode:8][reg:3][imm21:21]
     * Immediate is 21-bit unsigned (0 to 2,097,151)
     */
    public static int encodeImm(OpCode op, int reg, int immediate) {
        return (op.code << OPCODE_SHIFT)
             | ((reg & REG_MASK) << R1_SHIFT)
             | (immediate & IMM21_MASK);
    }
    
    // ========== v1.2: Jump with relative offset ==========
    
    /**
     * Encode JMP with relative offset (v1.2).
     * Format: [opcode:8][unused:6][offset:18]
     * @param offset signed 18-bit offset (-131,072 to +131,071)
     */
    public static int encodeJump(int offset) {
        return (JMP.code << OPCODE_SHIFT)
             | (offset & OFFSET18_MASK);
    }
    
    /**
     * Encode JMPZ with condition register and relative offset (v1.2).
     * Format: [opcode:8][R_cond:3][unused:3][offset:18]
     * @param rCond condition register (jump if zero)
     * @param offset signed 18-bit offset
     */
    public static int encodeJumpZero(int rCond, int offset) {
        return (JMPZ.code << OPCODE_SHIFT)
             | ((rCond & REG_MASK) << R1_SHIFT)
             | (offset & OFFSET18_MASK);
    }
    
    /**
     * Encode JLT (Jump if Less Than) with two condition registers and relative offset (v1.2).
     * Format: [opcode:8][R_a:3][R_b:3][offset:18]
     * @param rA first register
     * @param rB second register (jump if R_a < R_b)
     * @param offset signed 18-bit offset
     */
    public static int encodeJumpLess(int rA, int rB, int offset) {
        return (JLT.code << OPCODE_SHIFT)
             | ((rA & REG_MASK) << R1_SHIFT)
             | ((rB & REG_MASK) << R2_SHIFT)
             | (offset & OFFSET18_MASK);
    }
    
    /**
     * Decode 18-bit signed offset from JMP/JMPZ/JLT instruction (v1.2).
     * Performs sign extension from 18-bit to 32-bit.
     */
    public static int decodeOffset(int instruction) {
        int raw = instruction & OFFSET18_MASK;
        // Sign extend: if bit 17 is set, fill upper bits with 1s
        if ((raw & 0x20000) != 0) {
            return raw | 0xFFFC0000; // Extend sign
        }
        return raw;
    }
    
    /**
     * Decode opcode from instruction.
     */
    public static OpCode decodeOpCode(int instruction) {
        return fromCode((instruction >>> OPCODE_SHIFT) & OPCODE_MASK);
    }
    
    /**
     * Decode register 1 from instruction.
     */
    public static int decodeR1(int instruction) {
        return (instruction >>> R1_SHIFT) & REG_MASK;
    }
    
    /**
     * Decode register 2 from instruction.
     */
    public static int decodeR2(int instruction) {
        return (instruction >>> R2_SHIFT) & REG_MASK;
    }
    
    /**
     * Decode register 3 from instruction.
     */
    public static int decodeR3(int instruction) {
        return (instruction >>> R3_SHIFT) & REG_MASK;
    }
    
    /**
     * Decode register 4 from instruction.
     */
    public static int decodeR4(int instruction) {
        return (instruction >>> R4_SHIFT) & REG_MASK;
    }
    
    /**
     * Decode 21-bit immediate value from instruction (for MOVI).
     */
    public static int decodeImmediate(int instruction) {
        return instruction & IMM21_MASK;
    }
}

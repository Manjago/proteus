package io.github.manjago.proteus.core;


import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Instruction Set Architecture (ISA) v1.0
 * <p>
 * Instruction encoding (32 bits):
 * [8 bits: OpCode | 3 bits: R1 | 3 bits: R2 | 3 bits: R3 | 3 bits: R4 | 12 bits: reserved]
 * <p>
 * Bits 31-24: OpCode (0-255)
 * Bits 23-21: Register 1 (0-7)
 * Bits 20-18: Register 2 (0-7)
 * Bits 17-15: Register 3 (0-7)
 * Bits 14-12: Register 4 (0-7)
 * Bits 11-0:  Reserved
 */
public enum OpCode {

    // ========== Basic ==========

    /** No Operation. Consumes 1 CPU cycle. */
    NOP(0x00, 0, "NOP"),

    /** Move. R_dst = R_src */
    MOV(0x01, 2, "MOV"),

    // ========== Arithmetic ==========

    /** Add. R_a = R_a + R_b */
    ADD(0x10, 2, "ADD"),

    /** Subtract. R_a = R_a - R_b */
    SUB(0x11, 2, "SUB"),

    /** Increment. R_a = R_a + 1 */
    INC(0x12, 1, "INC"),

    /** Decrement. R_a = R_a - 1 */
    DEC(0x13, 1, "DEC"),

    // ========== Memory ==========

    /** Load. R_dst = memory[R_addr] */
    LOAD(0x20, 2, "LOAD"),

    /** Store. memory[R_addr] = R_src */
    STORE(0x21, 2, "STORE"),

    // ========== Control Flow ==========

    /** Unconditional Jump. IP = R_addr */
    JMP(0x30, 1, "JMP"),

    /** Jump if Zero. if (R_cond == 0) IP = R_addr */
    JMPZ(0x31, 2, "JMPZ"),

    /** Jump if Not Zero. if (R_cond != 0) IP = R_addr */
    JMPN(0x32, 2, "JMPN"),

    // ========== System Calls (Replication) ==========

    /**
     * Copy one memory cell. memory[R_dst] = memory[R_src]
     * MUTATION HAPPENS HERE!
     */
    COPY(0x40, 2, "COPY"),

    /**
     * Allocate memory block.
     * Request size in R_size, returns start address in R_addr.
     * Returns -1 in R_addr if allocation failed.
     */
    ALLOCATE(0x41, 2, "ALLOCATE"),

    /**
     * Spawn offspring.
     * Creates new organism at R_addr with genome size R_size.
     */
    SPAWN(0x42, 2, "SPAWN"),

    // ========== Advanced ==========

    /**
     * Search for template in memory.
     * Rs = start address for search
     * Rt = template address
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
    @Contract(pure = true)
    public static @Nullable OpCode fromCode(int code) {
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

    /**
     * Encode instruction from opcode and register operands.
     */
    @Contract(pure = true)
    public static int encode(@NotNull OpCode op, int r1, int r2, int r3, int r4) {
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
}

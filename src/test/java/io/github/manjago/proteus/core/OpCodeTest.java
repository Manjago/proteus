package io.github.manjago.proteus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.manjago.proteus.core.OpCode.*;

class OpCodeTest {

    @Test
    @DisplayName("NOP encodes to correct value")
    void nopEncodesToZeroInHighByte() {
        int instruction = encode(NOP);
        assertEquals(0x00_000000, instruction);
    }

    @Test
    @DisplayName("MOV R1, R2 encodes correctly")
    void movEncodesRegisters() {
        // MOV R3, R5 -> opcode=0x01, r1=3, r2=5
        int instruction = encode(MOV, 3, 5);
        
        assertEquals(MOV, decodeOpCode(instruction));
        assertEquals(3, decodeR1(instruction));
        assertEquals(5, decodeR2(instruction));
    }

    @Test
    @DisplayName("SEARCH uses all 4 registers")
    void searchEncodesFourRegisters() {
        // SEARCH Rs=0, Rt=1, Rl=2, Rf=7
        int instruction = encode(SEARCH, 0, 1, 2, 7);
        
        assertEquals(SEARCH, decodeOpCode(instruction));
        assertEquals(0, decodeR1(instruction));
        assertEquals(1, decodeR2(instruction));
        assertEquals(2, decodeR3(instruction));
        assertEquals(7, decodeR4(instruction));
    }

    @Test
    @DisplayName("Register values wrap at 3 bits (0-7)")
    void registerValuesWrapAt3Bits() {
        // R1 = 8 should become 0 (8 & 0x07 = 0)
        int instruction = encode(INC, 8);
        assertEquals(0, decodeR1(instruction));
        
        // R1 = 15 should become 7 (15 & 0x07 = 7)
        instruction = encode(INC, 15);
        assertEquals(7, decodeR1(instruction));
    }

    @ParameterizedTest
    @EnumSource(OpCode.class)
    @DisplayName("All opcodes can be encoded and decoded")
    void allOpCodesRoundTrip(OpCode op) {
        int instruction = encode(op, 1, 2, 3, 4);
        assertEquals(op, decodeOpCode(instruction));
    }

    @Test
    @DisplayName("fromCode returns null for unknown opcodes")
    void fromCodeReturnsNullForUnknown() {
        assertNull(fromCode(0xFF)); // not defined
        assertNull(fromCode(0x04)); // gap between GETADDR and ADD (v1.2)
        assertNull(fromCode(-1));   // negative
        assertNull(fromCode(256));  // out of range
    }

    @Test
    @DisplayName("fromCode returns correct OpCode for all defined codes")
    void fromCodeReturnsCorrectOpCode() {
        assertEquals(NOP, fromCode(0x00));
        assertEquals(MOV, fromCode(0x01));
        assertEquals(MOVI, fromCode(0x02));
        assertEquals(GETADDR, fromCode(0x03));  // v1.2
        assertEquals(ADD, fromCode(0x10));
        assertEquals(SUB, fromCode(0x11));
        assertEquals(INC, fromCode(0x12));
        assertEquals(DEC, fromCode(0x13));
        assertEquals(LOAD, fromCode(0x20));
        assertEquals(STORE, fromCode(0x21));
        assertEquals(JMP, fromCode(0x30));
        assertEquals(JMPZ, fromCode(0x31));
        assertEquals(JMPN, fromCode(0x32));
        assertEquals(COPY, fromCode(0x40));
        assertEquals(ALLOCATE, fromCode(0x41));
        assertEquals(SPAWN, fromCode(0x42));
        assertEquals(SEARCH, fromCode(0x50));
    }

    @Test
    @DisplayName("Operand counts are correct (v1.2)")
    void operandCountsAreCorrect() {
        assertEquals(0, NOP.getOperandCount());
        assertEquals(2, MOV.getOperandCount());
        assertEquals(1, MOVI.getOperandCount());  // R_dst (imm is in instruction)
        assertEquals(1, GETADDR.getOperandCount());  // v1.2: R_dst
        assertEquals(2, ADD.getOperandCount());
        assertEquals(1, INC.getOperandCount());
        assertEquals(1, DEC.getOperandCount());
        assertEquals(2, LOAD.getOperandCount());
        assertEquals(2, STORE.getOperandCount());
        assertEquals(0, JMP.getOperandCount());   // v1.2: offset in instruction
        assertEquals(1, JMPZ.getOperandCount());  // v1.2: R_cond (offset in instruction)
        assertEquals(2, JMPN.getOperandCount());  // v1.2: R_a, R_b (offset in instruction)
        assertEquals(2, COPY.getOperandCount());
        assertEquals(2, ALLOCATE.getOperandCount());
        assertEquals(2, SPAWN.getOperandCount());
        assertEquals(4, SEARCH.getOperandCount());
    }

    @Test
    @DisplayName("Mnemonics match enum names")
    void mnemonicsMatchEnumNames() {
        for (OpCode op : OpCode.values()) {
            assertEquals(op.name(), op.getMnemonic());
        }
    }

    @Test
    @DisplayName("Bit layout is correct: opcode in high byte")
    void bitLayoutOpCodeInHighByte() {
        // ADD has code 0x10
        int instruction = encode(ADD, 0, 0, 0, 0);
        // Should be 0x10 in bits 31-24
        assertEquals(0x10_000000, instruction);
    }

    @Test
    @DisplayName("Bit layout is correct: registers in correct positions")
    void bitLayoutRegistersInCorrectPositions() {
        // All registers set to 7 (0b111)
        int instruction = encode(NOP, 7, 7, 7, 7);
        
        // Expected: 0x00_EFF000
        // R1 (bits 23-21): 111 -> shifted to position 21
        // R2 (bits 20-18): 111 -> shifted to position 18  
        // R3 (bits 17-15): 111 -> shifted to position 15
        // R4 (bits 14-12): 111 -> shifted to position 12
        
        assertEquals(7, decodeR1(instruction));
        assertEquals(7, decodeR2(instruction));
        assertEquals(7, decodeR3(instruction));
        assertEquals(7, decodeR4(instruction));
        
        // Binary: 0000_0000_1111_1111_1111_0000_0000_0000
        // Hex:    0x00FFF000
        assertEquals(0x00FFF000, instruction);
    }
    
    // ========== MOVI Tests ==========
    
    @Test
    @DisplayName("MOVI encodes and decodes immediate correctly")
    void moviEncodesImmediate() {
        int instruction = encodeImm(MOVI, 3, 12345);
        
        assertEquals(MOVI, decodeOpCode(instruction));
        assertEquals(3, decodeR1(instruction));
        assertEquals(12345, decodeImmediate(instruction));
    }
    
    @Test
    @DisplayName("MOVI can encode maximum 21-bit immediate (2,097,151)")
    void moviMaxImmediate() {
        int maxImm = 0x1FFFFF; // 2,097,151
        int instruction = encodeImm(MOVI, 0, maxImm);
        
        assertEquals(maxImm, decodeImmediate(instruction));
    }
    
    @Test
    @DisplayName("MOVI immediate wraps at 21 bits")
    void moviImmediateWraps() {
        // 0x200000 = 2,097,152 should wrap to 0
        int instruction = encodeImm(MOVI, 0, 0x200000);
        assertEquals(0, decodeImmediate(instruction));
        
        // 0x200001 should wrap to 1
        instruction = encodeImm(MOVI, 0, 0x200001);
        assertEquals(1, decodeImmediate(instruction));
    }
    
    @Test
    @DisplayName("MOVI encodes register correctly")
    void moviEncodesRegister() {
        for (int r = 0; r < 8; r++) {
            int instruction = encodeImm(MOVI, r, 100);
            assertEquals(r, decodeR1(instruction));
        }
    }
    
    // ========== v1.2 Jump Encoding Tests ==========
    
    @Test
    @DisplayName("encodeJump encodes positive offset correctly")
    void encodeJumpPositiveOffset() {
        int instruction = encodeJump(100);
        
        assertEquals(JMP, decodeOpCode(instruction));
        assertEquals(100, decodeOffset(instruction));
    }
    
    @Test
    @DisplayName("encodeJump encodes negative offset correctly")
    void encodeJumpNegativeOffset() {
        int instruction = encodeJump(-50);
        
        assertEquals(JMP, decodeOpCode(instruction));
        assertEquals(-50, decodeOffset(instruction));
    }
    
    @Test
    @DisplayName("encodeJumpZero encodes R_cond and offset")
    void encodeJumpZeroEncodesCorrectly() {
        int instruction = encodeJumpZero(3, -10);
        
        assertEquals(JMPZ, decodeOpCode(instruction));
        assertEquals(3, decodeR1(instruction));
        assertEquals(-10, decodeOffset(instruction));
    }
    
    @Test
    @DisplayName("encodeJumpLess encodes R_a, R_b and offset")
    void encodeJumpLessEncodesCorrectly() {
        int instruction = encodeJumpLess(2, 5, 15);
        
        assertEquals(JMPN, decodeOpCode(instruction));
        assertEquals(2, decodeR1(instruction));
        assertEquals(5, decodeR2(instruction));
        assertEquals(15, decodeOffset(instruction));
    }
    
    @Test
    @DisplayName("decodeOffset sign-extends 18-bit value correctly")
    void decodeOffsetSignExtends() {
        // Test maximum positive offset
        int instruction = encodeJump(0x1FFFF);  // 131,071
        assertEquals(0x1FFFF, decodeOffset(instruction));
        
        // Test -1 (all 1s in 18 bits = 0x3FFFF)
        instruction = encodeJump(-1);
        assertEquals(-1, decodeOffset(instruction));
        
        // Test minimum negative offset
        instruction = encodeJump(-131072);
        assertEquals(-131072, decodeOffset(instruction));
    }
}

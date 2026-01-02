package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Assembler.
 */
class AssemblerTest {
    
    private Assembler assembler;
    
    @BeforeEach
    void setUp() {
        assembler = new Assembler();
    }
    
    @Test
    @DisplayName("Assemble NOP")
    void testNop() throws Exception {
        int[] code = assembler.assemble("NOP");
        assertEquals(1, code.length);
        assertEquals(OpCode.NOP, OpCode.decodeOpCode(code[0]));
    }
    
    @Test
    @DisplayName("Assemble MOV R1, R2")
    void testMov() throws Exception {
        int[] code = assembler.assemble("MOV R1, R2");
        assertEquals(1, code.length);
        assertEquals(OpCode.MOV, OpCode.decodeOpCode(code[0]));
        assertEquals(1, OpCode.decodeR1(code[0]));
        assertEquals(2, OpCode.decodeR2(code[0]));
    }
    
    @Test
    @DisplayName("Assemble MOVI R4, 14")
    void testMovi() throws Exception {
        int[] code = assembler.assemble("MOVI R4, 14");
        assertEquals(1, code.length);
        assertEquals(OpCode.MOVI, OpCode.decodeOpCode(code[0]));
        assertEquals(4, OpCode.decodeR1(code[0]));
        assertEquals(14, OpCode.decodeImmediate(code[0]));
    }
    
    @Test
    @DisplayName("Assemble MOVI with large immediate")
    void testMoviLarge() throws Exception {
        int[] code = assembler.assemble("MOVI R0, 2097151");  // Max 21-bit
        assertEquals(2097151, OpCode.decodeImmediate(code[0]));
    }
    
    @Test
    @DisplayName("Assemble GETADDR R7")
    void testGetaddr() throws Exception {
        int[] code = assembler.assemble("GETADDR R7");
        assertEquals(OpCode.GETADDR, OpCode.decodeOpCode(code[0]));
        assertEquals(7, OpCode.decodeR1(code[0]));
    }
    
    @Test
    @DisplayName("Assemble arithmetic instructions")
    void testArithmetic() throws Exception {
        int[] code = assembler.assemble("""
            ADD R0, R1
            SUB R2, R3
            INC R4
            DEC R5
            """);
        assertEquals(4, code.length);
        assertEquals(OpCode.ADD, OpCode.decodeOpCode(code[0]));
        assertEquals(OpCode.SUB, OpCode.decodeOpCode(code[1]));
        assertEquals(OpCode.INC, OpCode.decodeOpCode(code[2]));
        assertEquals(OpCode.DEC, OpCode.decodeOpCode(code[3]));
    }
    
    @Test
    @DisplayName("Assemble JMP with numeric offset")
    void testJmpNumeric() throws Exception {
        int[] code = assembler.assemble("JMP -5");
        assertEquals(OpCode.JMP, OpCode.decodeOpCode(code[0]));
        assertEquals(-5, OpCode.decodeOffset(code[0]));
    }
    
    @Test
    @DisplayName("Assemble JMPZ with numeric offset")
    void testJmpzNumeric() throws Exception {
        int[] code = assembler.assemble("JMPZ R0, 10");
        assertEquals(OpCode.JMPZ, OpCode.decodeOpCode(code[0]));
        assertEquals(0, OpCode.decodeR1(code[0]));
        assertEquals(10, OpCode.decodeOffset(code[0]));
    }
    
    @Test
    @DisplayName("Assemble JLT with numeric offset")
    void testJmpnNumeric() throws Exception {
        int[] code = assembler.assemble("JLT R0, R4, -5");
        assertEquals(OpCode.JLT, OpCode.decodeOpCode(code[0]));
        assertEquals(0, OpCode.decodeR1(code[0]));
        assertEquals(4, OpCode.decodeR2(code[0]));
        assertEquals(-5, OpCode.decodeOffset(code[0]));
    }
    
    @Test
    @DisplayName("Assemble with labels")
    void testLabels() throws Exception {
        int[] code = assembler.assemble("""
            start:
                NOP
                JMP start
            """);
        assertEquals(2, code.length);
        assertEquals(OpCode.NOP, OpCode.decodeOpCode(code[0]));
        assertEquals(OpCode.JMP, OpCode.decodeOpCode(code[1]));
        // JMP at addr 1, target is addr 0, offset = 0 - 1 - 1 = -2
        assertEquals(-2, OpCode.decodeOffset(code[1]));
    }
    
    @Test
    @DisplayName("Assemble with forward label reference")
    void testForwardLabel() throws Exception {
        int[] code = assembler.assemble("""
                JMP skip
                NOP
                NOP
            skip:
                NOP
            """);
        assertEquals(4, code.length);
        // JMP at addr 0, target is addr 3, offset = 3 - 0 - 1 = 2
        assertEquals(2, OpCode.decodeOffset(code[0]));
    }
    
    @Test
    @DisplayName("Assemble SEARCH")
    void testSearch() throws Exception {
        int[] code = assembler.assemble("SEARCH R0, R1, R2, R3");
        assertEquals(OpCode.SEARCH, OpCode.decodeOpCode(code[0]));
        assertEquals(0, OpCode.decodeR1(code[0]));
        assertEquals(1, OpCode.decodeR2(code[0]));
        assertEquals(2, OpCode.decodeR3(code[0]));
        assertEquals(3, OpCode.decodeR4(code[0]));
    }
    
    @Test
    @DisplayName("Assemble system calls")
    void testSyscalls() throws Exception {
        int[] code = assembler.assemble("""
            ALLOCATE R4, R3
            SPAWN R3, R4
            COPY R5, R6
            """);
        assertEquals(3, code.length);
        assertEquals(OpCode.ALLOCATE, OpCode.decodeOpCode(code[0]));
        assertEquals(OpCode.SPAWN, OpCode.decodeOpCode(code[1]));
        assertEquals(OpCode.COPY, OpCode.decodeOpCode(code[2]));
    }
    
    @Test
    @DisplayName("Assemble memory operations")
    void testMemoryOps() throws Exception {
        int[] code = assembler.assemble("""
            LOAD R0, R1
            STORE R2, R3
            """);
        assertEquals(2, code.length);
        assertEquals(OpCode.LOAD, OpCode.decodeOpCode(code[0]));
        assertEquals(OpCode.STORE, OpCode.decodeOpCode(code[1]));
    }
    
    @Test
    @DisplayName("Comments are stripped")
    void testComments() throws Exception {
        int[] code = assembler.assemble("""
            ; This is a comment
            NOP  ; Inline comment
            ; Another comment
            MOV R0, R1
            """);
        assertEquals(2, code.length);
    }
    
    @Test
    @DisplayName("Case insensitive")
    void testCaseInsensitive() throws Exception {
        int[] code = assembler.assemble("""
            nop
            Mov r0, R1
            movi r2, 100
            """);
        assertEquals(3, code.length);
        assertEquals(OpCode.NOP, OpCode.decodeOpCode(code[0]));
        assertEquals(OpCode.MOV, OpCode.decodeOpCode(code[1]));
        assertEquals(OpCode.MOVI, OpCode.decodeOpCode(code[2]));
    }
    
    @Test
    @DisplayName("Assemble Adam v3")
    void testAdamV3() throws Exception {
        // Full Adam v3 genome
        int[] code = assembler.assemble("""
            ; Adam v3 - self-replicating organism
            ; Position-independent code for ISA v1.2
            
            start:
                GETADDR R7        ; R7 = my start address
                MOVI R4, 14       ; R4 = genome size
                ALLOCATE R4, R3   ; R3 = child address
            
            ; Initialize pointers
                MOV R5, R7        ; R5 = src pointer
                MOV R6, R3        ; R6 = dst pointer
                MOVI R0, 0        ; R0 = counter
            
            ; Copy loop
            loop:
                COPY R5, R6       ; Copy with mutation!
                INC R5
                INC R6
                INC R0
                JLT R0, R4, loop ; if R0 < R4 goto loop
            
            ; Spawn and repeat
                SPAWN R3, R4
                MOVI R0, 0        ; Reset counter
                JMP start         ; Endless loop
            """);
        
        assertEquals(14, code.length, "Adam v3 should be 14 instructions");
        
        // Verify key instructions
        assertEquals(OpCode.GETADDR, OpCode.decodeOpCode(code[0]));
        assertEquals(OpCode.MOVI, OpCode.decodeOpCode(code[1]));
        assertEquals(14, OpCode.decodeImmediate(code[1]));
        assertEquals(OpCode.ALLOCATE, OpCode.decodeOpCode(code[2]));
        assertEquals(OpCode.COPY, OpCode.decodeOpCode(code[6]));
        assertEquals(OpCode.JLT, OpCode.decodeOpCode(code[10]));
        assertEquals(OpCode.SPAWN, OpCode.decodeOpCode(code[11]));
        assertEquals(OpCode.JMP, OpCode.decodeOpCode(code[13]));
    }
    
    @Test
    @DisplayName("Assemble .word with hex value")
    void testWordHex() throws Exception {
        int[] code = assembler.assemble(".word 0x0280000E");
        assertEquals(1, code.length);
        assertEquals(0x0280000E, code[0]);
        // This is MOVI R4, 14
        assertEquals(OpCode.MOVI, OpCode.decodeOpCode(code[0]));
        assertEquals(4, OpCode.decodeR1(code[0]));
        assertEquals(14, OpCode.decodeImmediate(code[0]));
    }
    
    @Test
    @DisplayName("Assemble .word with salted instruction")
    void testWordSalted() throws Exception {
        // MOVI R4, 14 with "salt" in reserved bits
        int[] code = assembler.assemble(".word 0x0280FFFE");
        assertEquals(1, code.length);
        assertEquals(0x0280FFFE, code[0]);
        // Still decodes as MOVI R4, but with different immediate
        assertEquals(OpCode.MOVI, OpCode.decodeOpCode(code[0]));
        assertEquals(4, OpCode.decodeR1(code[0]));
    }
    
    @Test
    @DisplayName("Assemble .word with decimal value")
    void testWordDecimal() throws Exception {
        int[] code = assembler.assemble(".word 42");
        assertEquals(1, code.length);
        assertEquals(42, code[0]);
    }
    
    @Test
    @DisplayName("Assemble .word case insensitive")
    void testWordCaseInsensitive() throws Exception {
        int[] code1 = assembler.assemble(".word 0xFF");
        int[] code2 = assembler.assemble(".WORD 0xFF");
        assertEquals(code1[0], code2[0]);
    }
    
    @Test
    @DisplayName("Assemble mixed instructions and .word")
    void testMixedWithWord() throws Exception {
        int[] code = assembler.assemble("""
            GETADDR R7
            .word 0x0280FFFE    ; Salted MOVI R4, 14
            ALLOCATE R4, R3
            """);
        assertEquals(3, code.length);
        assertEquals(OpCode.GETADDR, OpCode.decodeOpCode(code[0]));
        assertEquals(0x0280FFFE, code[1]);
        assertEquals(OpCode.ALLOCATE, OpCode.decodeOpCode(code[2]));
    }
    
    @Test
    @DisplayName("Error: unknown instruction")
    void testUnknownInstruction() {
        assertThrows(Assembler.AssemblerException.class, () -> 
            assembler.assemble("INVALID R0"));
    }
    
    @Test
    @DisplayName("Error: invalid register")
    void testInvalidRegister() {
        assertThrows(Assembler.AssemblerException.class, () ->
            assembler.assemble("MOV R8, R0"));
    }
    
    @Test
    @DisplayName("Error: undefined label")
    void testUndefinedLabel() {
        assertThrows(Assembler.AssemblerException.class, () ->
            assembler.assemble("JMP nowhere"));
    }
    
    @Test
    @DisplayName("Error: duplicate label")
    void testDuplicateLabel() {
        assertThrows(Assembler.AssemblerException.class, () ->
            assembler.assemble("""
                start:
                    NOP
                start:
                    NOP
                """));
    }
    
    @Test
    @DisplayName("Error: immediate out of range")
    void testImmediateOutOfRange() {
        assertThrows(Assembler.AssemblerException.class, () ->
            assembler.assemble("MOVI R0, 3000000"));
    }
    
    @Test
    @DisplayName("Round-trip: assemble then disassemble")
    void testRoundTrip() throws Exception {
        String source = """
            GETADDR R7
            MOVI R4, 14
            ALLOCATE R4, R3
            """;
        
        int[] code = assembler.assemble(source);
        
        // Disassemble back
        for (int i = 0; i < code.length; i++) {
            String disasm = Disassembler.disassemble(code[i]);
            assertNotNull(disasm);
            assertFalse(disasm.contains("???"));
        }
    }
}

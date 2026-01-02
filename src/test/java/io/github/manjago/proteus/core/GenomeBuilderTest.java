package io.github.manjago.proteus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.manjago.proteus.core.OpCode.*;

class GenomeBuilderTest {

    @Test
    @DisplayName("Empty builder produces empty array")
    void emptyBuilder() {
        int[] genome = GenomeBuilder.create().build();
        assertEquals(0, genome.length);
    }

    @Test
    @DisplayName("Builder produces correct instruction count")
    void correctInstructionCount() {
        int[] genome = GenomeBuilder.create()
            .nop()
            .inc(0)
            .dec(1)
            .build();
        
        assertEquals(3, genome.length);
    }

    @Test
    @DisplayName("NOP encodes correctly")
    void nopEncodes() {
        int[] genome = GenomeBuilder.create().nop().build();
        assertEquals(encode(NOP), genome[0]);
    }

    @Test
    @DisplayName("NOP with register encodes correctly")
    void nopWithRegister() {
        int[] genome = GenomeBuilder.create().nop(3).build();
        assertEquals(encode(NOP, 3), genome[0]);
    }

    @Test
    @DisplayName("MOV encodes correctly")
    void movEncodes() {
        int[] genome = GenomeBuilder.create().mov(1, 2).build();
        
        OpCode op = decodeOpCode(genome[0]);
        assertEquals(MOV, op);
        assertEquals(1, decodeR1(genome[0]));
        assertEquals(2, decodeR2(genome[0]));
    }

    @Test
    @DisplayName("Arithmetic instructions encode correctly")
    void arithmeticEncodes() {
        int[] genome = GenomeBuilder.create()
            .add(0, 1)
            .sub(2, 3)
            .inc(4)
            .dec(5)
            .build();
        
        assertEquals(ADD, decodeOpCode(genome[0]));
        assertEquals(SUB, decodeOpCode(genome[1]));
        assertEquals(INC, decodeOpCode(genome[2]));
        assertEquals(DEC, decodeOpCode(genome[3]));
    }

    @Test
    @DisplayName("Memory instructions encode correctly")
    void memoryEncodes() {
        int[] genome = GenomeBuilder.create()
            .load(0, 1)
            .store(2, 3)
            .build();
        
        assertEquals(LOAD, decodeOpCode(genome[0]));
        assertEquals(STORE, decodeOpCode(genome[1]));
    }

    @Test
    @DisplayName("Control flow instructions encode correctly (v1.2 relative)")
    void controlFlowEncodes() {
        int[] genome = GenomeBuilder.create()
            .jmp(-5)              // JMP with offset
            .jmpz(1, 10)          // JMPZ with R_cond and offset
            .jlt(3, 4, -3)       // JLT with R_a, R_b and offset
            .build();
        
        assertEquals(JMP, decodeOpCode(genome[0]));
        assertEquals(JMPZ, decodeOpCode(genome[1]));
        assertEquals(JLT, decodeOpCode(genome[2]));
        
        // v1.2: verify offsets are encoded correctly
        assertEquals(-5, decodeOffset(genome[0]));
        assertEquals(10, decodeOffset(genome[1]));
        assertEquals(-3, decodeOffset(genome[2]));
        
        // v1.2: verify registers for conditional jumps
        assertEquals(1, decodeR1(genome[1]));  // JMPZ R_cond
        assertEquals(3, decodeR1(genome[2]));  // JLT R_a
        assertEquals(4, decodeR2(genome[2]));  // JLT R_b
    }

    @Test
    @DisplayName("System call instructions encode correctly")
    void syscallEncodes() {
        int[] genome = GenomeBuilder.create()
            .copy(0, 1)
            .allocate(2, 3)
            .spawn(4, 5)
            .build();
        
        assertEquals(COPY, decodeOpCode(genome[0]));
        assertEquals(ALLOCATE, decodeOpCode(genome[1]));
        assertEquals(SPAWN, decodeOpCode(genome[2]));
    }

    @Test
    @DisplayName("SEARCH encodes correctly with 4 registers")
    void searchEncodes() {
        int[] genome = GenomeBuilder.create()
            .search(0, 1, 2, 3)
            .build();
        
        assertEquals(SEARCH, decodeOpCode(genome[0]));
        assertEquals(0, decodeR1(genome[0]));
        assertEquals(1, decodeR2(genome[0]));
        assertEquals(2, decodeR3(genome[0]));
        assertEquals(3, decodeR4(genome[0]));
    }

    @Test
    @DisplayName("Raw instruction can be added")
    void rawInstruction() {
        int[] genome = GenomeBuilder.create()
            .raw(0xDEADBEEF)
            .build();
        
        assertEquals(0xDEADBEEF, genome[0]);
    }

    @Test
    @DisplayName("Label returns current position")
    void labelReturnsPosition() {
        GenomeBuilder b = GenomeBuilder.create();
        
        assertEquals(0, b.label());
        b.nop();
        assertEquals(1, b.label());
        b.inc(0);
        b.dec(1);
        assertEquals(3, b.label());
    }

    @Test
    @DisplayName("Size returns current instruction count")
    void sizeReturnsCount() {
        GenomeBuilder b = GenomeBuilder.create();
        
        assertEquals(0, b.size());
        b.nop();
        assertEquals(1, b.size());
        b.inc(0).dec(1).add(2, 3);
        assertEquals(4, b.size());
    }

    @Test
    @DisplayName("Disassemble produces readable output")
    void disassembleProducesOutput() {
        String asm = GenomeBuilder.create()
            .nop()
            .inc(0)
            .jmp(-2)  // v1.2: relative offset
            .disassemble();
        
        assertTrue(asm.contains("NOP"));
        assertTrue(asm.contains("INC R0"));
        assertTrue(asm.contains("JMP"));
        assertTrue(asm.contains("-2"));  // offset should be visible
    }

    @Test
    @DisplayName("Fluent interface allows chaining")
    void fluentInterface() {
        // This should compile and work (v1.2 API)
        int[] genome = GenomeBuilder.create()
            .nop()
            .mov(0, 1)
            .add(0, 2)
            .sub(0, 3)
            .inc(0)
            .dec(1)
            .load(2, 3)
            .store(4, 5)
            .getaddr(6)           // v1.2: new instruction
            .jmp(-5)              // v1.2: relative offset
            .jmpz(0, 3)           // v1.2: R_cond, offset
            .jlt(1, 2, -3)       // v1.2: R_a, R_b, offset
            .copy(2, 3)
            .allocate(4, 5)
            .spawn(6, 7)
            .search(0, 1, 2, 3)
            .build();
        
        assertEquals(16, genome.length);
    }
    
    @Test
    @DisplayName("GETADDR encodes correctly (v1.2)")
    void getaddrEncodes() {
        int[] genome = GenomeBuilder.create()
            .getaddr(5)
            .build();
        
        assertEquals(GETADDR, decodeOpCode(genome[0]));
        assertEquals(5, decodeR1(genome[0]));
    }
}

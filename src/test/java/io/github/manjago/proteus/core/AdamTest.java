package io.github.manjago.proteus.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Adam genome loaded from adam.asm resource.
 */
@DisplayName("Adam (from adam.asm)")
class AdamTest {
    
    @Test
    @DisplayName("Genome loads successfully from resource")
    void genomeLoadsFromResource() {
        int[] genome = Adam.genome();
        
        assertNotNull(genome);
        assertEquals(14, genome.length, "Adam should be 14 instructions");
    }
    
    @Test
    @DisplayName("Genome size matches actual length")
    void sizeMatchesGenomeLength() {
        assertEquals(Adam.genome().length, Adam.size());
    }
    
    @Test
    @DisplayName("Source code is available")
    void sourceCodeAvailable() {
        String source = Adam.source();
        
        assertNotNull(source);
        assertTrue(source.contains("ADAM"), "Should contain ADAM in header");
        assertTrue(source.contains("copy_loop"), "Should contain copy_loop label");
        assertTrue(source.contains("SPAWN"), "Should contain SPAWN instruction");
    }
    
    @Test
    @DisplayName("Disassembly works")
    void disassemblyWorks() {
        String disasm = Adam.disassemble();
        
        assertNotNull(disasm);
        assertTrue(disasm.contains("MOVI"), "Should contain MOVI");
        assertTrue(disasm.contains("GETADDR"), "Should contain GETADDR");
        assertTrue(disasm.contains("SPAWN"), "Should contain SPAWN");
        assertTrue(disasm.contains("JLT"), "Should contain JLT (not JMPN)");
    }
    
    @Test
    @DisplayName("First instruction is MOVI R4, 14")
    void firstInstructionIsMovi() {
        int[] genome = Adam.genome();
        
        assertEquals(OpCode.MOVI, OpCode.decodeOpCode(genome[0]));
        assertEquals(4, OpCode.decodeR1(genome[0])); // R4
        assertEquals(14, OpCode.decodeImmediate(genome[0])); // value 14
    }
    
    @Test
    @DisplayName("Genome contains all required instructions")
    void containsRequiredInstructions() {
        int[] genome = Adam.genome();
        
        boolean hasGetaddr = false;
        boolean hasAllocate = false;
        boolean hasCopy = false;
        boolean hasSpawn = false;
        boolean hasJlt = false;
        boolean hasJmp = false;
        
        for (int instruction : genome) {
            OpCode op = OpCode.decodeOpCode(instruction);
            switch (op) {
                case GETADDR -> hasGetaddr = true;
                case ALLOCATE -> hasAllocate = true;
                case COPY -> hasCopy = true;
                case SPAWN -> hasSpawn = true;
                case JLT -> hasJlt = true;
                case JMP -> hasJmp = true;
                default -> {}
            }
        }
        
        assertTrue(hasGetaddr, "Should have GETADDR");
        assertTrue(hasAllocate, "Should have ALLOCATE");
        assertTrue(hasCopy, "Should have COPY");
        assertTrue(hasSpawn, "Should have SPAWN");
        assertTrue(hasJlt, "Should have JLT");
        assertTrue(hasJmp, "Should have JMP");
    }
    
    @Test
    @DisplayName("Genome is cached (same instance)")
    void genomeIsCached() {
        int[] g1 = Adam.genome();
        int[] g2 = Adam.genome();
        
        // Should return clones (defensive copy)
        assertNotSame(g1, g2, "Should return clones");
        assertArrayEquals(g1, g2, "But content should be equal");
    }
}

package io.github.manjago.proteus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.manjago.proteus.core.OpCode.*;
import static io.github.manjago.proteus.core.Disassembler.*;

class DisassemblerTest {

    @Test
    @DisplayName("NOP disassembles correctly")
    void nopDisassembly() {
        int instruction = encode(NOP);
        assertEquals("NOP", disassemble(instruction));
    }

    @Test
    @DisplayName("MOV R3, R5 disassembles correctly")
    void movDisassembly() {
        int instruction = encode(MOV, 3, 5);
        assertEquals("MOV R3, R5", disassemble(instruction));
    }

    @Test
    @DisplayName("Arithmetic instructions disassemble correctly")
    void arithmeticDisassembly() {
        assertEquals("ADD R0, R1", disassemble(encode(ADD, 0, 1)));
        assertEquals("SUB R2, R3", disassemble(encode(SUB, 2, 3)));
        assertEquals("INC R7", disassemble(encode(INC, 7)));
        assertEquals("DEC R4", disassemble(encode(DEC, 4)));
    }

    @Test
    @DisplayName("Memory instructions show bracket notation")
    void memoryDisassembly() {
        assertEquals("LOAD R0, [R1]", disassemble(encode(LOAD, 0, 1)));
        assertEquals("STORE [R2], R3", disassemble(encode(STORE, 2, 3)));
    }

    @Test
    @DisplayName("Jump instructions disassemble correctly")
    void jumpDisassembly() {
        assertEquals("JMP [R5]", disassemble(encode(JMP, 5)));
        assertEquals("JMPZ R0, [R1]", disassemble(encode(JMPZ, 0, 1)));
        assertEquals("JMPN R2, [R3]", disassemble(encode(JMPN, 2, 3)));
    }

    @Test
    @DisplayName("System call instructions disassemble correctly")
    void syscallDisassembly() {
        assertEquals("COPY [R0], [R1]", disassemble(encode(COPY, 0, 1)));
        assertEquals("ALLOCATE R2, R3", disassemble(encode(ALLOCATE, 2, 3)));
        assertEquals("SPAWN R4, R5", disassemble(encode(SPAWN, 4, 5)));
    }

    @Test
    @DisplayName("SEARCH with 4 registers disassembles correctly")
    void searchDisassembly() {
        int instruction = encode(SEARCH, 0, 1, 2, 7);
        assertEquals("SEARCH R0, R1, R2, R7", disassemble(instruction));
    }

    @Test
    @DisplayName("Unknown opcode shows hex dump")
    void unknownOpcodeShowsHex() {
        // Create instruction with undefined opcode 0xFF
        int instruction = 0xFF_000000;
        String result = disassemble(instruction);
        assertTrue(result.startsWith("???"));
        assertTrue(result.contains("FF000000"));
    }

    @Test
    @DisplayName("Array disassembly shows addresses")
    void arrayDisassemblyWithAddresses() {
        int[] code = {
            encode(NOP),
            encode(INC, 0),
            encode(JMP, 1)
        };
        
        String result = disassemble(code);
        
        assertTrue(result.contains("0000: NOP"));
        assertTrue(result.contains("0001: INC R0"));
        assertTrue(result.contains("0002: JMP [R1]"));
    }

    @Test
    @DisplayName("Array disassembly respects base address")
    void arrayDisassemblyWithBaseAddress() {
        int[] code = {
            encode(NOP),
            encode(INC, 0)
        };
        
        String result = disassemble(code, 0x1000);
        
        assertTrue(result.contains("1000: NOP"));
        assertTrue(result.contains("1001: INC R0"));
    }

    @Test
    @DisplayName("Hex dump mode shows raw instruction bytes")
    void hexDumpMode() {
        int[] code = {
            encode(NOP),
            encode(MOV, 3, 5)
        };
        
        String result = disassembleWithHex(code, 0);
        
        // NOP = 0x00000000
        assertTrue(result.contains("00000000"));
        assertTrue(result.contains("NOP"));
        
        // MOV R3, R5 = opcode 0x01, r1=3, r2=5
        // 0x01 << 24 | 3 << 21 | 5 << 18 = 0x016A0000
        assertTrue(result.contains("MOV R3, R5"));
    }

    @Test
    @DisplayName("Empty array returns empty string")
    void emptyArrayReturnsEmptyString() {
        assertEquals("", disassemble(new int[0]));
        assertEquals("", disassemble(null));
        assertEquals("", disassembleWithHex(new int[0], 0));
        assertEquals("", disassembleWithHex(null, 0));
    }

    @Test
    @DisplayName("Round-trip: encode -> disassemble produces readable output")
    void roundTripAllInstructions() {
        // This is more of a smoke test - ensure all opcodes can be disassembled
        for (OpCode op : OpCode.values()) {
            int instruction = encode(op, 1, 2, 3, 4);
            String asm = disassemble(instruction);
            
            assertNotNull(asm);
            assertFalse(asm.isEmpty());
            assertTrue(asm.contains(op.getMnemonic()), 
                "Disassembly of " + op + " should contain mnemonic");
        }
    }

    @Test
    @DisplayName("Simple Adam-like program disassembles readably")
    void simpleAdamProgram() {
        // A tiny self-copy loop (not real Adam, just for testing)
        int[] code = {
            encode(LOAD, 0, 1),    // R0 = memory[R1] (read source)
            encode(STORE, 2, 0),   // memory[R2] = R0 (write dest)
            encode(INC, 1),        // R1++ (next source)
            encode(INC, 2),        // R2++ (next dest)
            encode(DEC, 3),        // R3-- (counter)
            encode(JMPN, 3, 4)     // if R3 != 0, jump to R4
        };
        
        String result = disassemble(code);
        System.out.println("=== Simple Copy Loop ===");
        System.out.println(result);
        System.out.println("========================");
        
        // Just verify it doesn't throw and produces output
        assertEquals(6, result.split("\n").length);
    }
}

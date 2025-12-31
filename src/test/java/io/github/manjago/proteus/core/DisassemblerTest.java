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
    @DisplayName("Jump instructions disassemble with offsets (v1.2)")
    void jumpDisassembly() {
        assertEquals("JMP +5", disassemble(encodeJump(5)));
        assertEquals("JMP -10", disassemble(encodeJump(-10)));
        assertEquals("JMPZ R0, +3", disassemble(encodeJumpZero(0, 3)));
        assertEquals("JMPN R2, R3, -5", disassemble(encodeJumpLess(2, 3, -5)));
    }
    
    @Test
    @DisplayName("GETADDR disassembles correctly (v1.2)")
    void getaddrDisassembly() {
        assertEquals("GETADDR R5", disassemble(encode(GETADDR, 5)));
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
            encodeJump(-2)  // v1.2: relative offset
        };
        
        String result = disassemble(code);
        
        assertTrue(result.contains("0000: NOP"));
        assertTrue(result.contains("0001: INC R0"));
        assertTrue(result.contains("0002: JMP -2"));
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
        // This is a smoke test - ensure all opcodes can be disassembled
        for (OpCode op : OpCode.values()) {
            int instruction;
            
            // v1.2: JMP/JMPZ/JMPN need special encoding
            instruction = switch (op) {
                case JMP -> encodeJump(5);
                case JMPZ -> encodeJumpZero(0, 5);
                case JMPN -> encodeJumpLess(0, 1, 5);
                default -> encode(op, 1, 2, 3, 4);
            };
            
            String asm = disassemble(instruction);
            
            assertNotNull(asm);
            assertFalse(asm.isEmpty());
            assertTrue(asm.contains(op.getMnemonic()), 
                "Disassembly of " + op + " should contain mnemonic");
        }
    }

    @Test
    @DisplayName("Simple copy loop disassembles readably (v1.2)")
    void simpleAdamProgram() {
        // A tiny copy loop using v1.2 instructions
        int[] code = {
            encode(LOAD, 0, 1),       // R0 = memory[startAddr + R1]
            encode(STORE, 2, 0),      // memory[startAddr + R2] = R0
            encode(INC, 1),           // R1++
            encode(INC, 2),           // R2++
            encode(INC, 3),           // R3++ (counter)
            encodeJumpLess(3, 4, -6)  // if R3 < R4, jump -6 (to start)
        };
        
        String result = disassemble(code);
        System.out.println("=== Simple Copy Loop (v1.2) ===");
        System.out.println(result);
        System.out.println("===============================");
        
        // Verify output contains expected instructions
        assertTrue(result.contains("LOAD"));
        assertTrue(result.contains("STORE"));
        assertTrue(result.contains("JMPN R3, R4, -6"));
        assertEquals(6, result.split("\n").length);
    }
}

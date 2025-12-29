package io.github.manjago.proteus.core;

import org.jetbrains.annotations.NotNull;

import static io.github.manjago.proteus.core.OpCode.*;

/**
 * Disassembler for Proteus ISA v1.0
 * <p>
 * Converts machine code (int[]) to human-readable assembly text.
 */
public final class Disassembler {
    
    private Disassembler() {
        // Utility class
    }
    
    /**
     * Disassemble a single instruction to mnemonic form.
     * 
     * @param instruction encoded 32-bit instruction
     * @return assembly string like "MOV R3, R5" or "NOP"
     */
    public static @NotNull String disassemble(int instruction) {
        final OpCode op = decodeOpCode(instruction);
        
        if (op == null) {
            return String.format("??? 0x%08X", instruction);
        }
        
        final int r1 = decodeR1(instruction);
        final int r2 = decodeR2(instruction);
        final int r3 = decodeR3(instruction);
        final int r4 = decodeR4(instruction);
        
        return switch (op) {
            // No operands
            case NOP -> "NOP";
            
            // 1 operand
            case INC -> String.format("INC R%d", r1);
            case DEC -> String.format("DEC R%d", r1);
            case JMP -> String.format("JMP [R%d]", r1);
            
            // 2 operands - register to register
            case MOV -> String.format("MOV R%d, R%d", r1, r2);
            case ADD -> String.format("ADD R%d, R%d", r1, r2);
            case SUB -> String.format("SUB R%d, R%d", r1, r2);
            
            // 2 operands - memory operations
            case LOAD  -> String.format("LOAD R%d, [R%d]", r1, r2);
            case STORE -> String.format("STORE [R%d], R%d", r1, r2);
            
            // 2 operands - conditional jumps
            case JMPZ -> String.format("JMPZ R%d, [R%d]", r1, r2);
            case JMPN -> String.format("JMPN R%d, [R%d]", r1, r2);
            
            // 2 operands - system calls
            case COPY     -> String.format("COPY [R%d], [R%d]", r1, r2);
            case ALLOCATE -> String.format("ALLOCATE R%d, R%d", r1, r2);
            case SPAWN    -> String.format("SPAWN R%d, R%d", r1, r2);
            
            // 4 operands
            case SEARCH -> String.format("SEARCH R%d, R%d, R%d, R%d", r1, r2, r3, r4);
        };
    }
    
    /**
     * Disassemble an array of instructions with addresses.
     * 
     * @param code array of encoded instructions
     * @return multi-line assembly listing
     */
    public static @NotNull String disassemble(int[] code) {
        return disassemble(code, 0);
    }
    
    /**
     * Disassemble an array of instructions with addresses starting from baseAddress.
     * 
     * @param code array of encoded instructions
     * @param baseAddress starting address for listing
     * @return multi-line assembly listing
     */
    public static @NotNull String disassemble(int[] code, int baseAddress) {
        if (code == null || code.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(String.format("%04X: %s", baseAddress + i, disassemble(code[i])));
        }
        return sb.toString();
    }
    
    /**
     * Disassemble with hex dump of raw instruction.
     * 
     * @param code array of encoded instructions
     * @param baseAddress starting address for listing
     * @return multi-line assembly listing with hex dump
     */
    public static @NotNull String disassembleWithHex(int[] code, int baseAddress) {
        if (code == null || code.length == 0) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < code.length; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            int instruction = code[i];
            sb.append(String.format("%04X: %08X  %s", 
                baseAddress + i, 
                instruction, 
                disassemble(instruction)));
        }
        return sb.toString();
    }
}

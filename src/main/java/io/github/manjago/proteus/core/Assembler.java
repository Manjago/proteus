package io.github.manjago.proteus.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Assembler for Proteus ISA v1.2.
 * 
 * Converts text assembly to machine code (int[]).
 * 
 * <h2>Syntax:</h2>
 * <pre>
 * ; Comment (from ; to end of line)
 * label:           ; Label definition
 * MNEMONIC [operands]
 * .word 0xNNNNNNNN ; Raw 32-bit word
 * </pre>
 * 
 * <h2>Example:</h2>
 * <pre>
 * ; My organism
 * start:
 *     GETADDR R7        ; Get my address
 *     .word 0x0280FFFE  ; MOVI R4, 14 with "salt" for parasite protection
 * loop:
 *     COPY R5, R6       ; Copy with mutation
 *     INC R5
 *     INC R6
 *     INC R0
 *     JMPN R0, R4, loop ; if R0 < R4 goto loop
 *     SPAWN R3, R4
 *     JMP start
 * </pre>
 * 
 * <h2>Supported instructions:</h2>
 * <ul>
 *   <li>NOP</li>
 *   <li>MOV Rd, Rs</li>
 *   <li>MOVI Rd, imm21</li>
 *   <li>GETADDR Rd</li>
 *   <li>ADD Ra, Rb</li>
 *   <li>SUB Ra, Rb</li>
 *   <li>INC Ra</li>
 *   <li>DEC Ra</li>
 *   <li>LOAD Rd, Roff</li>
 *   <li>STORE Roff, Rs</li>
 *   <li>JMP offset|label</li>
 *   <li>JMPZ Rc, offset|label</li>
 *   <li>JMPN Ra, Rb, offset|label</li>
 *   <li>COPY Rs, Rd</li>
 *   <li>ALLOCATE Rsize, Raddr</li>
 *   <li>SPAWN Raddr, Rsize</li>
 *   <li>SEARCH Rs, Rt, Rl, Rf</li>
 *   <li>.word hex|decimal (raw 32-bit value)</li>
 * </ul>
 */
public class Assembler {
    
    private static final Logger log = LoggerFactory.getLogger(Assembler.class);
    
    // Regex patterns
    private static final Pattern COMMENT_PATTERN = Pattern.compile(";.*$");
    private static final Pattern LABEL_DEF_PATTERN = Pattern.compile("^\\s*(\\w+):\\s*$");
    private static final Pattern REGISTER_PATTERN = Pattern.compile("R(\\d)", Pattern.CASE_INSENSITIVE);
    private static final Pattern NUMBER_PATTERN = Pattern.compile("-?\\d+");
    private static final Pattern HEX_PATTERN = Pattern.compile("0[xX][0-9A-Fa-f]+");
    private static final Pattern WORD_DIRECTIVE_PATTERN = Pattern.compile("^\\.word\\s+(.+)$", Pattern.CASE_INSENSITIVE);
    
    /**
     * Assemble source code from string.
     * 
     * @param source assembly source code
     * @return machine code as int[]
     * @throws AssemblerException if syntax error
     */
    public int[] assemble(String source) throws AssemblerException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new StringReader(source))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        } catch (IOException e) {
            throw new AssemblerException("Failed to read source", e);
        }
        
        return assembleLines(lines);
    }
    
    /**
     * Assemble source code from file.
     * 
     * @param path path to .asm file
     * @return machine code as int[]
     * @throws AssemblerException if syntax error or IO error
     */
    public int[] assembleFile(Path path) throws AssemblerException {
        try {
            List<String> lines = Files.readAllLines(path);
            return assembleLines(lines);
        } catch (IOException e) {
            throw new AssemblerException("Failed to read file: " + path, e);
        }
    }
    
    /**
     * Assemble list of source lines.
     * 
     * Two-pass assembly:
     * 1. First pass: collect labels and their addresses
     * 2. Second pass: generate machine code, resolve label references
     */
    private int[] assembleLines(List<String> lines) throws AssemblerException {
        // First pass: collect labels
        Map<String, Integer> labels = new HashMap<>();
        List<ParsedLine> parsed = new ArrayList<>();
        int address = 0;
        
        for (int lineNum = 0; lineNum < lines.size(); lineNum++) {
            String line = lines.get(lineNum);
            String stripped = stripComment(line).trim();
            
            if (stripped.isEmpty()) {
                continue;  // Skip empty lines
            }
            
            // Check for label definition
            Matcher labelMatcher = LABEL_DEF_PATTERN.matcher(stripped);
            if (labelMatcher.matches()) {
                String labelName = labelMatcher.group(1).toLowerCase();
                if (labels.containsKey(labelName)) {
                    throw new AssemblerException("Duplicate label: " + labelName, lineNum + 1);
                }
                labels.put(labelName, address);
                log.debug("Label '{}' at address {}", labelName, address);
                continue;  // Label doesn't generate code
            }
            
            // Parse instruction
            ParsedLine pl = new ParsedLine(lineNum + 1, address, stripped);
            parsed.add(pl);
            address++;
        }
        
        // Second pass: generate code
        int[] code = new int[parsed.size()];
        
        for (int i = 0; i < parsed.size(); i++) {
            ParsedLine pl = parsed.get(i);
            try {
                code[i] = assembleLine(pl, labels);
            } catch (AssemblerException e) {
                throw e;  // Already has line number
            } catch (Exception e) {
                throw new AssemblerException("Error at line " + pl.lineNum + ": " + e.getMessage(), 
                        pl.lineNum, e);
            }
        }
        
        log.info("Assembled {} instructions from {} lines", code.length, lines.size());
        return code;
    }
    
    /**
     * Strip comment from line.
     */
    private String stripComment(String line) {
        return COMMENT_PATTERN.matcher(line).replaceAll("");
    }
    
    /**
     * Assemble single instruction line.
     */
    private int assembleLine(ParsedLine pl, Map<String, Integer> labels) throws AssemblerException {
        // Check for .word directive first
        Matcher wordMatcher = WORD_DIRECTIVE_PATTERN.matcher(pl.content);
        if (wordMatcher.matches()) {
            return parseRawWord(wordMatcher.group(1).trim(), pl.lineNum);
        }
        
        String[] tokens = pl.content.split("[,\\s]+");
        if (tokens.length == 0 || tokens[0].isEmpty()) {
            throw new AssemblerException("Empty instruction", pl.lineNum);
        }
        
        String mnemonic = tokens[0].toUpperCase();
        
        // Check for .word without pattern match (e.g. ".WORD" case)
        if (mnemonic.equals(".WORD")) {
            if (tokens.length < 2) {
                throw new AssemblerException("Not enough operands. Usage: .word 0xNNNNNNNN", pl.lineNum);
            }
            return parseRawWord(tokens[1], pl.lineNum);
        }
        
        OpCode op = findOpCode(mnemonic);
        
        if (op == null) {
            throw new AssemblerException("Unknown instruction: " + mnemonic, pl.lineNum);
        }
        
        return switch (op) {
            case NOP -> OpCode.encode(OpCode.NOP);
            
            case MOV -> {
                requireOperands(tokens, 3, pl.lineNum, "MOV Rd, Rs");
                yield OpCode.encode(OpCode.MOV, parseRegister(tokens[1], pl.lineNum), 
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case MOVI -> {
                requireOperands(tokens, 3, pl.lineNum, "MOVI Rd, imm");
                yield OpCode.encodeImm(OpCode.MOVI, parseRegister(tokens[1], pl.lineNum),
                        parseImmediate(tokens[2], pl.lineNum));
            }
            
            case GETADDR -> {
                requireOperands(tokens, 2, pl.lineNum, "GETADDR Rd");
                yield OpCode.encode(OpCode.GETADDR, parseRegister(tokens[1], pl.lineNum));
            }
            
            case ADD -> {
                requireOperands(tokens, 3, pl.lineNum, "ADD Ra, Rb");
                yield OpCode.encode(OpCode.ADD, parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case SUB -> {
                requireOperands(tokens, 3, pl.lineNum, "SUB Ra, Rb");
                yield OpCode.encode(OpCode.SUB, parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case INC -> {
                requireOperands(tokens, 2, pl.lineNum, "INC Ra");
                yield OpCode.encode(OpCode.INC, parseRegister(tokens[1], pl.lineNum));
            }
            
            case DEC -> {
                requireOperands(tokens, 2, pl.lineNum, "DEC Ra");
                yield OpCode.encode(OpCode.DEC, parseRegister(tokens[1], pl.lineNum));
            }
            
            case LOAD -> {
                requireOperands(tokens, 3, pl.lineNum, "LOAD Rd, Roff");
                yield OpCode.encode(OpCode.LOAD, parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case STORE -> {
                requireOperands(tokens, 3, pl.lineNum, "STORE Roff, Rs");
                yield OpCode.encode(OpCode.STORE, parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case JMP -> {
                requireOperands(tokens, 2, pl.lineNum, "JMP offset|label");
                int offset = parseOffsetOrLabel(tokens[1], pl.address, labels, pl.lineNum);
                yield OpCode.encodeJump(offset);
            }
            
            case JMPZ -> {
                requireOperands(tokens, 3, pl.lineNum, "JMPZ Rc, offset|label");
                int reg = parseRegister(tokens[1], pl.lineNum);
                int offset = parseOffsetOrLabel(tokens[2], pl.address, labels, pl.lineNum);
                yield OpCode.encodeJumpZero(reg, offset);
            }
            
            case JMPN -> {
                requireOperands(tokens, 4, pl.lineNum, "JMPN Ra, Rb, offset|label");
                int ra = parseRegister(tokens[1], pl.lineNum);
                int rb = parseRegister(tokens[2], pl.lineNum);
                int offset = parseOffsetOrLabel(tokens[3], pl.address, labels, pl.lineNum);
                yield OpCode.encodeJumpLess(ra, rb, offset);
            }
            
            case COPY -> {
                requireOperands(tokens, 3, pl.lineNum, "COPY Rs, Rd");
                yield OpCode.encode(OpCode.COPY, parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case ALLOCATE -> {
                requireOperands(tokens, 3, pl.lineNum, "ALLOCATE Rsize, Raddr");
                yield OpCode.encode(OpCode.ALLOCATE, parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case SPAWN -> {
                requireOperands(tokens, 3, pl.lineNum, "SPAWN Raddr, Rsize");
                yield OpCode.encode(OpCode.SPAWN, parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum));
            }
            
            case SEARCH -> {
                requireOperands(tokens, 5, pl.lineNum, "SEARCH Rs, Rt, Rl, Rf");
                yield OpCode.encode(OpCode.SEARCH,
                        parseRegister(tokens[1], pl.lineNum),
                        parseRegister(tokens[2], pl.lineNum),
                        parseRegister(tokens[3], pl.lineNum),
                        parseRegister(tokens[4], pl.lineNum));
            }
        };
    }
    
    /**
     * Find OpCode by mnemonic (case-insensitive).
     */
    private OpCode findOpCode(String mnemonic) {
        for (OpCode op : OpCode.values()) {
            if (op.getMnemonic().equalsIgnoreCase(mnemonic)) {
                return op;
            }
        }
        return null;
    }
    
    /**
     * Validate operand count.
     */
    private void requireOperands(String[] tokens, int required, int lineNum, String usage) 
            throws AssemblerException {
        if (tokens.length < required) {
            throw new AssemblerException("Not enough operands. Usage: " + usage, lineNum);
        }
    }
    
    /**
     * Parse register (R0-R7).
     */
    private int parseRegister(String token, int lineNum) throws AssemblerException {
        Matcher m = REGISTER_PATTERN.matcher(token.trim());
        if (!m.matches()) {
            throw new AssemblerException("Invalid register: " + token + " (expected R0-R7)", lineNum);
        }
        int reg = Integer.parseInt(m.group(1));
        if (reg < 0 || reg > 7) {
            throw new AssemblerException("Register out of range: " + token + " (expected R0-R7)", lineNum);
        }
        return reg;
    }
    
    /**
     * Parse immediate value (21-bit unsigned).
     */
    private int parseImmediate(String token, int lineNum) throws AssemblerException {
        try {
            int value = Integer.parseInt(token.trim());
            if (value < 0 || value > 0x1FFFFF) {
                throw new AssemblerException("Immediate out of range: " + value + 
                        " (expected 0-2097151)", lineNum);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new AssemblerException("Invalid immediate: " + token, lineNum);
        }
    }
    
    /**
     * Parse offset (numeric) or label reference.
     * For labels, calculates relative offset from current address.
     */
    private int parseOffsetOrLabel(String token, int currentAddr, Map<String, Integer> labels, int lineNum) 
            throws AssemblerException {
        String trimmed = token.trim();
        
        // Try numeric offset first
        if (NUMBER_PATTERN.matcher(trimmed).matches()) {
            try {
                int offset = Integer.parseInt(trimmed);
                // Check 18-bit signed range
                if (offset < -131072 || offset > 131071) {
                    throw new AssemblerException("Offset out of range: " + offset + 
                            " (expected -131072 to +131071)", lineNum);
                }
                return offset;
            } catch (NumberFormatException e) {
                throw new AssemblerException("Invalid offset: " + token, lineNum);
            }
        }
        
        // Try label
        String labelName = trimmed.toLowerCase();
        if (!labels.containsKey(labelName)) {
            throw new AssemblerException("Undefined label: " + trimmed, lineNum);
        }
        
        int targetAddr = labels.get(labelName);
        // Calculate relative offset: target - (current + 1)
        // +1 because IP advances before jump is applied
        int offset = targetAddr - currentAddr - 1;
        
        // Check range
        if (offset < -131072 || offset > 131071) {
            throw new AssemblerException("Label too far: " + trimmed + " (offset=" + offset + ")", lineNum);
        }
        
        return offset;
    }
    
    /**
     * Parse raw word (hex or decimal) for .word directive.
     */
    private int parseRawWord(String token, int lineNum) throws AssemblerException {
        String trimmed = token.trim();
        
        try {
            // Try hex first (0x...)
            if (HEX_PATTERN.matcher(trimmed).matches()) {
                // Parse as unsigned, handle overflow to int
                long value = Long.parseLong(trimmed.substring(2), 16);
                if (value > 0xFFFFFFFFL || value < 0) {
                    throw new AssemblerException("Hex value out of 32-bit range: " + trimmed, lineNum);
                }
                return (int) value;
            }
            
            // Try decimal
            long value = Long.parseLong(trimmed);
            if (value > 0xFFFFFFFFL || value < Integer.MIN_VALUE) {
                throw new AssemblerException("Value out of 32-bit range: " + trimmed, lineNum);
            }
            return (int) value;
            
        } catch (NumberFormatException e) {
            throw new AssemblerException("Invalid .word value: " + trimmed + 
                    " (expected hex 0xNNNNNNNN or decimal)", lineNum);
        }
    }
    
    // ========== Helper classes ==========
    
    /**
     * Parsed line with metadata.
     */
    private record ParsedLine(int lineNum, int address, String content) {}
    
    /**
     * Exception during assembly.
     */
    public static class AssemblerException extends Exception {
        private final int lineNum;
        
        public AssemblerException(String message) {
            super(message);
            this.lineNum = -1;
        }
        
        public AssemblerException(String message, int lineNum) {
            super("Line " + lineNum + ": " + message);
            this.lineNum = lineNum;
        }
        
        public AssemblerException(String message, Throwable cause) {
            super(message, cause);
            this.lineNum = -1;
        }
        
        public AssemblerException(String message, int lineNum, Throwable cause) {
            super("Line " + lineNum + ": " + message, cause);
            this.lineNum = lineNum;
        }
        
        public int getLineNum() {
            return lineNum;
        }
    }
}

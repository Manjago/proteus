package io.github.manjago.proteus.core;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;

/**
 * Adam v3 - the first self-replicating organism (ISA v1.2).
 * 
 * This is the "ancestor" from which all other organisms will evolve.
 * Genome is loaded from {@code adam.asm} resource file.
 * 
 * <h2>Features (Position-Independent Code):</h2>
 * <ul>
 *   <li>Uses GETADDR to get absolute start address</li>
 *   <li>Uses relative JMP offsets instead of absolute addresses</li>
 *   <li>Works correctly from any memory location</li>
 *   <li>Can be safely defragmented (moved in memory)</li>
 * </ul>
 * 
 * @see Assembler
 */
public final class Adam {
    
    private static final String RESOURCE_PATH = "/adam.asm";
    
    /** Cached genome (lazily loaded) */
    private static int[] cachedGenome;
    
    private Adam() {}
    
    /**
     * Get Adam's genome, assembled from adam.asm resource.
     * Result is cached for performance.
     * 
     * @return assembled genome
     * @throws RuntimeException if resource not found or assembly fails
     */
    public static int[] genome() {
        if (cachedGenome == null) {
            cachedGenome = loadAndAssemble();
        }
        return cachedGenome.clone(); // Return copy to prevent modification
    }
    
    /**
     * Get Adam's genome size.
     */
    public static int size() {
        return genome().length;
    }
    
    /**
     * Get disassembly of Adam's genome.
     */
    public static String disassemble() {
        return Disassembler.disassembleWithHex(genome(), 0);
    }
    
    /**
     * Get the source assembly code.
     */
    public static String source() {
        return loadResource();
    }
    
    private static int[] loadAndAssemble() {
        String source = loadResource();
        Assembler assembler = new Assembler();
        try {
            return assembler.assemble(source);
        } catch (Assembler.AssemblerException e) {
            throw new RuntimeException("Failed to assemble Adam genome: " + e.getMessage(), e);
        }
    }
    
    private static String loadResource() {
        try (InputStream is = Adam.class.getResourceAsStream(RESOURCE_PATH)) {
            if (is == null) {
                throw new RuntimeException("Adam genome not found: " + RESOURCE_PATH);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                return reader.lines().collect(Collectors.joining("\n"));
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to load Adam genome", e);
        }
    }
    
    /**
     * Print Adam's genome for debugging.
     */
    public static void main(String[] args) {
        System.out.println("=== ADAM v3: Position-Independent Self-Replicator ===");
        System.out.println("Size: " + size() + " instructions\n");
        System.out.println("--- Source (adam.asm) ---");
        System.out.println(source());
        System.out.println("\n--- Assembled & Disassembled ---");
        System.out.println(disassemble());
    }
}

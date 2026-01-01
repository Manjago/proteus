package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.core.Assembler;
import io.github.manjago.proteus.core.Disassembler;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.nio.file.Path;
import java.util.concurrent.Callable;

/**
 * CLI command: assemble
 * 
 * Assembles text assembly source into binary machine code.
 * 
 * Usage:
 *   proteus assemble input.asm -o output.bin
 *   proteus assemble input.asm --disasm  (show disassembly)
 */
@Command(
    name = "assemble",
    description = "Assemble text source to machine code",
    mixinStandardHelpOptions = true
)
public class AssembleCommand implements Callable<Integer> {
    
    @Parameters(index = "0", description = "Input assembly file (.asm)")
    private Path inputFile;
    
    @Option(names = {"-o", "--output"}, description = "Output binary file (.bin)")
    private Path outputFile;
    
    @Option(names = {"-d", "--disasm"}, description = "Show disassembly after assembly")
    private boolean showDisassembly;
    
    @Option(names = {"-v", "--verbose"}, description = "Verbose output")
    private boolean verbose;
    
    @Override
    public Integer call() {
        try {
            System.out.println("Assembling: " + inputFile);
            
            // Assemble
            Assembler assembler = new Assembler();
            int[] code = assembler.assembleFile(inputFile);
            
            System.out.println("✓ Assembled " + code.length + " instructions");
            
            // Show disassembly if requested
            if (showDisassembly || verbose) {
                System.out.println();
                System.out.println("=== Disassembly ===");
                Disassembler disasm = new Disassembler();
                for (int i = 0; i < code.length; i++) {
                    String hex = String.format("0x%08X", code[i]);
                    String asm = disasm.disassemble(code[i]);
                    System.out.printf("%04X: %s  ; %s%n", i, hex, asm);
                }
                System.out.println();
            }
            
            // Write output file if specified
            if (outputFile != null) {
                writeBinaryFile(code, outputFile);
                System.out.println("✓ Written to: " + outputFile);
            } else if (!showDisassembly) {
                // Default: generate .bin with same name
                String name = inputFile.getFileName().toString();
                if (name.endsWith(".asm")) {
                    name = name.substring(0, name.length() - 4);
                }
                Path defaultOutput = inputFile.resolveSibling(name + ".bin");
                writeBinaryFile(code, defaultOutput);
                System.out.println("✓ Written to: " + defaultOutput);
            }
            
            return 0;
            
        } catch (Assembler.AssemblerException e) {
            System.err.println("❌ Assembly error: " + e.getMessage());
            return 1;
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            if (verbose) {
                e.printStackTrace();
            }
            return 1;
        }
    }
    
    /**
     * Write code as binary file (32-bit big-endian integers).
     */
    private void writeBinaryFile(int[] code, Path path) throws Exception {
        try (DataOutputStream out = new DataOutputStream(new FileOutputStream(path.toFile()))) {
            for (int instruction : code) {
                out.writeInt(instruction);
            }
        }
    }
}

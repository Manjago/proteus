package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.Assembler;
import io.github.manjago.proteus.debug.*;
import io.github.manjago.proteus.persistence.CheckpointStore;
import io.github.manjago.proteus.sim.Simulator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * CLI command: debug
 * 
 * Run simulation in debug mode with frame recording.
 * 
 * Usage:
 *   proteus debug --cycles 40                       # Record 40 cycles with Adam
 *   proteus debug --cycles 40 --inject org.asm     # Inject custom organism
 *   proteus debug --cycles 2000 --from 1950        # Show only cycles 1950+
 *   proteus debug --cycles 100 --output out.txt    # Output to file
 *   proteus debug --cycles 100 --save state.mv     # Save checkpoint
 *   proteus debug --cycles 100 --resume state.mv   # Resume from checkpoint
 */
@Command(
    name = "debug",
    description = "Run simulation in debug mode with frame recording",
    mixinStandardHelpOptions = true
)
public class DebugCommand implements Callable<Integer> {
    
    @Option(names = {"-c", "--cycles"}, description = "Number of cycles to run", defaultValue = "40")
    private int cycles;
    
    @Option(names = {"-s", "--soup-size"}, description = "Soup size (ignored if --resume)", defaultValue = "1000")
    private int soupSize;
    
    @Option(names = {"-i", "--inject"}, description = "Inject organism from .asm file")
    private Path injectFile;
    
    @Option(names = {"-n", "--name"}, description = "Name for injected organism")
    private String injectName;
    
    @Option(names = {"--no-adam"}, description = "Don't seed Adam (use with --inject)")
    private boolean noAdam;
    
    @Option(names = {"--resume"}, description = "Resume from checkpoint file (.mv)")
    private Path resumeCheckpoint;
    
    @Option(names = {"--save"}, description = "Save checkpoint after run (.mv)")
    private Path saveCheckpoint;
    
    @Option(names = {"--from"}, description = "Show cycles starting from this number")
    private Long fromCycle;
    
    @Option(names = {"--to"}, description = "Show cycles up to this number (inclusive)")
    private Long toCycle;
    
    @Option(names = {"-o", "--output"}, description = "Output file (default: stdout)")
    private Path outputFile;
    
    @Option(names = {"--summary"}, description = "Show only summary table")
    private boolean summaryOnly;
    
    @Option(names = {"--seed"}, description = "Random seed (ignored if --resume)")
    private Long seed;
    
    @Override
    public Integer call() {
        PrintStream out = System.out;
        
        try {
            // Setup output
            if (outputFile != null) {
                out = new PrintStream(new FileOutputStream(outputFile.toFile()));
            }
            
            printHeader(out);
            
            Simulator sim;
            long startCycle;
            
            if (resumeCheckpoint != null) {
                // Resume from checkpoint
                out.println("📂 Resuming from checkpoint: " + resumeCheckpoint);
                out.println("   " + CheckpointStore.getInfo(resumeCheckpoint));
                out.println();
                
                // Note: seed is ignored when resuming!
                if (seed != null) {
                    out.println("⚠️  Note: --seed is ignored when resuming (using checkpoint RNG state)");
                    out.println();
                }
                
                sim = CheckpointStore.restore(resumeCheckpoint, null);
                startCycle = sim.getTotalCycles();
                
                out.println("Configuration (from checkpoint):");
                out.printf("  Soup size:    %,d cells%n", sim.getConfig().soupSize());
                out.printf("  Start cycle:  %,d%n", startCycle);
                out.printf("  Organisms:    %d%n", sim.getAliveOrganisms().size());
                out.printf("  Seed:         %d%n", sim.getActualSeed());
                out.printf("  Run cycles:   %,d (to cycle %,d)%n", cycles, startCycle + cycles);
                out.println();
                
            } else {
                // Fresh start
                long effectiveSeed = seed != null ? seed : System.currentTimeMillis();
                
                SimulatorConfig config = SimulatorConfig.builder()
                        .soupSize(soupSize)
                        .maxCycles(cycles)
                        .maxOrganisms(100)
                        .randomSeed(effectiveSeed)
                        .reportInterval(Integer.MAX_VALUE)
                        .build();
                
                sim = new Simulator(config);
                startCycle = 0;
                
                out.println("Configuration:");
                out.printf("  Soup size:  %,d cells%n", soupSize);
                out.printf("  Cycles:     %,d%n", cycles);
                out.printf("  Seed:       %d%n", effectiveSeed);
                
                if (fromCycle != null || toCycle != null) {
                    out.printf("  Show range: %s to %s%n", 
                        fromCycle != null ? fromCycle.toString() : "0",
                        toCycle != null ? toCycle.toString() : "end");
                }
                out.println();
                
                // Seed Adam unless disabled
                if (!noAdam) {
                    sim.seedAdam();
                    out.println("🌱 Seeded Adam#0");
                }
            }
            
            // Setup frame recorder
            FrameRecorder recorder = new FrameRecorder(cycles);
            sim.setFrameRecorder(recorder);
            
            // Name existing organisms
            for (var org : sim.getAliveOrganisms()) {
                String name = org.getParentId() < 0 ? "Adam" : null;
                recorder.setOrganismName(org.getId(), name);
            }
            
            // Inject custom organism if specified
            if (injectFile != null) {
                int[] genome = assembleFile(injectFile, out);
                if (genome == null) {
                    return 1;
                }
                
                String name = injectName != null ? injectName : injectFile.getFileName().toString().replace(".asm", "");
                var org = sim.injectOrganism(genome, name);
                if (org == null) {
                    out.println("❌ Failed to inject organism");
                    return 1;
                }
                out.println("💉 Injected " + name + "#" + org.getId() + " (" + genome.length + " instructions)");
            }
            
            out.println();
            out.println("▶️  Running " + cycles + " cycles...");
            out.println();
            
            // Run simulation
            sim.run(cycles);
            
            // Filter frames by cycle range
            List<Frame> frames = recorder.getFrames();
            if (fromCycle != null || toCycle != null) {
                final long from = fromCycle != null ? fromCycle : 0;
                final long to = toCycle != null ? toCycle : Long.MAX_VALUE;
                frames = frames.stream()
                        .filter(f -> f.cycle() >= from && f.cycle() <= to)
                        .collect(Collectors.toList());
                out.printf("Showing %d cycles (filtered from %d total)%n%n", frames.size(), recorder.getFrameCount());
            }
            
            // Print results
            FramePrinter printer = new FramePrinter(out);
            
            if (summaryOnly) {
                printer.printSummary(frames);
            } else {
                printer.printAll(frames);
            }
            
            // Save checkpoint if requested
            if (saveCheckpoint != null) {
                CheckpointStore.save(sim, saveCheckpoint);
                out.println();
                out.println("💾 Checkpoint saved: " + saveCheckpoint);
                out.println("   " + CheckpointStore.getInfo(saveCheckpoint));
            }
            
            return 0;
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        } finally {
            if (outputFile != null && out != System.out) {
                out.close();
            }
        }
    }
    
    private void printHeader(PrintStream out) {
        out.println("═".repeat(70));
        out.println("PROTEUS DEBUG MODE - Frame Recording");
        out.println("═".repeat(70));
        out.println();
    }
    
    private int[] assembleFile(Path path, PrintStream out) {
        try {
            out.println("📝 Assembling: " + path);
            Assembler asm = new Assembler();
            return asm.assembleFile(path);
        } catch (Exception e) {
            out.println("❌ Assembly error: " + e.getMessage());
            return null;
        }
    }
}

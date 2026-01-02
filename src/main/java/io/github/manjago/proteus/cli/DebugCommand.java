package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.Assembler;
import io.github.manjago.proteus.debug.*;
import io.github.manjago.proteus.sim.Simulator;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;

/**
 * CLI command: debug
 * 
 * Run simulation in debug mode with frame recording.
 * 
 * Usage:
 *   proteus debug --cycles 40                    # Record 40 cycles with Adam
 *   proteus debug --cycles 40 --inject org.asm  # Inject custom organism
 *   proteus debug --resume checkpoint.bin       # Resume from checkpoint
 */
@Command(
    name = "debug",
    description = "Run simulation in debug mode with frame recording",
    mixinStandardHelpOptions = true
)
public class DebugCommand implements Callable<Integer> {
    
    @Option(names = {"-c", "--cycles"}, description = "Number of cycles to record", defaultValue = "40")
    private int cycles;
    
    @Option(names = {"-s", "--soup-size"}, description = "Soup size", defaultValue = "1000")
    private int soupSize;
    
    @Option(names = {"-i", "--inject"}, description = "Inject organism from .asm file")
    private Path injectFile;
    
    @Option(names = {"-n", "--name"}, description = "Name for injected organism")
    private String injectName;
    
    @Option(names = {"--no-adam"}, description = "Don't seed Adam (use with --inject)")
    private boolean noAdam;
    
    @Option(names = {"--resume"}, description = "Resume from checkpoint file")
    private Path resumeCheckpoint;
    
    @Option(names = {"--save"}, description = "Save checkpoint after recording")
    private Path saveCheckpoint;
    
    @Option(names = {"--compact"}, description = "Compact output (less detail)")
    private boolean compact;
    
    @Option(names = {"--summary"}, description = "Show only summary table")
    private boolean summaryOnly;
    
    @Option(names = {"--seed"}, description = "Random seed")
    private Long seed;
    
    @Override
    public Integer call() {
        try {
            System.out.println("═".repeat(70));
            System.out.println("DEBUG MODE - Frame Recording");
            System.out.println("═".repeat(70));
            System.out.println();
            
            // Create config
            SimulatorConfig.Builder configBuilder = SimulatorConfig.builder()
                    .soupSize(soupSize)
                    .maxCycles(cycles)
                    .maxOrganisms(100)
                    .reportInterval(Integer.MAX_VALUE);  // Disable progress reports
            
            if (seed != null) {
                configBuilder.randomSeed(seed);
            }
            
            SimulatorConfig config = configBuilder.build();
            Simulator sim = new Simulator(config);
            
            // Setup frame recorder
            FrameRecorder recorder = new FrameRecorder(cycles);
            sim.setFrameRecorder(recorder);
            
            // Seed or resume
            if (resumeCheckpoint != null) {
                System.out.println("⚠️  Resume from checkpoint not yet implemented");
                System.out.println("    (Checkpoint file: " + resumeCheckpoint + ")");
                return 1;
            }
            
            // Seed Adam unless disabled
            if (!noAdam) {
                sim.seedAdam();
                recorder.setOrganismName(0, "Adam");
                System.out.println("🌱 Seeded Adam");
            }
            
            // Inject custom organism if specified
            if (injectFile != null) {
                int[] genome = assembleFile(injectFile);
                if (genome == null) {
                    return 1;
                }
                
                String name = injectName != null ? injectName : injectFile.getFileName().toString().replace(".asm", "");
                var org = sim.injectOrganism(genome, name);
                if (org == null) {
                    System.err.println("❌ Failed to inject organism");
                    return 1;
                }
                System.out.println("💉 Injected '" + name + "' (" + genome.length + " instructions)");
            }
            
            System.out.println();
            System.out.println("▶️  Running " + cycles + " cycles...");
            System.out.println();
            
            // Run simulation
            sim.run(cycles);
            
            // Print results
            List<Frame> frames = recorder.getFrames();
            FramePrinter printer = new FramePrinter();
            printer.compactMode(compact);
            
            if (summaryOnly) {
                printer.printSummary(frames);
            } else {
                printer.printAll(frames);
            }
            
            // Save checkpoint if requested
            if (saveCheckpoint != null) {
                Checkpoint.save(sim, saveCheckpoint);
                System.out.println();
                System.out.println("💾 Checkpoint saved: " + saveCheckpoint);
            }
            
            return 0;
            
        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            return 1;
        }
    }
    
    private int[] assembleFile(Path path) {
        try {
            System.out.println("📝 Assembling: " + path);
            Assembler asm = new Assembler();
            return asm.assembleFile(path);
        } catch (Exception e) {
            System.err.println("❌ Assembly error: " + e.getMessage());
            return null;
        }
    }
}

package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.Assembler;
import io.github.manjago.proteus.core.Organism;
import io.github.manjago.proteus.debug.*;
import io.github.manjago.proteus.persistence.CheckpointStore;
import io.github.manjago.proteus.sim.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

/**
 * Run simulation command.
 * 
 * Examples:
 *   proteus run                           # Run with defaults
 *   proteus run -c 100000                 # Run 100K cycles
 *   proteus run --config my.conf          # Use custom config
 *   proteus run --soup-size 50000 -m 0.01 # Override params
 */
@Command(
    name = "run",
    description = "Run a new simulation",
    mixinStandardHelpOptions = true
)
public class RunCommand implements Callable<Integer> {
    
    private static final Logger log = LoggerFactory.getLogger(RunCommand.class);
    
    @Option(names = {"-f", "--config"}, description = "Configuration file (HOCON)")
    private Path configFile;
    
    @Option(names = {"-c", "--cycles"}, description = "Max cycles (0 = infinite)")
    private Long maxCycles;
    
    @Option(names = {"-s", "--soup-size"}, description = "Soup size (memory cells)")
    private Integer soupSize;
    
    @Option(names = {"-m", "--mutation-rate"}, description = "Mutation rate (0.0-1.0)")
    private Double mutationRate;
    
    @Option(names = {"--seed"}, description = "Random seed for reproducibility (0 = random)")
    private Long randomSeed;
    
    @Option(names = {"--max-organisms"}, description = "Maximum living organisms")
    private Integer maxOrganisms;
    
    @Option(names = {"--resume"}, description = "Resume from checkpoint file (.mv)")
    private Path resumeCheckpoint;
    
    @Option(names = {"--save"}, description = "Save checkpoint after run (.mv)")
    private Path saveCheckpoint;
    
    @Option(names = {"--checkpoint-interval"}, description = "Auto-save checkpoint every N cycles (requires --save)")
    private Long checkpointInterval;
    
    @Option(names = {"--report-interval"}, description = "Progress report interval (cycles)")
    private Integer reportInterval;
    
    @Option(names = {"-q", "--quiet"}, description = "Quiet mode (minimal output)")
    private boolean quiet;
    
    @Option(names = {"-i", "--inject"}, description = "Inject organism from .asm file (required if not --resume)")
    private Path injectFile;
    
    @Option(names = {"-n", "--name"}, description = "Name for injected organism")
    private String injectName;
    
    // Debug options (frame recording)
    @Option(names = {"--debug"}, description = "Enable debug mode with frame recording (output to file or '-' for stdout)")
    private String debugOutput;
    
    @Option(names = {"--from"}, description = "Show cycles starting from this number (requires --debug)")
    private Long fromCycle;
    
    @Option(names = {"--to"}, description = "Show cycles up to this number (requires --debug)")
    private Long toCycle;
    
    @Option(names = {"--summary"}, description = "Show only summary table (requires --debug)")
    private boolean summaryOnly;
    
    @Override
    public Integer call() {
        Simulator simulator = null;
        ConsoleProgressListener progressListener = null;
        long startTime = System.currentTimeMillis();
        
        try {
            if (resumeCheckpoint != null) {
                // Resume from checkpoint
                if (!quiet) {
                    printBanner();
                    System.out.println("📂 Resuming from checkpoint: " + resumeCheckpoint);
                    System.out.println("   " + CheckpointStore.getInfo(resumeCheckpoint));
                    System.out.println();
                    
                    if (randomSeed != null) {
                        System.out.println("⚠️  Note: --seed is ignored when resuming");
                    }
                }
                
                // Load checkpoint data first to get saved config values
                var checkpointData = CheckpointStore.load(resumeCheckpoint);
                
                // Build config override: start with checkpoint values, override CLI args
                var builder = SimulatorConfig.builder()
                        .soupSize(checkpointData.soupSize())
                        .mutationRate(checkpointData.mutationRate())
                        .maxErrors(checkpointData.maxErrors())
                        .maxOrganisms(maxOrganisms != null ? maxOrganisms : checkpointData.maxOrganisms())
                        .maxCycles(maxCycles != null ? maxCycles : 0)
                        .reportInterval(reportInterval != null ? reportInterval : 1000)
                        .checkpointInterval(checkpointInterval != null ? checkpointInterval : 0)
                        .randomSeed(checkpointData.seed());
                
                SimulatorConfig configOverride = builder.build();
                
                simulator = CheckpointStore.restore(resumeCheckpoint, configOverride);
                
                if (!quiet) {
                    System.out.println("Configuration (from checkpoint):");
                    System.out.printf("  Soup size:    %,d cells%n", simulator.getConfig().soupSize());
                    System.out.printf("  Start cycle:  %,d%n", simulator.getTotalCycles());
                    System.out.printf("  Organisms:    %d%n", simulator.getAliveOrganisms().size());
                    System.out.printf("  Max organisms:%,d%n", simulator.getConfig().maxOrganisms());
                    System.out.printf("  Seed:         %d%n", simulator.getActualSeed());
                    if (maxCycles != null) {
                        System.out.printf("  Run cycles:   %,d (to cycle %,d)%n", maxCycles, simulator.getTotalCycles() + maxCycles);
                    }
                    System.out.println();
                }
                
                // Optional: inject additional organism into resumed world
                if (injectFile != null) {
                    try {
                        String source = Files.readString(injectFile);
                        Assembler assembler = new Assembler();
                        int[] genome = assembler.assemble(source);
                        
                        String name = injectName != null ? injectName : 
                            injectFile.getFileName().toString().replace(".asm", "");
                        
                        Organism org = simulator.injectOrganism(genome, name);
                        if (org == null) {
                            System.err.println("❌ Failed to inject organism (allocation failed)");
                            return 1;
                        }
                        
                        if (!quiet) {
                            System.out.printf("💉 Injected %s#%d (%d instructions)%n", name, org.getId(), genome.length);
                        }
                    } catch (Assembler.AssemblerException e) {
                        System.err.println("❌ Assembly error: " + e.getMessage());
                        return 1;
                    } catch (Exception e) {
                        System.err.println("❌ Failed to read " + injectFile + ": " + e.getMessage());
                        return 1;
                    }
                }
                
            } else {
                // Fresh start - requires --inject
                if (injectFile == null) {
                    System.err.println("❌ Error: --inject is required for a fresh start");
                    System.err.println("   Use --inject <file.asm> to specify initial organism");
                    System.err.println("   Or use --resume <checkpoint.mv> to continue from saved state");
                    return 1;
                }
                
                SimulatorConfig config = buildConfig();
                
                if (!quiet) {
                    printBanner();
                    printConfig(config);
                }
                
                simulator = new Simulator(config);
                
                // Inject organism from file
                try {
                    String source = Files.readString(injectFile);
                    Assembler assembler = new Assembler();
                    int[] genome = assembler.assemble(source);
                    
                    String name = injectName != null ? injectName : 
                        injectFile.getFileName().toString().replace(".asm", "");
                    
                    Organism org = simulator.injectOrganism(genome, name);
                    if (org == null) {
                        System.err.println("❌ Failed to inject organism (allocation failed)");
                        return 1;
                    }
                    
                    if (!quiet) {
                        System.out.printf("🌱 Injected %s#%d (%d instructions)%n", name, org.getId(), genome.length);
                    }
                } catch (Assembler.AssemblerException e) {
                    System.err.println("❌ Assembly error: " + e.getMessage());
                    return 1;
                } catch (Exception e) {
                    System.err.println("❌ Failed to read " + injectFile + ": " + e.getMessage());
                    return 1;
                }
            }
            
            // Setup debug mode (frame recording) if requested
            FrameRecorder frameRecorder = null;
            PrintStream debugOut = null;
            boolean isDebugMode = debugOutput != null;
            
            if (isDebugMode) {
                // Setup output stream
                if ("-".equals(debugOutput)) {
                    debugOut = System.out;
                } else {
                    debugOut = new PrintStream(new FileOutputStream(debugOutput));
                }
                
                // Setup frame recorder
                long cyclesToRecord = maxCycles != null ? maxCycles : 1000;
                frameRecorder = new FrameRecorder((int) Math.min(cyclesToRecord, Integer.MAX_VALUE));
                simulator.setFrameRecorder(frameRecorder);
                
                // Register names for existing organisms
                for (var org : simulator.getAliveOrganisms()) {
                    frameRecorder.setOrganismName(org.getId(), org.getName());
                }
                
                // Print debug header
                debugOut.println("═".repeat(70));
                debugOut.println("PROTEUS DEBUG MODE - Frame Recording");
                debugOut.println("═".repeat(70));
                debugOut.println();
            }
            
            // Setup graceful shutdown
            final Simulator sim = simulator;
            final Path checkpointPath = saveCheckpoint;
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                if (sim.isRunning()) {
                    System.out.println("\n⏸️  Stopping gracefully...");
                    sim.stop();
                    try { Thread.sleep(100); } catch (InterruptedException ignored) {}
                    
                    // Auto-save on interrupt if --save specified
                    if (checkpointPath != null) {
                        try {
                            System.out.println("💾 Auto-saving checkpoint...");
                            CheckpointStore.save(sim, checkpointPath);
                            System.out.println("   " + CheckpointStore.getInfo(checkpointPath));
                        } catch (Exception e) {
                            System.err.println("❌ Failed to save checkpoint: " + e.getMessage());
                        }
                    }
                }
            }));
            
            // Set listener for console output and auto-checkpoint
            if (!quiet) {
                progressListener = new ConsoleProgressListener();
                simulator.setListener(progressListener);
                
                // Configure auto-checkpoint if requested
                if (checkpointInterval != null && checkpointInterval > 0 && saveCheckpoint != null) {
                    progressListener.setCheckpointConfig(simulator, saveCheckpoint);
                    // Also configure simulator's checkpoint interval
                    // Note: this requires updating the config which is immutable, so we'll use the listener
                    log.info("Auto-checkpoint every {} cycles to {}", checkpointInterval, saveCheckpoint);
                    if (!quiet) {
                        System.out.printf("📸 Auto-checkpoint every %,d cycles%n", checkpointInterval);
                    }
                }
            }
            
            // Run simulation
            if (!quiet) {
                // Warn about long simulation without auto-checkpoint
                long cyclesToRun = maxCycles != null ? maxCycles : 0;
                boolean hasAutoCheckpoint = checkpointInterval != null && checkpointInterval > 0 && saveCheckpoint != null;
                
                if (cyclesToRun > 100_000 && !hasAutoCheckpoint && saveCheckpoint != null) {
                    System.out.println("⚠️  Long simulation without auto-checkpoint!");
                    System.out.println("   Consider adding --checkpoint-interval 50000");
                    System.out.println("   This will save progress every 50K cycles.");
                    System.out.println();
                }
                
                System.out.println("▶️  Running simulation...\n");
            }
            
            simulator.run(maxCycles != null ? maxCycles : 0);
            
            long elapsed = System.currentTimeMillis() - startTime;
            
            // Clear progress line before final report
            if (progressListener != null) {
                progressListener.finish();
            }
            
            // Output debug frames if in debug mode
            if (isDebugMode && frameRecorder != null) {
                List<Frame> frames = frameRecorder.getFrames();
                
                // Filter by cycle range if specified
                if (fromCycle != null || toCycle != null) {
                    final long from = fromCycle != null ? fromCycle : 0;
                    final long to = toCycle != null ? toCycle : Long.MAX_VALUE;
                    frames = frames.stream()
                            .filter(f -> f.cycle() >= from && f.cycle() <= to)
                            .collect(Collectors.toList());
                    debugOut.printf("Showing %d cycles (filtered from %d total)%n%n", 
                            frames.size(), frameRecorder.getFrameCount());
                }
                
                // Print frames
                FramePrinter printer = new FramePrinter(debugOut);
                if (summaryOnly) {
                    printer.printSummary(frames);
                } else {
                    printer.printAll(frames);
                }
                
                // Close debug output if it's a file
                if (debugOut != null && debugOut != System.out) {
                    debugOut.close();
                }
            }
            
            // Save checkpoint if requested
            if (saveCheckpoint != null) {
                if (!quiet) {
                    System.out.println();
                    System.out.println("💾 Saving checkpoint...");
                }
                CheckpointStore.save(simulator, saveCheckpoint);
                if (!quiet) {
                    System.out.println("   " + CheckpointStore.getInfo(saveCheckpoint));
                }
            }
            
            // Final report
            if (!quiet) {
                printFinalReport(simulator, elapsed);
            }
            
            return 0;
            
        } catch (java.io.IOException e) {
            System.out.println();
            System.out.println("❌ Checkpoint error: " + e.getMessage());
            log.error("Checkpoint I/O error", e);
            e.printStackTrace();
            return 1;
            
        } catch (OutOfMemoryError e) {
            // Force flush logs immediately (before we run out of memory completely)
            log.error("OUT OF MEMORY! Heap exhausted", e);
            
            // Try to force log flush
            try {
                // SLF4J doesn't have direct flush, but we can try to trigger it
                Thread.sleep(100);  // Give async loggers time to flush
            } catch (InterruptedException ignored) {}
            
            // Try to report what we can
            System.out.println();
            System.out.println("❌ OUT OF MEMORY!");
            System.out.printf("   Heap: %,d MB used of %,d MB max%n",
                    Runtime.getRuntime().totalMemory() / (1024 * 1024),
                    Runtime.getRuntime().maxMemory() / (1024 * 1024));
            
            if (simulator != null) {
                try {
                    SimulatorStats stats = simulator.getStats();
                    System.out.printf("   Reaper queue: %,d organisms%n", stats.reaperQueueSize());
                    System.out.printf("   Cycle: %,d  |  Spawns: %,d  |  Alive: %,d%n", 
                            stats.totalCycles(), stats.totalSpawns(), stats.aliveCount());
                    log.error("OOM stats: cycle={}, spawns={}, alive={}, reaperQueue={}", 
                            stats.totalCycles(), stats.totalSpawns(), stats.aliveCount(), stats.reaperQueueSize());
                } catch (Throwable ignored) {
                    // Can't get stats - too little memory
                }
            }
            
            System.out.println();
            System.out.println("💡 Try: increase -Xmx or reduce --max-organisms");
            System.out.println();
            
            return 1;
        }
    }
    
    private void printBanner() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║          PROTEUS Simulator            ║");
        System.out.println("║     Artificial Life Evolution         ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.println();
    }
    
    private void printConfig(SimulatorConfig config) {
        System.out.println("Configuration:");
        System.out.printf("  Soup size:       %,d cells%n", config.soupSize());
        System.out.printf("  Mutation rate:   %.2f%%%n", config.mutationRate() * 100);
        System.out.printf("  Max organisms:   %,d%n", config.maxOrganisms());
        System.out.printf("  Max cycles:      %s%n", 
                config.maxCycles() == 0 ? "∞ (infinite)" : String.format("%,d", config.maxCycles()));
        if (config.randomSeed() != 0) {
            System.out.printf("  Seed:            %d%n", config.randomSeed());
        }
        System.out.println();
    }
    
    private SimulatorConfig buildConfig() {
        SimulatorConfig.Builder builder;
        
        if (configFile != null) {
            // Start from config file
            SimulatorConfig base = SimulatorConfig.fromFile(configFile);
            builder = SimulatorConfig.builder()
                    .soupSize(base.soupSize())
                    .mutationRate(base.mutationRate())
                    .randomSeed(base.randomSeed())
                    .reaperStrategy(base.reaperStrategy())
                    .maxErrors(base.maxErrors())
                    .maxCycles(base.maxCycles())
                    .maxOrganisms(base.maxOrganisms())
                    .dataFile(base.dataFile())
                    .checkpointInterval(base.checkpointInterval())
                    .reportInterval(base.reportInterval());
        } else {
            // Start from defaults
            builder = SimulatorConfig.builder();
        }
        
        // Override from CLI options
        if (soupSize != null) builder.soupSize(soupSize);
        if (mutationRate != null) builder.mutationRate(mutationRate);
        if (randomSeed != null) builder.randomSeed(randomSeed);
        if (maxOrganisms != null) builder.maxOrganisms(maxOrganisms);
        if (maxCycles != null) builder.maxCycles(maxCycles);
        if (reportInterval != null) builder.reportInterval(reportInterval);
        if (checkpointInterval != null) builder.checkpointInterval(checkpointInterval);
        
        return builder.build();
    }
    
    private void printFinalReport(Simulator simulator, long elapsedMs) {
        SimulatorStats stats = simulator.getStats();
        double speed = stats.totalCycles() * 1000.0 / Math.max(1, elapsedMs);
        
        System.out.println();
        System.out.println("═══════════════════════════════════════");
        System.out.println("         SIMULATION COMPLETE           ");
        System.out.println("═══════════════════════════════════════");
        System.out.println();
        
        // Time & Performance
        System.out.printf("⏱️  Time: %s  |  Speed: %,.0f cycles/sec%n", 
                formatDuration(elapsedMs), speed);
        System.out.println();
        
        // Population
        System.out.println("👥 Population:");
        System.out.printf("   Alive: %,d  |  Max: %,d  |  Total spawns: %,d%n", 
                stats.aliveCount(), stats.maxAlive(), stats.totalSpawns());
        System.out.printf("   Deaths: %,d by errors, %,d by reaper%n",
                stats.deathsByErrors(), stats.deathsByReaper());
        if (stats.rejectedSpawns() > 0) {
            System.out.printf("   Rejected spawns: %,d (invalid addr/size)%n", stats.rejectedSpawns());
        }
        System.out.println();
        
        // Memory
        System.out.println("💾 Memory:");
        int totalMem = stats.memoryUsed() + stats.memoryFree();
        System.out.printf("   Used: %,d / %,d cells (%.1f%%)%n", 
                stats.memoryUsed(), totalMem,
                100.0 * stats.memoryUsed() / totalMem);
        System.out.printf("   Fragmentation: %.1f%%  |  Largest free: %,d%n",
                stats.fragmentation() * 100, stats.largestFreeBlock());
        if (stats.defragmentations() > 0) {
            double defragRate = stats.defragmentations() * 10000.0 / stats.totalCycles();
            System.out.printf("   Defragmentations: %,d (%.1f per 10K cycles)%n", 
                    stats.defragmentations(), defragRate);
        }
        if (stats.memoryLeak() != 0) {
            System.out.printf("   ⚠️  Memory leak: %,d cells%n", stats.memoryLeak());
            // Print detailed diagnostics
            System.out.println("   " + simulator.getMemoryDiagnostics().replace("\n", "\n   "));
        } else {
            System.out.println("   Memory leak: 0 ✓");
        }
        System.out.println();
        
        // Evolution
        System.out.println("🧬 Evolution:");
        System.out.printf("   Mutations: %,d total (%.3f per spawn)%n",
                stats.totalMutations(), stats.mutationsPerSpawn());
        System.out.printf("   Reproduction rate: %.1f cycles/spawn%n",
                stats.cyclesPerSpawn());
        System.out.println();
        
        // JVM
        System.out.println("📦 JVM:");
        System.out.printf("   Heap: %,d / %,d MB (%.1f%%)%n",
                stats.heapUsedMB(), stats.heapMaxMB(), stats.heapUsagePercent());
        System.out.printf("   Reaper queue: %,d%n", stats.reaperQueueSize());
        System.out.println();
        
        // Reproducibility
        System.out.printf("🔑 Seed: %d (use --seed %d to reproduce)%n", 
                simulator.getActualSeed(), simulator.getActualSeed());
        
        System.out.println();
        System.out.println("═══════════════════════════════════════");
    }
    
    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + " ms";
        } else if (ms < 60_000) {
            return String.format("%.1f sec", ms / 1000.0);
        } else {
            long minutes = ms / 60_000;
            long seconds = (ms % 60_000) / 1000;
            return String.format("%d min %d sec", minutes, seconds);
        }
    }
    
    /**
     * Console progress listener with live single-line updates.
     */
    private static class ConsoleProgressListener implements SimulatorListener {
        private static final Logger log = LoggerFactory.getLogger(ConsoleProgressListener.class);
        private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
        private static final String CLEAR_LINE = "\r\033[K"; // ANSI: carriage return + clear to end
        private int spinnerIdx = 0;
        private boolean needsClear = false;
        private final long startTime = System.currentTimeMillis();
        
        // For auto-checkpoint
        private Simulator simulator;
        private Path checkpointPath;
        private long lastCheckpointCycle = 0;
        
        public void setCheckpointConfig(Simulator simulator, Path checkpointPath) {
            this.simulator = simulator;
            this.checkpointPath = checkpointPath;
        }
        
        @Override
        public void onProgress(SimulatorStats stats) {
            String spinner = SPINNER[spinnerIdx++ % SPINNER.length];
            long elapsed = System.currentTimeMillis() - startTime;
            String time = formatElapsed(elapsed);
            
            // Clear line and print progress (no newline)
            System.out.print(CLEAR_LINE);
            System.out.printf("%s [%s] Cycle %,d  |  👥 %d  |  🐣 %d  |  💀 %d  |  📦 %d/%dMB",
                    spinner,
                    time,
                    stats.totalCycles(),
                    stats.aliveCount(),
                    stats.totalSpawns(),
                    stats.deathsByErrors() + stats.deathsByReaper(),
                    stats.heapUsedMB(),
                    stats.heapMaxMB());
            System.out.flush();
            needsClear = true;
        }
        
        private String formatElapsed(long ms) {
            long seconds = ms / 1000;
            if (seconds < 60) {
                return String.format("%ds", seconds);
            } else {
                return String.format("%dm%02ds", seconds / 60, seconds % 60);
            }
        }
        
        @Override
        public void onCheckpoint(long cycle) {
            if (simulator != null && checkpointPath != null && cycle > lastCheckpointCycle) {
                try {
                    CheckpointStore.save(simulator, checkpointPath);
                    lastCheckpointCycle = cycle;
                    log.info("Auto-checkpoint saved at cycle {} to {}", cycle, checkpointPath);
                } catch (Exception e) {
                    log.error("Failed to save auto-checkpoint at cycle {}: {}", cycle, e.getMessage());
                }
            }
        }
        
        /**
         * Emergency checkpoint save when heap is critically full.
         * Called by Simulator when heap usage > 90%.
         */
        public void emergencyCheckpoint(long cycle) {
            if (simulator != null && checkpointPath != null) {
                try {
                    System.out.print(CLEAR_LINE);
                    System.out.println("⚠️  High memory pressure - emergency checkpoint...");
                    CheckpointStore.save(simulator, checkpointPath);
                    lastCheckpointCycle = cycle;
                    log.warn("Emergency checkpoint saved at cycle {} due to memory pressure", cycle);
                    System.out.println("   Checkpoint saved: " + checkpointPath);
                } catch (Exception e) {
                    log.error("Failed emergency checkpoint at cycle {}: {}", cycle, e.getMessage());
                }
            }
        }
        
        @Override
        public void onMemoryPressure(long cycle, int heapUsagePercent) {
            // Save emergency checkpoint if we have a path configured
            if (checkpointPath != null && cycle > lastCheckpointCycle + 10_000) {
                emergencyCheckpoint(cycle);
            }
        }
        
        /**
         * Clear the progress line before final output.
         */
        public void finish() {
            if (needsClear) {
                System.out.print(CLEAR_LINE);
                System.out.flush();
            }
        }
    }
}

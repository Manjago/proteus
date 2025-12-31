package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.Organism;
import io.github.manjago.proteus.sim.*;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.nio.file.Path;
import java.util.concurrent.Callable;

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
    
    @Option(names = {"-f", "--config"}, description = "Configuration file (HOCON)")
    private Path configFile;
    
    @Option(names = {"-c", "--cycles"}, description = "Max cycles (0 = infinite)")
    private Long maxCycles;
    
    @Option(names = {"-s", "--soup-size"}, description = "Soup size (memory cells)")
    private Integer soupSize;
    
    @Option(names = {"-m", "--mutation-rate"}, description = "Mutation rate (0.0-1.0)")
    private Double mutationRate;
    
    @Option(names = {"-o", "--output"}, description = "Output file for state")
    private Path outputFile;
    
    @Option(names = {"--report-interval"}, description = "Progress report interval (cycles)")
    private Integer reportInterval;
    
    @Option(names = {"-q", "--quiet"}, description = "Quiet mode (minimal output)")
    private boolean quiet;
    
    @Override
    public Integer call() {
        // Build configuration
        SimulatorConfig config = buildConfig();
        
        if (!quiet) {
            printBanner();
            printConfig(config);
        }
        
        // Create and configure simulator
        Simulator simulator = new Simulator(config);
        
        // Setup graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (simulator.isRunning()) {
                System.out.println("\n⏸️  Stopping gracefully...");
                simulator.stop();
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }));
        
        // Set listener for console output
        if (!quiet) {
            simulator.setListener(new ConsoleProgressListener());
        }
        
        // Seed Adam
        if (!quiet) {
            System.out.println("🌱 Seeding Adam...");
        }
        simulator.seedAdam();
        
        // Run simulation
        if (!quiet) {
            System.out.println("▶️  Running simulation...\n");
        }
        
        long startTime = System.currentTimeMillis();
        simulator.run(maxCycles != null ? maxCycles : 0);
        long elapsed = System.currentTimeMillis() - startTime;
        
        // Final report
        if (!quiet) {
            printFinalReport(simulator, elapsed);
        }
        
        return 0;
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
        System.out.printf("  Max cycles:      %s%n", 
                config.maxCycles() == 0 ? "∞ (infinite)" : String.format("%,d", config.maxCycles()));
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
        if (maxCycles != null) builder.maxCycles(maxCycles);
        if (outputFile != null) builder.dataFile(outputFile);
        if (reportInterval != null) builder.reportInterval(reportInterval);
        
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
        System.out.printf("   Alive: %,d  |  Total spawns: %,d%n", 
                stats.aliveCount(), stats.totalSpawns());
        System.out.printf("   Deaths: %,d by errors, %,d by reaper%n",
                stats.deathsByErrors(), stats.deathsByReaper());
        System.out.println();
        
        // Memory
        System.out.println("💾 Memory:");
        int totalMem = stats.memoryUsed() + stats.memoryFree();
        System.out.printf("   Used: %,d / %,d cells (%.1f%%)%n", 
                stats.memoryUsed(), totalMem,
                100.0 * stats.memoryUsed() / totalMem);
        System.out.printf("   Fragmentation: %.1f%%  |  Largest free: %,d%n",
                stats.fragmentation() * 100, stats.largestFreeBlock());
        System.out.println();
        
        // Evolution
        System.out.println("🧬 Evolution:");
        System.out.printf("   Mutations: %,d total (%.3f per spawn)%n",
                stats.totalMutations(), stats.mutationsPerSpawn());
        System.out.printf("   Reproduction rate: %.1f cycles/spawn%n",
                stats.cyclesPerSpawn());
        
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
     * Console progress listener with live updates.
     */
    private static class ConsoleProgressListener implements SimulatorListener {
        private static final String[] SPINNER = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
        private int spinnerIdx = 0;
        
        @Override
        public void onProgress(SimulatorStats stats) {
            String spinner = SPINNER[spinnerIdx++ % SPINNER.length];
            
            // Compact one-line progress
            System.out.printf("\r%s Cycle %,d  |  👥 %d alive  |  🐣 %d spawns  |  💀 %d deaths  |  🧬 %d mutations   ",
                    spinner,
                    stats.totalCycles(),
                    stats.aliveCount(),
                    stats.totalSpawns(),
                    stats.deathsByErrors() + stats.deathsByReaper(),
                    stats.totalMutations());
            System.out.flush();
        }
        
        @Override
        public void onCheckpoint(long cycle) {
            System.out.printf("%n💾 Checkpoint at cycle %,d%n", cycle);
        }
    }
}

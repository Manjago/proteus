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
            System.out.println();
            System.out.println("╔═══════════════════════════════════════╗");
            System.out.println("║          PROTEUS Simulator            ║");
            System.out.println("║     Artificial Life Evolution         ║");
            System.out.println("╚═══════════════════════════════════════╝");
            System.out.println();
            System.out.println(config);
        }
        
        // Create and configure simulator
        Simulator simulator = new Simulator(config);
        
        // Setup graceful shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (simulator.isRunning()) {
                System.out.println("\n⚠️  Shutdown requested, stopping gracefully...");
                simulator.stop();
                // Give it a moment to finish current cycle
                try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            }
        }));
        
        // Set listener for console output
        if (!quiet) {
            simulator.setListener(new ConsoleListener());
        }
        
        // Seed Adam
        simulator.seedAdam();
        
        // Run simulation
        long startTime = System.currentTimeMillis();
        simulator.run(maxCycles != null ? maxCycles : 0);
        long elapsed = System.currentTimeMillis() - startTime;
        
        // Final report
        if (!quiet) {
            printFinalReport(simulator, elapsed);
        }
        
        return 0;
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
        
        System.out.println();
        System.out.println("═══════════════════════════════════════");
        System.out.println("           SIMULATION COMPLETE         ");
        System.out.println("═══════════════════════════════════════");
        System.out.println(stats);
        System.out.printf("Time elapsed: %,d ms%n", elapsedMs);
        System.out.printf("Speed: %,.0f cycles/sec%n", 
                stats.totalCycles() * 1000.0 / Math.max(1, elapsedMs));
        System.out.println("═══════════════════════════════════════");
    }
    
    /**
     * Simple console listener for progress output.
     */
    private static class ConsoleListener implements SimulatorListener {
        @Override
        public void onSpawn(Organism child, Organism parent, long cycle) {
            if (child.getId() <= 10 || child.getId() % 100 == 0) {
                String parentStr = parent != null ? String.valueOf(parent.getId()) : "none";
                System.out.printf("  🐣 Spawn #%d (parent: %s) at cycle %,d%n", 
                        child.getId(), parentStr, cycle);
            }
        }
        
        @Override
        public void onProgress(SimulatorStats stats) {
            System.out.printf("   📊 Cycle %,d: %d alive, %d spawns, %d reaped%n",
                    stats.totalCycles(), stats.aliveCount(), 
                    stats.totalSpawns(), stats.deathsByReaper());
        }
    }
}

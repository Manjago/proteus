package io.github.manjago.proteus.config;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import java.nio.file.Path;

/**
 * Configuration for the Proteus simulator.
 * 
 * Loads from HOCON files using typesafe-config.
 * Default values are in reference.conf.
 */
public record SimulatorConfig(
    // Soup configuration
    int soupSize,
    
    // Mutation
    double mutationRate,
    
    // Reaper
    String reaperStrategy,
    int maxErrors,
    
    // Limits
    long maxCycles,           // 0 = infinite
    int maxOrganisms,
    
    // Persistence
    Path dataFile,
    int checkpointInterval,   // cycles between checkpoints, 0 = disabled
    
    // Reporting
    int reportInterval        // cycles between progress reports
) {
    
    /**
     * Load default configuration.
     */
    public static SimulatorConfig defaults() {
        return fromConfig(ConfigFactory.load());
    }
    
    /**
     * Load configuration from a specific file.
     */
    public static SimulatorConfig fromFile(Path configFile) {
        Config fileConfig = ConfigFactory.parseFile(configFile.toFile());
        Config merged = fileConfig.withFallback(ConfigFactory.load());
        return fromConfig(merged);
    }
    
    /**
     * Load from Config object.
     */
    public static SimulatorConfig fromConfig(Config config) {
        Config c = config.getConfig("proteus");
        
        return new SimulatorConfig(
            c.getInt("soup.size"),
            c.getDouble("mutation.rate"),
            c.getString("reaper.strategy"),
            c.getInt("reaper.max-errors"),
            c.getLong("limits.max-cycles"),
            c.getInt("limits.max-organisms"),
            Path.of(c.getString("persistence.file")),
            c.getInt("persistence.checkpoint-interval"),
            c.getInt("reporting.interval")
        );
    }
    
    /**
     * Builder for programmatic configuration.
     */
    public static Builder builder() {
        return new Builder();
    }
    
    public static class Builder {
        private int soupSize = 10_000;
        private double mutationRate = 0.001;
        private String reaperStrategy = "age-based";
        private int maxErrors = 100;
        private long maxCycles = 0;
        private int maxOrganisms = 1000;
        private Path dataFile = Path.of("proteus-run.mv");
        private int checkpointInterval = 10_000;
        private int reportInterval = 1000;
        
        public Builder soupSize(int size) { this.soupSize = size; return this; }
        public Builder mutationRate(double rate) { this.mutationRate = rate; return this; }
        public Builder reaperStrategy(String strategy) { this.reaperStrategy = strategy; return this; }
        public Builder maxErrors(int max) { this.maxErrors = max; return this; }
        public Builder maxCycles(long max) { this.maxCycles = max; return this; }
        public Builder maxOrganisms(int max) { this.maxOrganisms = max; return this; }
        public Builder dataFile(Path file) { this.dataFile = file; return this; }
        public Builder checkpointInterval(int interval) { this.checkpointInterval = interval; return this; }
        public Builder reportInterval(int interval) { this.reportInterval = interval; return this; }
        
        public SimulatorConfig build() {
            return new SimulatorConfig(
                soupSize, mutationRate, reaperStrategy, maxErrors,
                maxCycles, maxOrganisms, dataFile, checkpointInterval, reportInterval
            );
        }
    }
    
    @Override
    public String toString() {
        return String.format("""
            SimulatorConfig:
              soup.size:              %,d
              mutation.rate:          %.4f (%.2f%%)
              reaper.strategy:        %s
              reaper.max-errors:      %d
              limits.max-cycles:      %s
              limits.max-organisms:   %d
              persistence.file:       %s
              persistence.checkpoint: %,d cycles
              reporting.interval:     %,d cycles
            """,
            soupSize,
            mutationRate, mutationRate * 100,
            reaperStrategy,
            maxErrors,
            maxCycles == 0 ? "infinite" : String.format("%,d", maxCycles),
            maxOrganisms,
            dataFile,
            checkpointInterval,
            reportInterval
        );
    }
}

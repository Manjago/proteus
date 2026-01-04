package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.config.SimulatorConfig;
import picocli.CommandLine.Command;

import java.util.concurrent.Callable;

/**
 * Show information about Proteus.
 */
@Command(
    name = "info",
    description = "Show version and configuration info",
    mixinStandardHelpOptions = true
)
public class InfoCommand implements Callable<Integer> {
    
    @Override
    public Integer call() {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║              PROTEUS                  ║");
        System.out.println("║     Artificial Life Simulator         ║");
        System.out.println("║          Version 1.0.0                ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.println();
        
        System.out.println("Default Configuration:");
        System.out.println(SimulatorConfig.defaults());
        
        System.out.println("Quick Start:");
        System.out.println("  # Run simulation with ancestor organism");
        System.out.println("  java -jar proteus.jar run --inject examples/ancestor.asm --cycles 100000");
        System.out.println();
        System.out.println("  # Debug mode (step-by-step)");
        System.out.println("  java -jar proteus.jar debug --inject examples/ancestor.asm --cycles 40");
        System.out.println();
        System.out.println("  # Assemble custom organism");
        System.out.println("  java -jar proteus.jar assemble myorg.asm -d");
        System.out.println();
        System.out.println("Example organisms in examples/:");
        System.out.println("  ancestor.asm  - basic self-replicator");
        System.out.println("  parasite.asm  - uses SEARCH to find and attack");
        System.out.println("  chaotic.asm   - writes randomly with STORE");
        System.out.println();
        
        return 0;
    }
}

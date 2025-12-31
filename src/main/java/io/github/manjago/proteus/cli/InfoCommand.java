package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.Adam;
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
        
        System.out.println("Adam (first organism):");
        System.out.println("  Size: " + Adam.genome().length + " instructions");
        System.out.println();
        System.out.println(Adam.disassemble());
        
        return 0;
    }
}

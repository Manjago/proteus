package io.github.manjago.proteus.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

/**
 * Proteus CLI - Swiss Army Knife for artificial life simulation.
 * 
 * Usage:
 *   proteus run [options]            - Run simulation
 *   proteus debug [options]          - Debug mode with frame recording
 *   proteus checkpoint info <file>   - Show checkpoint info
 *   proteus checkpoint diff <a> <b>  - Compare checkpoints
 *   proteus assemble <file>          - Assemble source to binary
 *   proteus analyze [options]        - Analyze saved run
 *   proteus info                     - Show version and config
 */
@Command(
    name = "proteus",
    description = "Artificial Life Simulation - Tierra-style evolutionary sandbox",
    mixinStandardHelpOptions = true,
    version = "Proteus 1.0.0",
    subcommands = {
        RunCommand.class,
        DebugCommand.class,
        CheckpointCommand.class,
        AssembleCommand.class,
        AnalyzeCommand.class,
        InfoCommand.class,
        CommandLine.HelpCommand.class
    }
)
public class ProteusCli implements Runnable {
    
    @Override
    public void run() {
        // If no subcommand, show help
        CommandLine.usage(this, System.out);
    }
    
    public static void main(String[] args) {
        int exitCode = new CommandLine(new ProteusCli())
                .setCaseInsensitiveEnumValuesAllowed(true)
                .execute(args);
        System.exit(exitCode);
    }
}

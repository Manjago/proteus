package io.github.manjago.proteus.cli;

import io.github.manjago.proteus.persistence.CheckpointStore;
import io.github.manjago.proteus.persistence.CheckpointStore.CheckpointData;
import io.github.manjago.proteus.persistence.CheckpointStore.OrganismData;
import picocli.CommandLine.Command;
import picocli.CommandLine.Parameters;

import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.Callable;

/**
 * CLI command: checkpoint
 * 
 * Work with checkpoint files.
 * 
 * Usage:
 *   proteus checkpoint info state.mv           # Show checkpoint info
 *   proteus checkpoint diff state1.mv state2.mv # Compare two checkpoints
 */
@Command(
    name = "checkpoint",
    description = "Work with checkpoint files",
    mixinStandardHelpOptions = true,
    subcommands = {
        CheckpointCommand.InfoCommand.class,
        CheckpointCommand.DiffCommand.class
    }
)
public class CheckpointCommand implements Callable<Integer> {
    
    @Override
    public Integer call() {
        System.out.println("Use 'checkpoint info' or 'checkpoint diff'");
        System.out.println("Run 'proteus checkpoint --help' for usage");
        return 0;
    }
    
    // ========== Subcommand: info ==========
    
    @Command(
        name = "info",
        description = "Show detailed checkpoint information",
        mixinStandardHelpOptions = true
    )
    public static class InfoCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "Checkpoint file (.mv)")
        private Path checkpointFile;
        
        @Override
        public Integer call() {
            try {
                System.out.println("═".repeat(60));
                System.out.println("CHECKPOINT INFO: " + checkpointFile.getFileName());
                System.out.println("═".repeat(60));
                System.out.println();
                
                CheckpointData data = CheckpointStore.load(checkpointFile);
                
                System.out.println("📋 Metadata:");
                System.out.printf("   Version:      %d%n", data.version());
                System.out.printf("   Cycles:       %,d%n", data.totalCycles());
                System.out.printf("   Seed:         %d%n", data.seed());
                System.out.printf("   Soup size:    %,d cells%n", data.soupSize());
                System.out.println();
                
                System.out.println("👥 Population:");
                System.out.printf("   Organisms:    %d%n", data.organisms().size());
                System.out.printf("   Total spawns: %,d%n", data.totalSpawns());
                System.out.printf("   Deaths (err): %,d%n", data.deathsByErrors());
                System.out.printf("   Deaths (reap):%,d%n", data.deathsByReaper());
                System.out.printf("   Next org ID:  %d%n", data.nextOrgId());
                System.out.printf("   Next alloc ID:%d%n", data.nextAllocId());
                System.out.println();
                
                System.out.println("⚙️  Config:");
                System.out.printf("   Mutation rate: %.4f%n", data.mutationRate());
                System.out.printf("   Max errors:    %d%n", data.maxErrors());
                System.out.printf("   Max organisms: %d%n", data.maxOrganisms());
                System.out.println();
                
                System.out.println("🎲 RNG:");
                if (data.hasDeterministicRng()) {
                    var rng = data.rngState();
                    byte[] rngBytes = rng.toBytes();
                    System.out.printf("   Initial seed: %d%n", rng.initialSeed());
                    System.out.printf("   State size: %d bytes%n", rngBytes.length);
                    System.out.printf("   State hash: %08X%n", java.util.Arrays.hashCode(rngBytes));
                    System.out.println("   ✅ Deterministic resume supported");
                } else {
                    System.out.println("   ❌ No RNG state (non-deterministic)");
                }
                System.out.println();
                
                // Soup statistics
                int nonZeroCells = 0;
                for (int cell : data.soup()) {
                    if (cell != 0) nonZeroCells++;
                }
                System.out.println("💾 Soup:");
                System.out.printf("   Non-zero cells: %,d (%.1f%%)%n", 
                    nonZeroCells, 100.0 * nonZeroCells / data.soupSize());
                System.out.println();
                
                // Organism details
                if (!data.organisms().isEmpty()) {
                    System.out.println("🧬 Organisms:");
                    for (OrganismData org : data.organisms()) {
                        String nameDisplay = org.name != null ? org.name + "#" + org.id : "#" + org.id;
                        System.out.printf("   %s @ [%d..%d) size=%d parent=#%d allocId=%d%n",
                            nameDisplay, org.startAddr, org.startAddr + org.size, 
                            org.size, org.parentId, org.allocId);
                        System.out.printf("      IP=%d errors=%d age=%d%n",
                            org.ip, org.errors, org.age);
                        if (org.hasPending) {
                            System.out.printf("      pending: [%d..%d) allocId=%d%n",
                                org.pendingAddr, org.pendingAddr + org.pendingSize, org.pendingAllocId);
                        }
                    }
                }
                
                return 0;
                
            } catch (Exception e) {
                System.err.println("❌ Error reading checkpoint: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
    }
    
    // ========== Subcommand: diff ==========
    
    @Command(
        name = "diff",
        description = "Compare two checkpoints for determinism verification",
        mixinStandardHelpOptions = true
    )
    public static class DiffCommand implements Callable<Integer> {
        
        @Parameters(index = "0", description = "First checkpoint file")
        private Path file1;
        
        @Parameters(index = "1", description = "Second checkpoint file")
        private Path file2;
        
        @Override
        public Integer call() {
            try {
                System.out.println("═".repeat(60));
                System.out.println("CHECKPOINT DIFF");
                System.out.println("═".repeat(60));
                System.out.printf("File 1: %s%n", file1);
                System.out.printf("File 2: %s%n", file2);
                System.out.println();
                
                CheckpointData d1 = CheckpointStore.load(file1);
                CheckpointData d2 = CheckpointStore.load(file2);
                
                List<String> diffs = new ArrayList<>();
                
                // Compare metadata
                compareField(diffs, "version", d1.version(), d2.version());
                compareField(diffs, "totalCycles", d1.totalCycles(), d2.totalCycles());
                compareField(diffs, "seed", d1.seed(), d2.seed());
                compareField(diffs, "soupSize", d1.soupSize(), d2.soupSize());
                compareField(diffs, "totalSpawns", d1.totalSpawns(), d2.totalSpawns());
                compareField(diffs, "deathsByReaper", d1.deathsByReaper(), d2.deathsByReaper());
                compareField(diffs, "deathsByErrors", d1.deathsByErrors(), d2.deathsByErrors());
                compareField(diffs, "nextOrgId", d1.nextOrgId(), d2.nextOrgId());
                compareField(diffs, "nextAllocId", d1.nextAllocId(), d2.nextAllocId());
                
                // Compare config
                compareField(diffs, "mutationRate", d1.mutationRate(), d2.mutationRate());
                compareField(diffs, "maxErrors", d1.maxErrors(), d2.maxErrors());
                compareField(diffs, "maxOrganisms", d1.maxOrganisms(), d2.maxOrganisms());
                
                // Compare RNG state
                if (d1.hasDeterministicRng() && d2.hasDeterministicRng()) {
                    var rng1 = d1.rngState();
                    var rng2 = d2.rngState();
                    compareField(diffs, "rng.initialSeed", rng1.initialSeed(), rng2.initialSeed());
                    // Compare actual state bytes
                    byte[] bytes1 = rng1.toBytes();
                    byte[] bytes2 = rng2.toBytes();
                    if (!java.util.Arrays.equals(bytes1, bytes2)) {
                        diffs.add("rng.state: bytes differ");
                    }
                } else if (d1.hasDeterministicRng() != d2.hasDeterministicRng()) {
                    diffs.add("rng: one has RNG state, other doesn't");
                }
                
                // Compare soup
                int soupDiffs = compareSoup(d1.soup(), d2.soup());
                if (soupDiffs > 0) {
                    diffs.add(String.format("soup: %d cells differ", soupDiffs));
                }
                
                // Compare organisms
                compareOrganisms(diffs, d1.organisms(), d2.organisms());
                
                // Print results
                System.out.println("📊 COMPARISON RESULT:");
                System.out.println();
                
                if (diffs.isEmpty()) {
                    System.out.println("✅ CHECKPOINTS ARE IDENTICAL!");
                    System.out.println();
                    System.out.println("   Determinism verified:");
                    System.out.printf("   - %,d cycles%n", d1.totalCycles());
                    System.out.printf("   - %d organisms%n", d1.organisms().size());
                    System.out.printf("   - %,d soup cells checked%n", d1.soupSize());
                    if (d1.hasDeterministicRng()) {
                        System.out.printf("   - RNG state hash: %08X%n", 
                            java.util.Arrays.hashCode(d1.rngState().toBytes()));
                    }
                    return 0;
                } else {
                    System.out.println("❌ CHECKPOINTS DIFFER!");
                    System.out.println();
                    System.out.printf("   Found %d difference(s):%n", diffs.size());
                    for (String diff : diffs) {
                        System.out.println("   • " + diff);
                    }
                    return 1;
                }
                
            } catch (Exception e) {
                System.err.println("❌ Error comparing checkpoints: " + e.getMessage());
                e.printStackTrace();
                return 1;
            }
        }
        
        private void compareField(List<String> diffs, String name, long v1, long v2) {
            if (v1 != v2) {
                diffs.add(String.format("%s: %d vs %d", name, v1, v2));
            }
        }
        
        private void compareField(List<String> diffs, String name, double v1, double v2) {
            if (Math.abs(v1 - v2) > 1e-10) {
                diffs.add(String.format("%s: %f vs %f", name, v1, v2));
            }
        }
        
        private int compareSoup(int[] soup1, int[] soup2) {
            int diffs = 0;
            int len = Math.min(soup1.length, soup2.length);
            
            for (int i = 0; i < len; i++) {
                if (soup1[i] != soup2[i]) {
                    diffs++;
                    if (diffs <= 5) {
                        System.out.printf("   soup[%d]: 0x%08X vs 0x%08X%n", i, soup1[i], soup2[i]);
                    }
                }
            }
            
            // Check for size difference
            if (soup1.length != soup2.length) {
                diffs += Math.abs(soup1.length - soup2.length);
            }
            
            return diffs;
        }
        
        private void compareOrganisms(List<String> diffs, List<OrganismData> orgs1, List<OrganismData> orgs2) {
            if (orgs1.size() != orgs2.size()) {
                diffs.add(String.format("organism count: %d vs %d", orgs1.size(), orgs2.size()));
                return;
            }
            
            // Sort by ID for comparison
            Map<Integer, OrganismData> map1 = new HashMap<>();
            Map<Integer, OrganismData> map2 = new HashMap<>();
            for (OrganismData o : orgs1) map1.put(o.id, o);
            for (OrganismData o : orgs2) map2.put(o.id, o);
            
            // Check all organisms from file1
            for (int id : map1.keySet()) {
                OrganismData o1 = map1.get(id);
                OrganismData o2 = map2.get(id);
                
                if (o2 == null) {
                    diffs.add(String.format("organism #%d: exists in file1 but not file2", id));
                    continue;
                }
                
                List<String> orgDiffs = new ArrayList<>();
                if (o1.startAddr != o2.startAddr) orgDiffs.add("startAddr");
                if (o1.size != o2.size) orgDiffs.add("size");
                if (o1.parentId != o2.parentId) orgDiffs.add("parentId");
                if (o1.birthCycle != o2.birthCycle) orgDiffs.add("birthCycle");
                if (o1.allocId != o2.allocId) orgDiffs.add("allocId");
                if (o1.ip != o2.ip) orgDiffs.add("ip");
                if (o1.errors != o2.errors) orgDiffs.add("errors");
                if (o1.age != o2.age) orgDiffs.add("age");
                if (!Arrays.equals(o1.registers, o2.registers)) orgDiffs.add("registers");
                if (o1.hasPending != o2.hasPending) orgDiffs.add("hasPending");
                if (o1.hasPending && o2.hasPending) {
                    if (o1.pendingAddr != o2.pendingAddr) orgDiffs.add("pendingAddr");
                    if (o1.pendingSize != o2.pendingSize) orgDiffs.add("pendingSize");
                    if (o1.pendingAllocId != o2.pendingAllocId) orgDiffs.add("pendingAllocId");
                }
                
                if (!orgDiffs.isEmpty()) {
                    diffs.add(String.format("organism #%d differs in: %s", id, String.join(", ", orgDiffs)));
                }
            }
            
            // Check for organisms in file2 but not in file1
            for (int id : map2.keySet()) {
                if (!map1.containsKey(id)) {
                    diffs.add(String.format("organism #%d: exists in file2 but not file1", id));
                }
            }
        }
    }
}

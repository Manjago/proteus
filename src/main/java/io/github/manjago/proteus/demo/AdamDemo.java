package io.github.manjago.proteus.demo;

import io.github.manjago.proteus.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Demo: Watch Adam replicate!
 * 
 * This is a simple demonstration of the first self-replicating organism.
 * Run this to see Adam copy itself in the "primordial soup".
 */
public class AdamDemo {
    
    private static final int SOUP_SIZE = 10_000;
    private static final int MAX_ORGANISMS = 100;
    private static final int CYCLES_PER_REPORT = 1000;
    
    // The "soup" - shared memory
    private final AtomicIntegerArray soup;
    
    // All living organisms
    private final List<Organism> organisms = new ArrayList<>();
    
    // Memory allocation pointer (simple bump allocator)
    private int nextFreeAddress;
    
    // Statistics
    private long totalCycles = 0;
    private int totalSpawns = 0;
    private int failedAllocations = 0;
    
    // CPU with configurable mutation rate
    private final VirtualCPU cpu;
    private final double mutationRate;
    
    public AdamDemo(double mutationRate) {
        this.soup = new AtomicIntegerArray(SOUP_SIZE);
        this.mutationRate = mutationRate;
        this.cpu = new VirtualCPU(mutationRate, new Random(), createHandler());
    }
    
    /**
     * Simple organism record.
     */
    static class Organism {
        final int id;
        final int startAddr;
        final int size;
        final CpuState state;
        final int parentId;
        final long birthCycle;
        boolean alive = true;
        
        Organism(int id, int startAddr, int size, int parentId, long birthCycle) {
            this.id = id;
            this.startAddr = startAddr;
            this.size = size;
            this.state = new CpuState(startAddr);
            this.parentId = parentId;
            this.birthCycle = birthCycle;
        }
        
        @Override
        public String toString() {
            return String.format("Organism#%d[addr=%d, size=%d, parent=%d]", 
                id, startAddr, size, parentId);
        }
    }
    
    private SystemCallHandler createHandler() {
        return new SystemCallHandler() {
            @Override
            public int allocate(int requestedSize) {
                if (requestedSize <= 0 || requestedSize > 1000) {
                    return -1; // Sanity check
                }
                
                int addr = nextFreeAddress;
                if (addr + requestedSize > SOUP_SIZE) {
                    failedAllocations++;
                    return -1; // Out of memory
                }
                
                nextFreeAddress += requestedSize;
                return addr;
            }
            
            @Override
            public boolean spawn(int address, int size, CpuState parentState) {
                if (address < 0 || address + size > SOUP_SIZE) {
                    return false; // Invalid address
                }
                
                if (organisms.size() >= MAX_ORGANISMS) {
                    return false; // Population limit
                }
                
                // Find parent
                int parentId = -1;
                for (Organism o : organisms) {
                    if (o.state == parentState) {
                        parentId = o.id;
                        break;
                    }
                }
                
                Organism child = new Organism(
                    organisms.size(),
                    address,
                    size,
                    parentId,
                    totalCycles
                );
                organisms.add(child);
                totalSpawns++;
                
                System.out.printf("  🐣 SPAWN! %s (cycle %d)%n", child, totalCycles);
                
                return true;
            }
        };
    }
    
    /**
     * Load Adam into the soup at address 0.
     */
    public void seedAdam() {
        int[] genome = Adam.genome();
        for (int i = 0; i < genome.length; i++) {
            soup.set(i, genome[i]);
        }
        
        Organism adam = new Organism(0, 0, genome.length, -1, 0);
        organisms.add(adam);
        nextFreeAddress = genome.length;
        
        System.out.println("🌱 Adam loaded at address 0");
        System.out.println("   Size: " + genome.length + " instructions");
        System.out.println();
    }
    
    /**
     * Run simulation for specified number of cycles.
     */
    public void run(int maxCycles) {
        System.out.println("▶️  Starting simulation...");
        System.out.println("   Mutation rate: " + (mutationRate * 100) + "%");
        System.out.println("   Max cycles: " + maxCycles);
        System.out.println();
        
        long startTime = System.currentTimeMillis();
        
        for (int cycle = 0; cycle < maxCycles; cycle++) {
            totalCycles++;
            
            // Round-robin: each organism gets one instruction per cycle
            // Use index-based loop to avoid ConcurrentModificationException
            for (int i = 0; i < organisms.size(); i++) {
                Organism org = organisms.get(i);
                if (!org.alive) continue;
                
                ExecutionResult result = cpu.execute(org.state, soup);
                
                // Kill organisms that error too much
                if (org.state.getErrors() > 100) {
                    org.alive = false;
                    System.out.printf("  💀 %s died (too many errors)%n", org);
                }
            }
            
            // Periodic report
            if (totalCycles % CYCLES_PER_REPORT == 0) {
                printProgress();
            }
        }
        
        long elapsed = System.currentTimeMillis() - startTime;
        
        System.out.println();
        System.out.println("⏹️  Simulation complete!");
        printFinalReport(elapsed);
    }
    
    private void printProgress() {
        long alive = organisms.stream().filter(o -> o.alive).count();
        System.out.printf("   Cycle %,d: %d organisms alive, %d total spawns%n",
            totalCycles, alive, totalSpawns);
    }
    
    private void printFinalReport(long elapsedMs) {
        System.out.println();
        System.out.println("═══════════════════════════════════════");
        System.out.println("           SIMULATION REPORT           ");
        System.out.println("═══════════════════════════════════════");
        System.out.printf("Total cycles:        %,d%n", totalCycles);
        System.out.printf("Total spawns:        %d%n", totalSpawns);
        System.out.printf("Failed allocations:  %d%n", failedAllocations);
        System.out.printf("Memory used:         %,d / %,d cells%n", nextFreeAddress, SOUP_SIZE);
        System.out.printf("Time elapsed:        %,d ms%n", elapsedMs);
        System.out.printf("Speed:               %,.0f cycles/sec%n", 
            totalCycles * 1000.0 / Math.max(1, elapsedMs));
        System.out.println();
        
        System.out.println("ORGANISMS:");
        long alive = 0;
        for (Organism org : organisms) {
            String status = org.alive ? "✓" : "✗";
            System.out.printf("  %s #%d: addr=%d, size=%d, parent=%d, age=%d%n",
                status, org.id, org.startAddr, org.size, 
                org.parentId, org.state.getAge());
            if (org.alive) alive++;
        }
        System.out.println();
        System.out.printf("Alive: %d / %d%n", alive, organisms.size());
        
        // Show genome diversity (compare first vs last valid organism)
        if (organisms.size() >= 2) {
            System.out.println();
            System.out.println("GENOME COMPARISON (first vs last valid):");
            Organism first = organisms.get(0);
            Organism last = null;
            
            // Find last organism with valid address
            for (int i = organisms.size() - 1; i >= 0; i--) {
                if (organisms.get(i).startAddr >= 0) {
                    last = organisms.get(i);
                    break;
                }
            }
            
            if (last == null || last == first) {
                System.out.println("  No valid organisms to compare");
            } else {
                int differences = 0;
                int compareLen = Math.min(first.size, last.size);
                for (int i = 0; i < compareLen; i++) {
                    if (soup.get(first.startAddr + i) != soup.get(last.startAddr + i)) {
                        differences++;
                    }
                }
                
                System.out.printf("  Comparing #%d (addr=%d) vs #%d (addr=%d)%n",
                    first.id, first.startAddr, last.id, last.startAddr);
                
                if (first.size != last.size) {
                    System.out.printf("  Size changed: %d → %d%n", first.size, last.size);
                }
                System.out.printf("  Instruction differences: %d / %d%n", differences, compareLen);
                
                if (differences > 0) {
                    System.out.println("  🧬 EVOLUTION DETECTED!");
                }
            }
        }
        
        System.out.println("═══════════════════════════════════════");
    }
    
    /**
     * Dump memory region as disassembly.
     */
    public void dumpMemory(int start, int length) {
        System.out.printf("%nMemory dump [%d - %d]:%n", start, start + length - 1);
        int[] region = new int[length];
        for (int i = 0; i < length; i++) {
            region[i] = soup.get(start + i);
        }
        System.out.println(Disassembler.disassembleWithHex(region, start));
    }
    
    // ═══════════════════════════════════════
    //                  MAIN
    // ═══════════════════════════════════════
    
    public static void main(String[] args) {
        System.out.println();
        System.out.println("╔═══════════════════════════════════════╗");
        System.out.println("║     PROTEUS: Artificial Life Demo     ║");
        System.out.println("║         🧬 Adam Replication 🧬         ║");
        System.out.println("╚═══════════════════════════════════════╝");
        System.out.println();
        
        // Show Adam's genome first
        System.out.println("ADAM'S GENOME:");
        System.out.println(Adam.disassemble());
        System.out.println();
        
        // Run demo with 0.1% mutation rate
        double mutationRate = 0.001;
        int cycles = 50_000;
        
        AdamDemo demo = new AdamDemo(mutationRate);
        demo.seedAdam();
        demo.run(cycles);
        
        // Show final state of Adam and first child
        if (demo.organisms.size() >= 2) {
            System.out.println();
            demo.dumpMemory(0, 24); // Adam
            demo.dumpMemory(demo.organisms.get(1).startAddr, 24); // First child
        }
    }
}

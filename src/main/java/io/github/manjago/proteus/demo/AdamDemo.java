package io.github.manjago.proteus.demo;

import io.github.manjago.proteus.core.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Demo: Watch Adam replicate with proper memory management!
 * 
 * This demonstration shows:
 * - Self-replicating organisms (starting with Adam)
 * - Memory allocation via FreeListMemoryManager
 * - Population control via AgeBasedReaper
 * - Mutations during replication
 * - Evolution over time
 */
public class AdamDemo {
    
    private static final int SOUP_SIZE = 10_000;
    private static final int MAX_ORGANISMS = 100;
    private static final int CYCLES_PER_REPORT = 1000;
    
    // The "soup" - shared memory
    private final AtomicIntegerArray soup;
    
    // Memory management
    private final MemoryManager memoryManager;
    private final Reaper reaper;
    
    // All organisms (alive and dead)
    private final List<Organism> organisms = new ArrayList<>();
    
    // Statistics
    private long totalCycles = 0;
    private int totalSpawns = 0;
    private int failedAllocations = 0;
    private int deathsByErrors = 0;
    
    // CPU with configurable mutation rate
    private final VirtualCPU cpu;
    private final double mutationRate;
    
    public AdamDemo(double mutationRate) {
        this.soup = new AtomicIntegerArray(SOUP_SIZE);
        this.memoryManager = new FreeListMemoryManager(SOUP_SIZE);
        this.reaper = new AgeBasedReaper(memoryManager);
        this.mutationRate = mutationRate;
        this.cpu = new VirtualCPU(mutationRate, new Random(), createHandler());
    }
    
    private SystemCallHandler createHandler() {
        return new SystemCallHandler() {
            @Override
            public int allocate(int requestedSize) {
                if (requestedSize <= 0 || requestedSize > 1000) {
                    return -1; // Sanity check
                }
                
                // Try to allocate
                int addr = memoryManager.allocate(requestedSize);
                
                if (addr == -1) {
                    // No space - try reaping
                    int killed = reaper.reapUntilFree(requestedSize);
                    if (killed > 0) {
                        // Retry allocation
                        addr = memoryManager.allocate(requestedSize);
                    }
                }
                
                if (addr == -1) {
                    failedAllocations++;
                }
                
                return addr;
            }
            
            @Override
            public boolean spawn(int address, int size, CpuState parentState) {
                if (address < 0 || address + size > SOUP_SIZE) {
                    return false; // Invalid address
                }
                
                // Find parent
                int parentId = -1;
                for (Organism o : organisms) {
                    if (o.getState() == parentState) {
                        parentId = o.getId();
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
                reaper.register(child);
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
        
        // Allocate space for Adam
        int addr = memoryManager.allocate(genome.length);
        if (addr != 0) {
            throw new IllegalStateException("Adam should be at address 0, got: " + addr);
        }
        
        // Load genome into soup
        for (int i = 0; i < genome.length; i++) {
            soup.set(i, genome[i]);
        }
        
        Organism adam = new Organism(0, 0, genome.length, -1, 0);
        organisms.add(adam);
        reaper.register(adam);
        
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
                if (!org.isAlive()) continue;
                
                cpu.execute(org.getState(), soup);
                
                // Kill organisms that error too much
                if (org.getState().getErrors() > 100) {
                    killOrganism(org, "too many errors");
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
    
    /**
     * Kill an organism and free its memory.
     */
    private void killOrganism(Organism org, String reason) {
        org.kill();
        reaper.unregister(org);
        memoryManager.free(org.getStartAddr(), org.getSize());
        deathsByErrors++;
        System.out.printf("  💀 %s died (%s)%n", org, reason);
    }
    
    private void printProgress() {
        long alive = organisms.stream().filter(Organism::isAlive).count();
        int reaped = reaper.getReapCount();
        System.out.printf("   Cycle %,d: %d alive, %d spawns, %d reaped, %d errors%n",
            totalCycles, alive, totalSpawns, reaped, deathsByErrors);
    }
    
    private void printFinalReport(long elapsedMs) {
        System.out.println();
        System.out.println("═══════════════════════════════════════");
        System.out.println("           SIMULATION REPORT           ");
        System.out.println("═══════════════════════════════════════");
        System.out.printf("Total cycles:        %,d%n", totalCycles);
        System.out.printf("Total spawns:        %d%n", totalSpawns);
        System.out.printf("Deaths by errors:    %d%n", deathsByErrors);
        System.out.printf("Reaped (by age):     %d%n", reaper.getReapCount());
        System.out.printf("Failed allocations:  %d%n", failedAllocations);
        System.out.println();
        
        // Memory stats
        System.out.println("MEMORY:");
        System.out.printf("  Used:          %,d / %,d cells (%.1f%%)%n", 
            memoryManager.getUsedMemory(), SOUP_SIZE,
            100.0 * memoryManager.getUsedMemory() / SOUP_SIZE);
        System.out.printf("  Free:          %,d cells%n", memoryManager.getFreeMemory());
        System.out.printf("  Largest block: %,d cells%n", memoryManager.getLargestFreeBlock());
        System.out.printf("  Fragmentation: %.1f%%%n", memoryManager.getFragmentation() * 100);
        System.out.printf("  Free blocks:   %d%n", memoryManager.getFreeBlockCount());
        System.out.println();
        
        // Reaper stats
        if (reaper instanceof AgeBasedReaper abr) {
            System.out.println("REAPER:");
            System.out.printf("  Total reaped:  %d%n", abr.getReapCount());
            System.out.printf("  Avg age@death: %.0f cycles%n", abr.getAverageAgeAtDeath());
            System.out.printf("  Queue size:    %d%n", abr.getQueueSize());
            System.out.printf("  Oldest alive:  %d cycles%n", abr.getOldestAge());
            System.out.println();
        }
        
        System.out.printf("Time elapsed:        %,d ms%n", elapsedMs);
        System.out.printf("Speed:               %,.0f cycles/sec%n", 
            totalCycles * 1000.0 / Math.max(1, elapsedMs));
        System.out.println();
        
        // Organism summary
        long alive = organisms.stream().filter(Organism::isAlive).count();
        System.out.printf("ORGANISMS: %d alive / %d total%n", alive, organisms.size());
        
        // Show first 20 and last 10
        int showFirst = Math.min(20, organisms.size());
        for (int i = 0; i < showFirst; i++) {
            printOrganismLine(organisms.get(i));
        }
        
        if (organisms.size() > 30) {
            System.out.println("  ... (" + (organisms.size() - 30) + " more) ...");
        }
        
        if (organisms.size() > 20) {
            int showLast = Math.min(10, organisms.size() - 20);
            for (int i = organisms.size() - showLast; i < organisms.size(); i++) {
                printOrganismLine(organisms.get(i));
            }
        }
        
        // Evolution detection
        printEvolutionAnalysis();
        
        System.out.println("═══════════════════════════════════════");
    }
    
    private void printOrganismLine(Organism org) {
        String status = org.isAlive() ? "✓" : "✗";
        System.out.printf("  %s #%d: addr=%d, size=%d, parent=%d, age=%d%n",
            status, org.getId(), org.getStartAddr(), org.getSize(), 
            org.getParentId(), org.getAge());
    }
    
    private void printEvolutionAnalysis() {
        // Find first and last ALIVE organisms
        Organism first = null;
        Organism last = null;
        
        for (Organism o : organisms) {
            if (o.isAlive()) {
                if (first == null) first = o;
                last = o;
            }
        }
        
        if (first == null || first == last) {
            System.out.println("\nNo evolution analysis available.");
            return;
        }
        
        System.out.println("\nEVOLUTION ANALYSIS:");
        System.out.printf("  Comparing #%d (addr=%d) vs #%d (addr=%d)%n",
            first.getId(), first.getStartAddr(), last.getId(), last.getStartAddr());
        
        if (first.getSize() != last.getSize()) {
            System.out.printf("  Size changed: %d → %d%n", first.getSize(), last.getSize());
        }
        
        int differences = 0;
        int compareLen = Math.min(first.getSize(), last.getSize());
        for (int i = 0; i < compareLen; i++) {
            if (soup.get(first.getStartAddr() + i) != soup.get(last.getStartAddr() + i)) {
                differences++;
            }
        }
        
        System.out.printf("  Instruction differences: %d / %d%n", differences, compareLen);
        
        if (differences > 0) {
            System.out.println("  🧬 EVOLUTION DETECTED!");
        }
        
        // Generation depth
        int maxGen = 0;
        for (Organism o : organisms) {
            int gen = getGeneration(o);
            if (gen > maxGen) maxGen = gen;
        }
        System.out.printf("  Max generation depth: %d%n", maxGen);
    }
    
    private int getGeneration(Organism org) {
        int gen = 0;
        int parentId = org.getParentId();
        while (parentId >= 0 && parentId < organisms.size()) {
            gen++;
            parentId = organisms.get(parentId).getParentId();
        }
        return gen;
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
        System.out.println("║      🧬 With Memory Management 🧬      ║");
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
        
        // Show some memory dumps
        System.out.println();
        demo.dumpMemory(0, 13); // Original Adam location
        
        // Find a living organism to show
        for (Organism org : demo.organisms) {
            if (org.isAlive() && org.getStartAddr() > 0) {
                demo.dumpMemory(org.getStartAddr(), org.getSize());
                break;
            }
        }
    }
}

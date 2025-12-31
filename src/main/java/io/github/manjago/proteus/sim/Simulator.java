package io.github.manjago.proteus.sim;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Main simulation engine for Proteus artificial life.
 * 
 * Manages the "soup" (memory), organisms, CPU execution, and lifecycle events.
 * Can be started, paused, and resumed. Supports graceful shutdown.
 */
public class Simulator {
    
    private static final Logger log = LoggerFactory.getLogger(Simulator.class);
    
    private final SimulatorConfig config;
    
    // The "soup" - shared memory
    private final AtomicIntegerArray soup;
    
    // Core components
    private final MemoryManager memoryManager;
    private final Reaper reaper;
    private final VirtualCPU cpu;
    private final MutationTracker mutationTracker;
    
    // All organisms (alive and dead)
    private final List<Organism> organisms = new ArrayList<>();
    
    // Statistics
    private long totalCycles = 0;
    private int totalSpawns = 0;
    private int failedAllocations = 0;
    private int deathsByErrors = 0;
    
    // Control
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    
    // Event listener
    private SimulatorListener listener = SimulatorListener.NOOP;
    
    public Simulator(SimulatorConfig config) {
        this.config = config;
        this.soup = new AtomicIntegerArray(config.soupSize());
        this.memoryManager = new FreeListMemoryManager(config.soupSize());
        this.reaper = new AgeBasedReaper(memoryManager);
        this.mutationTracker = new MutationTracker();
        this.cpu = new VirtualCPU(config.mutationRate(), new Random(), createHandler());
        this.cpu.setMutationTracker(mutationTracker);
        
        log.info("Simulator created with config:\n{}", config);
    }
    
    /**
     * Set event listener for simulation events.
     */
    public void setListener(SimulatorListener listener) {
        this.listener = listener != null ? listener : SimulatorListener.NOOP;
    }
    
    /**
     * Seed Adam (the first organism) into the soup.
     */
    public void seedAdam() {
        int[] genome = Adam.genome();
        
        int addr = memoryManager.allocate(genome.length);
        if (addr < 0) {
            throw new IllegalStateException("Cannot allocate space for Adam");
        }
        
        // Load genome into soup
        for (int i = 0; i < genome.length; i++) {
            soup.set(addr + i, genome[i]);
        }
        
        Organism adam = new Organism(0, addr, genome.length, -1, 0);
        organisms.add(adam);
        reaper.register(adam);
        
        log.info("Adam seeded at address {} (size: {} instructions)", addr, genome.length);
        listener.onSpawn(adam, null, totalCycles);
    }
    
    /**
     * Run simulation for specified number of cycles.
     * Returns when cycles complete or stop is requested.
     * 
     * @param cycles number of cycles to run (0 = use config.maxCycles)
     */
    public void run(long cycles) {
        long targetCycles = cycles > 0 ? cycles : config.maxCycles();
        boolean infinite = targetCycles == 0;
        
        if (running.getAndSet(true)) {
            log.warn("Simulator already running");
            return;
        }
        
        stopRequested.set(false);
        log.info("Starting simulation{}", infinite ? " (infinite)" : String.format(" for %,d cycles", targetCycles));
        
        long startTime = System.currentTimeMillis();
        long cyclesDone = 0;
        
        try {
            while (!stopRequested.get()) {
                if (!infinite && cyclesDone >= targetCycles) {
                    break;
                }
                
                runCycle();
                cyclesDone++;
                
                // Progress report
                if (totalCycles % config.reportInterval() == 0) {
                    reportProgress();
                }
                
                // Checkpoint
                if (config.checkpointInterval() > 0 && totalCycles % config.checkpointInterval() == 0) {
                    listener.onCheckpoint(totalCycles);
                }
            }
        } finally {
            running.set(false);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("Simulation stopped after {} cycles ({} ms, {} cycles/sec)",
                    cyclesDone, elapsed, cyclesDone * 1000 / Math.max(1, elapsed));
        }
    }
    
    /**
     * Request graceful stop.
     */
    public void stop() {
        log.info("Stop requested");
        stopRequested.set(true);
    }
    
    /**
     * Check if simulation is running.
     */
    public boolean isRunning() {
        return running.get();
    }
    
    /**
     * Run a single simulation cycle.
     */
    private void runCycle() {
        totalCycles++;
        mutationTracker.setCycle(totalCycles);
        
        // Round-robin: each organism gets one instruction
        for (int i = 0; i < organisms.size(); i++) {
            Organism org = organisms.get(i);
            if (!org.isAlive()) continue;
            
            cpu.execute(org.getState(), soup);
            
            // Kill on too many errors
            if (org.getState().getErrors() > config.maxErrors()) {
                killOrganism(org, DeathCause.ERRORS);
            }
        }
    }
    
    private void killOrganism(Organism org, DeathCause cause) {
        org.kill();
        reaper.unregister(org);
        
        // Only free memory if size is valid
        if (org.getSize() > 0) {
            memoryManager.free(org.getStartAddr(), org.getSize());
        }
        deathsByErrors++;
        
        log.debug("Organism {} died: {}", org.getId(), cause);
        listener.onDeath(org, cause, totalCycles);
    }
    
    private SystemCallHandler createHandler() {
        return new SystemCallHandler() {
            @Override
            public int allocate(int requestedSize) {
                if (requestedSize <= 0 || requestedSize > 1000) {
                    return -1;
                }
                
                int addr = memoryManager.allocate(requestedSize);
                
                if (addr == -1) {
                    int killed = reaper.reapUntilFree(requestedSize);
                    if (killed > 0) {
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
                // Validate size (must be positive and reasonable)
                if (size <= 0 || size > 1000) {
                    return false;
                }
                // Validate address
                if (address < 0 || address + size > config.soupSize()) {
                    return false;
                }
                
                // Find parent
                Organism parent = null;
                for (Organism o : organisms) {
                    if (o.getState() == parentState) {
                        parent = o;
                        break;
                    }
                }
                
                int parentId = parent != null ? parent.getId() : -1;
                
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
                
                log.debug("Spawn: {} (parent: {})", child.getId(), parentId);
                listener.onSpawn(child, parent, totalCycles);
                
                return true;
            }
        };
    }
    
    private void reportProgress() {
        long alive = organisms.stream().filter(Organism::isAlive).count();
        int reaped = reaper.getReapCount();
        
        log.info("Cycle {}: {} alive, {} spawns, {} reaped, {} errors",
                totalCycles, alive, totalSpawns, reaped, deathsByErrors);
        
        listener.onProgress(getStats());
    }
    
    // ========== Getters ==========
    
    public SimulatorConfig getConfig() { return config; }
    public long getTotalCycles() { return totalCycles; }
    public int getTotalSpawns() { return totalSpawns; }
    public int getDeathsByErrors() { return deathsByErrors; }
    public int getFailedAllocations() { return failedAllocations; }
    public List<Organism> getOrganisms() { return new ArrayList<>(organisms); }
    public MemoryManager getMemoryManager() { return memoryManager; }
    public Reaper getReaper() { return reaper; }
    public MutationTracker getMutationTracker() { return mutationTracker; }
    public AtomicIntegerArray getSoup() { return soup; }
    
    /**
     * Get current simulation statistics.
     */
    public SimulatorStats getStats() {
        long alive = organisms.stream().filter(Organism::isAlive).count();
        
        return new SimulatorStats(
            totalCycles,
            totalSpawns,
            deathsByErrors,
            reaper.getReapCount(),
            failedAllocations,
            alive,
            organisms.size(),
            memoryManager.getUsedMemory(),
            memoryManager.getFreeMemory(),
            memoryManager.getLargestFreeBlock(),
            memoryManager.getFragmentation(),
            mutationTracker.size()
        );
    }
}

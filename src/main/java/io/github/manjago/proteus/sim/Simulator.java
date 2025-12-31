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
    private final Defragmenter defragmenter;
    
    // All organisms (alive and dead) - for history/genealogy
    private final List<Organism> organisms = new ArrayList<>();
    
    // Only alive organisms - for fast iteration
    private final List<Organism> aliveOrganisms = new ArrayList<>();
    
    // Statistics
    private long totalCycles = 0;
    private int totalSpawns = 0;
    private int failedAllocations = 0;
    private int deathsByErrors = 0;
    private int aliveCount = 0;  // Track live organisms for O(1) check
    private int maxAlive = 0;    // Peak population
    private int defragmentations = 0;
    private int rejectedSpawns = 0;  // Spawns rejected due to invalid params
    
    // Control
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicBoolean stopRequested = new AtomicBoolean(false);
    
    // Event listener
    private SimulatorListener listener = SimulatorListener.NOOP;
    
    // Random seed used (for reproducibility logging)
    private final long actualSeed;
    
    public Simulator(SimulatorConfig config) {
        this.config = config;
        this.actualSeed = config.effectiveSeed();
        this.soup = new AtomicIntegerArray(config.soupSize());
        this.memoryManager = new FreeListMemoryManager(config.soupSize());
        this.reaper = new AgeBasedReaper(memoryManager);
        this.mutationTracker = new MutationTracker();
        this.defragmenter = new Defragmenter(soup, memoryManager);
        this.cpu = new VirtualCPU(config.mutationRate(), new Random(actualSeed), createHandler());
        this.cpu.setMutationTracker(mutationTracker);
        
        log.info("Simulator created (seed: {})", actualSeed);
    }
    
    /**
     * Get the actual random seed used (useful for reproducing runs).
     */
    public long getActualSeed() {
        return actualSeed;
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
        aliveOrganisms.add(adam);
        reaper.register(adam);
        aliveCount++;
        maxAlive = Math.max(maxAlive, aliveCount);
        
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
        
        // Fast path: skip if no organisms alive
        if (aliveCount == 0) {
            return;
        }
        
        // Collect organisms to kill (can't modify list during iteration)
        List<Organism> toKill = null;
        
        // Round-robin: each alive organism gets one instruction
        for (int i = 0; i < aliveOrganisms.size(); i++) {
            Organism org = aliveOrganisms.get(i);
            
            // Double-check (shouldn't happen, but defensive)
            if (!org.isAlive()) continue;
            
            cpu.execute(org.getState(), soup);
            
            // Mark for death on too many errors
            if (org.getState().getErrors() > config.maxErrors()) {
                if (toKill == null) toKill = new ArrayList<>();
                toKill.add(org);
            }
        }
        
        // Kill marked organisms after iteration
        if (toKill != null) {
            for (Organism org : toKill) {
                killOrganism(org, DeathCause.ERRORS);
            }
        }
    }
    
    private void killOrganism(Organism org, DeathCause cause) {
        // Guard against double-kill
        if (!org.isAlive()) {
            return;
        }
        
        org.kill();
        aliveCount--;
        aliveOrganisms.remove(org);  // O(n) but infrequent
        reaper.unregister(org);
        
        // Free pending allocation (memory allocated for child but not spawned)
        CpuState state = org.getState();
        if (state.hasPendingAllocation()) {
            memoryManager.free(state.getPendingAllocAddr(), state.getPendingAllocSize());
            state.clearPendingAllocation();
        }
        
        // Only free memory if size is valid
        if (org.getSize() > 0) {
            memoryManager.free(org.getStartAddr(), org.getSize());
        }
        
        // Track death cause correctly
        if (cause == DeathCause.ERRORS) {
            deathsByErrors++;
        }
        // Note: REAPED deaths are counted by reaper itself
        
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
                
                // Log large allocation requests (might indicate mutated SIZE)
                if (requestedSize > 50) {
                    log.debug("Large allocation request: size={}, largest={}", 
                            requestedSize, memoryManager.getLargestFreeBlock());
                }
                
                // First attempt: try reaping old organisms
                if (addr == -1) {
                    log.debug("Allocation failed for size {}, trying reaper (frag={}%, largest={})",
                            requestedSize, 
                            String.format("%.1f", memoryManager.getFragmentation() * 100), 
                            memoryManager.getLargestFreeBlock());
                    
                    int killed = reaper.reapUntilFree(requestedSize);
                    if (killed > 0) {
                        // Sync aliveOrganisms with actual state
                        aliveOrganisms.removeIf(o -> !o.isAlive());
                        aliveCount = aliveOrganisms.size();
                        addr = memoryManager.allocate(requestedSize);
                    }
                }
                
                // Second attempt: defragment if fragmented
                if (addr == -1) {
                    log.debug("After reaper still failed, checking defrag: frag={}%, largest={}, needed={}",
                            String.format("%.1f", memoryManager.getFragmentation() * 100), 
                            memoryManager.getLargestFreeBlock(), requestedSize);
                    
                    if (defragmenter.needsDefragmentation(requestedSize, 0.5)) {
                        log.info("Triggering defragmentation: {} alive organisms", aliveOrganisms.size());
                        int moved = defragmenter.defragment(aliveOrganisms);
                        if (moved > 0) {
                            defragmentations++;
                            addr = memoryManager.allocate(requestedSize);
                        }
                    }
                }
                
                if (addr == -1) {
                    failedAllocations++;
                    log.debug("Allocation ultimately failed: frag={}%, largest={}, free={}",
                            String.format("%.1f", memoryManager.getFragmentation() * 100),
                            memoryManager.getLargestFreeBlock(),
                            memoryManager.getFreeMemory());
                }
                
                return addr;
            }
            
            @Override
            public boolean spawn(int address, int size, CpuState parentState) {
                // Get pending allocation info for proper cleanup
                int pendingAddr = parentState.getPendingAllocAddr();
                int pendingSize = parentState.getPendingAllocSize();
                
                // Validate size (must be positive and reasonable)
                if (size <= 0 || size > 1000) {
                    rejectedSpawns++;
                    // Free pending allocation if valid
                    if (pendingAddr >= 0 && pendingSize > 0) {
                        memoryManager.free(pendingAddr, pendingSize);
                    }
                    return false;
                }
                // Validate address (must be valid, not -1 from failed allocation)
                if (address < 0) {
                    rejectedSpawns++;
                    // No memory to free - allocation failed
                    return false;
                }
                if (address + size > config.soupSize()) {
                    rejectedSpawns++;
                    // Free pending allocation if valid
                    if (pendingAddr >= 0 && pendingSize > 0) {
                        memoryManager.free(pendingAddr, pendingSize);
                    }
                    return false;
                }
                // Check population limit
                if (aliveCount >= config.maxOrganisms()) {
                    // Too many organisms - trigger reaper to make room
                    int killed = reaper.reap() != null ? 1 : 0;
                    if (killed > 0) {
                        aliveOrganisms.removeIf(o -> !o.isAlive());
                        aliveCount = aliveOrganisms.size();
                    }
                    if (aliveCount >= config.maxOrganisms()) {
                        rejectedSpawns++;
                        // Free pending allocation
                        if (pendingAddr >= 0 && pendingSize > 0) {
                            memoryManager.free(pendingAddr, pendingSize);
                        }
                        return false; // Still full, reject spawn
                    }
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
                aliveOrganisms.add(child);
                reaper.register(child);
                aliveCount++;
                maxAlive = Math.max(maxAlive, aliveCount);
                totalSpawns++;
                
                log.debug("Spawn: {} (parent: {})", child.getId(), parentId);
                listener.onSpawn(child, parent, totalCycles);
                
                return true;
            }
        };
    }
    
    private void reportProgress() {
        int reaped = reaper.getReapCount();
        
        log.info("Cycle {}: {} alive, {} spawns, {} reaped, {} errors",
                totalCycles, aliveCount, totalSpawns, reaped, deathsByErrors);
        
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
        return new SimulatorStats(
            totalCycles,
            totalSpawns,
            rejectedSpawns,
            deathsByErrors,
            reaper.getReapCount(),
            failedAllocations,
            aliveCount,  // O(1) instead of O(n) stream
            maxAlive,
            organisms.size(),
            memoryManager.getUsedMemory(),
            memoryManager.getFreeMemory(),
            memoryManager.getLargestFreeBlock(),
            memoryManager.getFragmentation(),
            mutationTracker.size(),
            defragmentations
        );
    }
}

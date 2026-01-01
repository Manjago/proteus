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
    private final BitmapMemoryManager memoryManager;
    private final Reaper reaper;
    private final VirtualCPU cpu;
    private final MutationTracker mutationTracker;
    private final Defragmenter defragmenter;
    
    // Only alive organisms - for fast iteration
    private final List<Organism> aliveOrganisms = new ArrayList<>();
    
    // Statistics
    private long totalCycles = 0;
    private int totalSpawns = 0;
    private int totalOrganismsCreated = 0;  // Counter instead of list (memory optimization)
    private int failedAllocations = 0;
    private int deathsByErrors = 0;
    private int aliveCount = 0;  // Track live organisms for O(1) check
    private int maxAlive = 0;    // Peak population
    private int defragmentations = 0;
    private int rejectedSpawns = 0;  // Spawns rejected due to invalid params
    private int overlapWarnings = 0;  // Counter for overlap detection (debug)
    
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
        this.memoryManager = new BitmapMemoryManager(config.soupSize());
        this.reaper = new AgeBasedReaper(memoryManager);
        this.mutationTracker = new MutationTracker();
        this.defragmenter = new Defragmenter(soup, memoryManager);
        this.cpu = new VirtualCPU(config.mutationRate(), new Random(actualSeed), createHandler());
        this.cpu.setMutationTracker(mutationTracker);
        
        log.info("Simulator created with BitmapMemoryManager (seed: {})", actualSeed);
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
        totalOrganismsCreated++;
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
                
                // Periodic reaper queue cleanup (every 10,000 cycles)
                // Prevents OOM from lazy deletion accumulation
                if (totalCycles % 10_000 == 0) {
                    int rawSize = reaper.getRawQueueSize();
                    // Cleanup when dead organisms exceed 2x alive count
                    if (rawSize > aliveCount * 2 + 10_000) {
                        reaper.cleanup();
                    }
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
        // Use freeIfOwned to avoid freeing another organism's memory
        CpuState state = org.getState();
        if (state.hasPendingAllocation()) {
            memoryManager.freeIfOwned(state.getPendingAllocAddr(), state.getPendingAllocSize());
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
    
    /**
     * Clear all pending allocations from alive organisms.
     * Called before defragmentation.
     * 
     * We FREE the memory first, then clear the state. This way:
     * - If defrag succeeds: rebuild() will overwrite free list anyway
     * - If defrag aborts: free list correctly reflects freed memory
     * 
     * @return number of pending allocations cleared
     */
    private int clearAllPendingAllocations() {
        int cleared = 0;
        for (Organism org : aliveOrganisms) {
            CpuState state = org.getState();
            if (state.hasPendingAllocation()) {
                // Use freeIfOwned - pending may have been taken by another organism
                memoryManager.freeIfOwned(state.getPendingAllocAddr(), state.getPendingAllocSize());
                state.clearPendingAllocation();
                cleared++;
            }
        }
        if (cleared > 0) {
            log.debug("Cleared {} pending allocations before defrag", cleared);
        }
        return cleared;
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
                        
                        // CRITICAL: Clear all pending allocations before defrag!
                        // rebuild() will reset the free list, making pending addresses invalid.
                        // If we don't clear them, organisms will double-free on death.
                        // We free() before clear() so if defrag aborts, memory is still correct.
                        clearAllPendingAllocations();
                        
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
                
                // If no valid pending allocation, spawn fails
                // (ALLOCATE must succeed before SPAWN can work)
                if (pendingAddr < 0 || pendingSize <= 0) {
                    rejectedSpawns++;
                    return false;
                }
                
                // Validate that spawn parameters match pending allocation
                // This prevents memory corruption from mutated registers
                if (address != pendingAddr) {
                    rejectedSpawns++;
                    // Use freeIfOwned to avoid freeing another organism's memory!
                    if (!memoryManager.freeIfOwned(pendingAddr, pendingSize)) {
                        log.warn("Spawn rejected: address mismatch, but pending memory already taken! " +
                                "(spawn={}, pending={})", address, pendingAddr);
                    }
                    log.debug("Spawn rejected: address mismatch (spawn={}, pending={})", address, pendingAddr);
                    return false;
                }
                
                // Use pendingSize for the organism, not the potentially mutated size
                // This ensures we track the actual allocated memory
                int actualSize = pendingSize;
                
                // Validate size is reasonable
                if (actualSize <= 0 || actualSize > 1000) {
                    rejectedSpawns++;
                    memoryManager.freeIfOwned(pendingAddr, pendingSize);
                    return false;
                }
                
                // Check bounds
                if (address + actualSize > config.soupSize()) {
                    rejectedSpawns++;
                    memoryManager.freeIfOwned(pendingAddr, pendingSize);
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
                        memoryManager.freeIfOwned(pendingAddr, pendingSize);
                        return false;
                    }
                }
                
                // Find parent (search only alive organisms - parent must be alive)
                Organism parent = null;
                for (Organism o : aliveOrganisms) {
                    if (o.getState() == parentState) {
                        parent = o;
                        break;
                    }
                }
                
                int parentId = parent != null ? parent.getId() : -1;
                
                // DEBUG: Check for overlaps BEFORE creating child (expensive - only first 10)
                if (overlapWarnings < 10) {
                    int childStart = address;
                    int childEnd = address + actualSize;
                    for (Organism existing : aliveOrganisms) {
                        int existingEnd = existing.getStartAddr() + existing.getSize();
                        if (childStart < existingEnd && childEnd > existing.getStartAddr()) {
                            // Overlap detected!
                            log.warn("OVERLAP at spawn! New child [{},{}) overlaps with Org#{} [{},{}) - isAlive={}",
                                    childStart, childEnd, 
                                    existing.getId(), existing.getStartAddr(), existingEnd,
                                    existing.isAlive());
                            overlapWarnings++;
                            if (overlapWarnings >= 10) {
                                log.warn("Further overlap warnings suppressed");
                                break;
                            }
                        }
                    }
                }
                
                // Use actualSize (from pending allocation) not size (potentially mutated)
                Organism child = new Organism(
                    totalOrganismsCreated,
                    address,
                    actualSize,
                    parentId,
                    totalCycles
                );
                totalOrganismsCreated++;
                aliveOrganisms.add(child);
                reaper.register(child);
                aliveCount++;
                maxAlive = Math.max(maxAlive, aliveCount);
                totalSpawns++;
                
                log.debug("Spawn: {} (parent: {}, size: {})", child.getId(), parentId, actualSize);
                listener.onSpawn(child, parent, totalCycles);
                
                return true;
            }
            
            @Override
            public void freePending(int address, int size) {
                if (address >= 0 && size > 0 && address + size <= config.soupSize()) {
                    // Use freeIfOwned to avoid freeing another organism's memory
                    if (memoryManager.freeIfOwned(address, size)) {
                        log.debug("Freed pending allocation: {} cells at addr {}", size, address);
                    } else {
                        log.debug("Pending at {} already taken by another organism", address);
                    }
                }
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
    public List<Organism> getAliveOrganisms() { return new ArrayList<>(aliveOrganisms); }
    public int getTotalOrganismsCreated() { return totalOrganismsCreated; }
    public MemoryManager getMemoryManager() { return memoryManager; }
    public Reaper getReaper() { return reaper; }
    public MutationTracker getMutationTracker() { return mutationTracker; }
    public AtomicIntegerArray getSoup() { return soup; }
    
    /**
     * Calculate expected memory usage (for leak detection).
     * Sum of: alive organisms' sizes + pending allocations
     */
    public int getExpectedMemoryUsed() {
        int expected = 0;
        for (Organism org : aliveOrganisms) {
            expected += org.getSize();
            CpuState state = org.getState();
            if (state.hasPendingAllocation()) {
                expected += state.getPendingAllocSize();
            }
        }
        return expected;
    }
    
    /**
     * Detect memory leak (difference between actual and expected usage).
     */
    public int getMemoryLeak() {
        return memoryManager.getUsedMemory() - getExpectedMemoryUsed();
    }
    
    /**
     * Detailed memory diagnostics for debugging leaks.
     */
    public String getMemoryDiagnostics() {
        int actual = memoryManager.getUsedMemory();
        int expected = getExpectedMemoryUsed();
        int leak = actual - expected;
        
        if (leak == 0) {
            return "Memory OK: " + actual + " cells";
        }
        
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("Memory mismatch: actual=%d, expected=%d, diff=%d%n", actual, expected, leak));
        
        // Count components of expected
        int orgSizes = 0;
        int pendingCount = 0;
        int pendingTotal = 0;
        
        // Detailed org analysis
        int orgsWithMismatch = 0;
        int totalMismatch = 0;
        int orgsPartiallyFree = 0;
        
        // Collect problematic orgs for detailed report
        List<String> problemOrgs = new ArrayList<>();
        
        for (Organism org : aliveOrganisms) {
            orgSizes += org.getSize();
            if (org.getState().hasPendingAllocation()) {
                pendingCount++;
                pendingTotal += org.getState().getPendingAllocSize();
            }
            
            // Check if org's memory is actually marked as used
            int actualUsed = memoryManager.countUsedInRange(org.getStartAddr(), org.getSize());
            if (actualUsed != org.getSize()) {
                orgsWithMismatch++;
                totalMismatch += (org.getSize() - actualUsed);
                if (actualUsed > 0 && actualUsed < org.getSize()) {
                    orgsPartiallyFree++;
                    if (problemOrgs.size() < 5) {
                        problemOrgs.add(String.format("Org#%d addr=%d size=%d actualUsed=%d",
                                org.getId(), org.getStartAddr(), org.getSize(), actualUsed));
                    }
                }
            }
        }
        
        sb.append(String.format("  Alive orgs: %d, total size: %d%n", aliveOrganisms.size(), orgSizes));
        sb.append(String.format("  Pending allocations: %d, total: %d%n", pendingCount, pendingTotal));
        sb.append(String.format("  Expected = orgSizes + pending = %d + %d = %d%n", orgSizes, pendingTotal, expected));
        
        if (orgsWithMismatch > 0) {
            sb.append(String.format("  ⚠️  Orgs with memory mismatch: %d (total diff: %d cells)%n", 
                    orgsWithMismatch, totalMismatch));
            sb.append(String.format("  ⚠️  Orgs partially free: %d%n", orgsPartiallyFree));
            for (String prob : problemOrgs) {
                sb.append(String.format("      %s%n", prob));
            }
        }
        
        // Check pending allocations consistency
        int pendingMismatch = 0;
        for (Organism org : aliveOrganisms) {
            CpuState state = org.getState();
            if (state.hasPendingAllocation()) {
                int pendAddr = state.getPendingAllocAddr();
                int pendSize = state.getPendingAllocSize();
                int actualPending = memoryManager.countUsedInRange(pendAddr, pendSize);
                if (actualPending != pendSize) {
                    pendingMismatch += (pendSize - actualPending);
                }
            }
        }
        
        if (pendingMismatch > 0) {
            sb.append(String.format("  ⚠️  Pending allocation mismatch: %d cells%n", pendingMismatch));
        }
        
        // Check for OVERLAPS between organisms
        int overlaps = 0;
        List<Organism> sortedOrgs = new ArrayList<>(aliveOrganisms);
        sortedOrgs.sort((a, b) -> Integer.compare(a.getStartAddr(), b.getStartAddr()));
        for (int i = 0; i < sortedOrgs.size() - 1; i++) {
            Organism curr = sortedOrgs.get(i);
            Organism next = sortedOrgs.get(i + 1);
            int currEnd = curr.getStartAddr() + curr.getSize();
            if (currEnd > next.getStartAddr()) {
                overlaps++;
                if (overlaps <= 3) {
                    sb.append(String.format("  ⚠️  OVERLAP: Org#%d [%d,%d) vs Org#%d [%d,%d)%n",
                            curr.getId(), curr.getStartAddr(), currEnd,
                            next.getId(), next.getStartAddr(), next.getStartAddr() + next.getSize()));
                }
            }
        }
        if (overlaps > 3) {
            sb.append(String.format("  ⚠️  ... and %d more overlaps%n", overlaps - 3));
        }
        if (overlaps > 0) {
            sb.append(String.format("  ⚠️  Total organism overlaps: %d%n", overlaps));
        }
        
        // Check for overlaps between orgs and pending
        int orgPendingOverlaps = 0;
        for (Organism org : aliveOrganisms) {
            CpuState state = org.getState();
            if (state.hasPendingAllocation()) {
                int pendAddr = state.getPendingAllocAddr();
                int pendSize = state.getPendingAllocSize();
                int pendEnd = pendAddr + pendSize;
                
                // Check against all other orgs
                for (Organism other : aliveOrganisms) {
                    if (other == org) continue;
                    int otherEnd = other.getStartAddr() + other.getSize();
                    // Check overlap
                    if (pendAddr < otherEnd && pendEnd > other.getStartAddr()) {
                        orgPendingOverlaps++;
                    }
                }
            }
        }
        if (orgPendingOverlaps > 0) {
            sb.append(String.format("  ⚠️  Org-Pending overlaps: %d%n", orgPendingOverlaps));
        }
        
        return sb.toString();
    }
    
    /**
     * Get current simulation statistics.
     */
    public SimulatorStats getStats() {
        Runtime runtime = Runtime.getRuntime();
        long heapUsed = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
        long heapMax = runtime.maxMemory() / (1024 * 1024);
        
        return new SimulatorStats(
            totalCycles,
            totalSpawns,
            rejectedSpawns,
            deathsByErrors,
            reaper.getReapCount(),
            failedAllocations,
            aliveCount,  // O(1) instead of O(n) stream
            maxAlive,
            totalOrganismsCreated,
            memoryManager.getUsedMemory(),
            memoryManager.getFreeMemory(),
            getMemoryLeak(),
            memoryManager.getLargestFreeBlock(),
            memoryManager.getFragmentation(),
            mutationTracker.size(),
            defragmentations,
            reaper.getRawQueueSize(),
            heapUsed,
            heapMax
        );
    }
}

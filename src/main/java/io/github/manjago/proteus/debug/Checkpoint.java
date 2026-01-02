package io.github.manjago.proteus.debug;

import io.github.manjago.proteus.core.CpuState;
import io.github.manjago.proteus.core.Organism;
import io.github.manjago.proteus.sim.Simulator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Checkpoint: save and load simulation state.
 * 
 * Format:
 * - Header: magic, version, metadata
 * - Soup: raw int[] (only non-zero regions)
 * - Organisms: list of serialized organisms
 * - Simulator state: cycle count, statistics
 */
public class Checkpoint {
    
    private static final Logger log = LoggerFactory.getLogger(Checkpoint.class);
    
    private static final int MAGIC = 0x50524F54;  // "PROT"
    private static final int VERSION = 1;
    
    /**
     * Save checkpoint to file.
     */
    public static void save(Simulator sim, Path path) throws IOException {
        log.info("Saving checkpoint to {}", path);
        
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            
            // Header
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeLong(sim.getTotalCycles());
            out.writeLong(sim.getActualSeed());
            
            // Soup size
            int soupSize = sim.getMemoryManager().getSoupSize();
            out.writeInt(soupSize);
            
            // Find and write non-zero regions
            List<int[]> regions = new ArrayList<>();
            List<Integer> regionStarts = new ArrayList<>();
            
            int regionStart = -1;
            List<Integer> regionData = new ArrayList<>();
            
            for (int i = 0; i < soupSize; i++) {
                int value = sim.getMemoryManager().read(i);
                if (value != 0) {
                    if (regionStart == -1) {
                        regionStart = i;
                    }
                    regionData.add(value);
                } else if (regionStart != -1) {
                    regionStarts.add(regionStart);
                    regions.add(toIntArray(regionData));
                    regionStart = -1;
                    regionData.clear();
                }
            }
            if (regionStart != -1) {
                regionStarts.add(regionStart);
                regions.add(toIntArray(regionData));
            }
            
            // Write regions
            out.writeInt(regions.size());
            for (int i = 0; i < regions.size(); i++) {
                out.writeInt(regionStarts.get(i));
                int[] data = regions.get(i);
                out.writeInt(data.length);
                for (int v : data) {
                    out.writeInt(v);
                }
            }
            
            // Write organisms
            List<Organism> organisms = sim.getAliveOrganisms();
            out.writeInt(organisms.size());
            
            for (Organism org : organisms) {
                out.writeInt(org.getId());
                out.writeInt(org.getStartAddr());
                out.writeInt(org.getSize());
                out.writeInt(org.getParentId());
                out.writeLong(org.getBirthCycle());
                out.writeInt(org.getAllocId());
                
                CpuState state = org.getState();
                out.writeInt(state.getIP());
                out.writeInt(state.getErrors());
                out.writeLong(state.getAge());
                
                int[] regs = state.getRegisters();
                for (int r : regs) {
                    out.writeInt(r);
                }
                
                out.writeBoolean(state.hasPendingAllocation());
                if (state.hasPendingAllocation()) {
                    out.writeInt(state.getPendingAllocAddr());
                    out.writeInt(state.getPendingAllocSize());
                    out.writeInt(state.getPendingAllocId());
                }
            }
            
            // Statistics (for info, not restored)
            out.writeInt(sim.getTotalSpawns());
            out.writeInt(sim.getReaper().getReapCount());
            out.writeInt(sim.getDeathsByErrors());
        }
        
        log.info("Checkpoint saved: {} cycles, {} organisms", 
            sim.getTotalCycles(), sim.getAliveOrganisms().size());
    }
    
    /**
     * Load checkpoint data from file.
     * Returns data that can be used to restore simulation state.
     */
    public static CheckpointData load(Path path) throws IOException {
        log.info("Loading checkpoint from {}", path);
        
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            
            // Header
            int magic = in.readInt();
            if (magic != MAGIC) {
                throw new IOException("Invalid checkpoint file (bad magic)");
            }
            
            int version = in.readInt();
            if (version != VERSION) {
                throw new IOException("Unsupported checkpoint version: " + version);
            }
            
            long totalCycles = in.readLong();
            long seed = in.readLong();
            int soupSize = in.readInt();
            
            // Read soup regions
            int[] soup = new int[soupSize];
            int regionCount = in.readInt();
            
            for (int r = 0; r < regionCount; r++) {
                int start = in.readInt();
                int length = in.readInt();
                for (int i = 0; i < length; i++) {
                    soup[start + i] = in.readInt();
                }
            }
            
            // Read organisms
            int orgCount = in.readInt();
            List<OrganismData> organisms = new ArrayList<>();
            
            for (int o = 0; o < orgCount; o++) {
                OrganismData od = new OrganismData();
                od.id = in.readInt();
                od.startAddr = in.readInt();
                od.size = in.readInt();
                od.parentId = in.readInt();
                od.birthCycle = in.readLong();
                od.allocId = in.readInt();
                od.ip = in.readInt();
                od.errors = in.readInt();
                od.age = in.readLong();
                od.registers = new int[8];
                for (int i = 0; i < 8; i++) {
                    od.registers[i] = in.readInt();
                }
                od.hasPending = in.readBoolean();
                if (od.hasPending) {
                    od.pendingAddr = in.readInt();
                    od.pendingSize = in.readInt();
                    od.pendingAllocId = in.readInt();
                }
                organisms.add(od);
            }
            
            // Statistics
            int totalSpawns = in.readInt();
            int deathsByReaper = in.readInt();
            int deathsByErrors = in.readInt();
            
            log.info("Checkpoint loaded: {} cycles, {} organisms", totalCycles, organisms.size());
            
            return new CheckpointData(
                totalCycles, seed, soupSize, soup, organisms,
                totalSpawns, deathsByReaper, deathsByErrors
            );
        }
    }
    
    /**
     * Save just the soup to a file (binary format).
     */
    public static void saveSoup(Simulator sim, Path path) throws IOException {
        int soupSize = sim.getMemoryManager().getSoupSize();
        
        try (DataOutputStream out = new DataOutputStream(
                new BufferedOutputStream(Files.newOutputStream(path)))) {
            out.writeInt(soupSize);
            for (int i = 0; i < soupSize; i++) {
                out.writeInt(sim.getMemoryManager().read(i));
            }
        }
        
        log.info("Soup saved: {} cells", soupSize);
    }
    
    /**
     * Load soup from file.
     */
    public static int[] loadSoup(Path path) throws IOException {
        try (DataInputStream in = new DataInputStream(
                new BufferedInputStream(Files.newInputStream(path)))) {
            int size = in.readInt();
            int[] soup = new int[size];
            for (int i = 0; i < size; i++) {
                soup[i] = in.readInt();
            }
            log.info("Soup loaded: {} cells", size);
            return soup;
        }
    }
    
    // ========== Data classes ==========
    
    /**
     * Loaded checkpoint data.
     */
    public record CheckpointData(
        long totalCycles,
        long seed,
        int soupSize,
        int[] soup,
        List<OrganismData> organisms,
        int totalSpawns,
        int deathsByReaper,
        int deathsByErrors
    ) {}
    
    /**
     * Organism data from checkpoint.
     */
    public static class OrganismData {
        public int id;
        public int startAddr;
        public int size;
        public int parentId;
        public long birthCycle;
        public int allocId;
        public int ip;
        public int errors;
        public long age;
        public int[] registers;
        public boolean hasPending;
        public int pendingAddr;
        public int pendingSize;
        public int pendingAllocId;
    }
    
    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
}

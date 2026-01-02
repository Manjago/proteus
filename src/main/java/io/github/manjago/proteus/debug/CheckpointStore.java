package io.github.manjago.proteus.debug;

import io.github.manjago.proteus.core.CpuState;
import io.github.manjago.proteus.core.GameRng;
import io.github.manjago.proteus.core.Organism;
import io.github.manjago.proteus.sim.Simulator;
import org.h2.mvstore.MVMap;
import org.h2.mvstore.MVStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Checkpoint storage using H2 MVStore.
 * 
 * MVStore provides:
 * - ACID transactions
 * - Automatic corruption recovery
 * - Compact binary format
 * - Easy key-value storage
 * 
 * Structure:
 * - "meta" map: metadata (version, cycles, seed, etc.)
 * - "rng" map: RNG state bytes
 * - "soup" map: soup data (region-based for compression)
 * - "organisms" map: organism data
 */
public class CheckpointStore {
    
    private static final Logger log = LoggerFactory.getLogger(CheckpointStore.class);
    
    private static final int VERSION = 1;
    
    // Meta keys
    private static final String KEY_VERSION = "version";
    private static final String KEY_CYCLES = "cycles";
    private static final String KEY_SEED = "seed";
    private static final String KEY_SOUP_SIZE = "soup_size";
    private static final String KEY_ORG_COUNT = "org_count";
    private static final String KEY_TOTAL_SPAWNS = "total_spawns";
    private static final String KEY_DEATHS_BY_REAPER = "deaths_reaper";
    private static final String KEY_DEATHS_BY_ERRORS = "deaths_errors";
    
    /**
     * Save checkpoint to MVStore file.
     */
    public static void save(Simulator sim, Path path) throws IOException {
        log.info("Saving checkpoint to {} (MVStore)", path);
        
        // IMPORTANT: Save RNG state FIRST, before any more random calls!
        GameRng.GameRngState rngState = sim.getGameRng().saveState();
        
        try (MVStore store = new MVStore.Builder()
                .fileName(path.toString())
                .compress()
                .open()) {
            
            // Meta map
            MVMap<String, Long> meta = store.openMap("meta");
            meta.put(KEY_VERSION, (long) VERSION);
            meta.put(KEY_CYCLES, sim.getTotalCycles());
            meta.put(KEY_SEED, sim.getActualSeed());
            meta.put(KEY_SOUP_SIZE, (long) sim.getMemoryManager().getTotalMemory());
            meta.put(KEY_ORG_COUNT, (long) sim.getAliveOrganisms().size());
            meta.put(KEY_TOTAL_SPAWNS, (long) sim.getTotalSpawns());
            meta.put(KEY_DEATHS_BY_REAPER, (long) sim.getReaper().getReapCount());
            meta.put(KEY_DEATHS_BY_ERRORS, (long) sim.getDeathsByErrors());
            
            // RNG state
            MVMap<String, byte[]> rng = store.openMap("rng");
            rng.put("state", rngState.toBytes());
            
            // Soup - store non-zero regions
            MVMap<Integer, int[]> soup = store.openMap("soup");
            saveSoupRegions(sim, soup);
            
            // Organisms
            MVMap<Integer, byte[]> organisms = store.openMap("organisms");
            saveOrganisms(sim, organisms);
            
            store.commit();
        }
        
        log.info("Checkpoint saved: {} cycles, {} organisms", 
            sim.getTotalCycles(), sim.getAliveOrganisms().size());
    }
    
    /**
     * Load checkpoint from MVStore file.
     */
    public static CheckpointData load(Path path) throws IOException {
        log.info("Loading checkpoint from {} (MVStore)", path);
        
        try (MVStore store = MVStore.open(path.toString())) {
            
            // Meta
            MVMap<String, Long> meta = store.openMap("meta");
            int version = meta.getOrDefault(KEY_VERSION, 0L).intValue();
            if (version < 1 || version > VERSION) {
                throw new IOException("Unsupported checkpoint version: " + version);
            }
            
            long totalCycles = meta.getOrDefault(KEY_CYCLES, 0L);
            long seed = meta.getOrDefault(KEY_SEED, 0L);
            int soupSize = meta.getOrDefault(KEY_SOUP_SIZE, 0L).intValue();
            int totalSpawns = meta.getOrDefault(KEY_TOTAL_SPAWNS, 0L).intValue();
            int deathsByReaper = meta.getOrDefault(KEY_DEATHS_BY_REAPER, 0L).intValue();
            int deathsByErrors = meta.getOrDefault(KEY_DEATHS_BY_ERRORS, 0L).intValue();
            
            // RNG state
            MVMap<String, byte[]> rngMap = store.openMap("rng");
            byte[] rngBytes = rngMap.get("state");
            GameRng.GameRngState rngState = rngBytes != null 
                ? GameRng.GameRngState.fromBytes(rngBytes) 
                : null;
            
            // Soup
            MVMap<Integer, int[]> soupMap = store.openMap("soup");
            int[] soupData = loadSoupRegions(soupMap, soupSize);
            
            // Organisms
            MVMap<Integer, byte[]> organismsMap = store.openMap("organisms");
            List<OrganismData> organisms = loadOrganisms(organismsMap);
            
            log.info("Checkpoint loaded: {} cycles, {} organisms (v{})", 
                totalCycles, organisms.size(), version);
            
            return new CheckpointData(
                version, totalCycles, seed, rngState, soupSize, soupData, organisms,
                totalSpawns, deathsByReaper, deathsByErrors
            );
        }
    }
    
    /**
     * Check if file is a valid checkpoint.
     */
    public static boolean isValidCheckpoint(Path path) {
        try (MVStore store = MVStore.open(path.toString())) {
            MVMap<String, Long> meta = store.openMap("meta");
            int version = meta.getOrDefault(KEY_VERSION, 0L).intValue();
            return version >= 1 && version <= VERSION;
        } catch (Exception e) {
            return false;
        }
    }
    
    // ========== Private helpers ==========
    
    private static void saveSoupRegions(Simulator sim, MVMap<Integer, int[]> soupMap) {
        int soupSize = sim.getMemoryManager().getTotalMemory();
        var soup = sim.getSoup();
        
        // Find non-zero regions and store them
        int regionStart = -1;
        List<Integer> regionData = new ArrayList<>();
        
        for (int i = 0; i < soupSize; i++) {
            int value = soup.get(i);
            if (value != 0) {
                if (regionStart == -1) {
                    regionStart = i;
                }
                regionData.add(value);
            } else if (regionStart != -1) {
                // Store region
                soupMap.put(regionStart, toIntArray(regionData));
                regionStart = -1;
                regionData.clear();
            }
        }
        
        // Don't forget last region
        if (regionStart != -1) {
            soupMap.put(regionStart, toIntArray(regionData));
        }
    }
    
    private static int[] loadSoupRegions(MVMap<Integer, int[]> soupMap, int soupSize) {
        int[] soup = new int[soupSize];
        
        for (Integer startAddr : soupMap.keySet()) {
            int[] region = soupMap.get(startAddr);
            if (region != null) {
                System.arraycopy(region, 0, soup, startAddr, region.length);
            }
        }
        
        return soup;
    }
    
    private static void saveOrganisms(Simulator sim, MVMap<Integer, byte[]> organismsMap) {
        for (Organism org : sim.getAliveOrganisms()) {
            byte[] data = serializeOrganism(org);
            organismsMap.put(org.getId(), data);
        }
    }
    
    private static List<OrganismData> loadOrganisms(MVMap<Integer, byte[]> organismsMap) {
        List<OrganismData> result = new ArrayList<>();
        
        for (Integer orgId : organismsMap.keySet()) {
            byte[] data = organismsMap.get(orgId);
            if (data != null) {
                OrganismData od = deserializeOrganism(data);
                result.add(od);
            }
        }
        
        return result;
    }
    
    private static byte[] serializeOrganism(Organism org) {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             DataOutputStream out = new DataOutputStream(baos)) {
            
            out.writeInt(org.getId());
            out.writeInt(org.getStartAddr());
            out.writeInt(org.getSize());
            out.writeInt(org.getParentId());
            out.writeLong(org.getBirthCycle());
            out.writeInt(org.getAllocId());
            
            CpuState state = org.getState();
            out.writeInt(state.getIp());
            out.writeInt(state.getErrors());
            out.writeLong(state.getAge());
            
            for (int r = 0; r < 8; r++) {
                out.writeInt(state.getRegister(r));
            }
            
            out.writeBoolean(state.hasPendingAllocation());
            if (state.hasPendingAllocation()) {
                out.writeInt(state.getPendingAllocAddr());
                out.writeInt(state.getPendingAllocSize());
                out.writeInt(state.getPendingAllocId());
            }
            
            return baos.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private static OrganismData deserializeOrganism(byte[] data) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(data);
             DataInputStream in = new DataInputStream(bais)) {
            
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
            
            return od;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private static int[] toIntArray(List<Integer> list) {
        int[] arr = new int[list.size()];
        for (int i = 0; i < list.size(); i++) {
            arr[i] = list.get(i);
        }
        return arr;
    }
    
    // ========== Data classes ==========
    
    /**
     * Loaded checkpoint data.
     */
    public record CheckpointData(
        int version,
        long totalCycles,
        long seed,
        GameRng.GameRngState rngState,
        int soupSize,
        int[] soup,
        List<OrganismData> organisms,
        int totalSpawns,
        int deathsByReaper,
        int deathsByErrors
    ) {
        public boolean hasDeterministicRng() {
            return rngState != null;
        }
    }
    
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
}

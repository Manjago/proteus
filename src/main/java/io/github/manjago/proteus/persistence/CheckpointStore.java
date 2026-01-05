package io.github.manjago.proteus.persistence;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.*;
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
 * - "meta" map: metadata (version, cycles, seed, soup_size, etc.)
 * - "rng" map: RNG state bytes
 * - "soup" map: soup data (region-based for compression)
 * - "organisms" map: organism data
 * - "config" map: simulator configuration
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
    private static final String KEY_NEXT_ORG_ID = "next_org_id";
    private static final String KEY_NEXT_ALLOC_ID = "next_alloc_id";
    
    // Config keys
    private static final String KEY_MUTATION_RATE = "mutation_rate";
    private static final String KEY_MAX_ERRORS = "max_errors";
    private static final String KEY_MAX_ORGANISMS = "max_organisms";
    
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
            meta.put(KEY_NEXT_ORG_ID, (long) sim.getNextOrganismId());
            
            // Save next allocation ID for deterministic restore
            if (sim.getMemoryManager() instanceof BitmapMemoryManager bmm) {
                meta.put(KEY_NEXT_ALLOC_ID, (long) bmm.getNextAllocationId());
            }
            
            // Config map (for restore)
            MVMap<String, String> config = store.openMap("config");
            SimulatorConfig cfg = sim.getConfig();
            config.put(KEY_MUTATION_RATE, String.valueOf(cfg.mutationRate()));
            config.put(KEY_MAX_ERRORS, String.valueOf(cfg.maxErrors()));
            config.put(KEY_MAX_ORGANISMS, String.valueOf(cfg.maxOrganisms()));
            
            // RNG state
            MVMap<String, byte[]> rng = store.openMap("rng");
            rng.put("state", rngState.toBytes());
            
            // Soup - store non-zero regions
            MVMap<Integer, int[]> soup = store.openMap("soup");
            soup.clear();  // Clear old data
            saveSoupRegions(sim, soup);
            
            // Organisms
            MVMap<Integer, byte[]> organisms = store.openMap("organisms");
            organisms.clear();  // Clear old data
            saveOrganisms(sim, organisms);
            
            store.commit();
        }
        
        log.info("Checkpoint saved: {} cycles, {} organisms", 
            sim.getTotalCycles(), sim.getAliveOrganisms().size());
    }
    
    /**
     * Load checkpoint data from MVStore file.
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
            int nextOrgId = meta.getOrDefault(KEY_NEXT_ORG_ID, 0L).intValue();
            int nextAllocId = meta.getOrDefault(KEY_NEXT_ALLOC_ID, 0L).intValue();
            
            // Config
            MVMap<String, String> configMap = store.openMap("config");
            double mutationRate = Double.parseDouble(configMap.getOrDefault(KEY_MUTATION_RATE, "0.002"));
            int maxErrors = Integer.parseInt(configMap.getOrDefault(KEY_MAX_ERRORS, "100"));
            int maxOrganisms = Integer.parseInt(configMap.getOrDefault(KEY_MAX_ORGANISMS, "1000"));
            
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
                totalSpawns, deathsByReaper, deathsByErrors, nextOrgId, nextAllocId,
                mutationRate, maxErrors, maxOrganisms
            );
        }
    }
    
    /**
     * Restore Simulator from checkpoint.
     * 
     * @param path checkpoint file
     * @param configOverride optional config override (null = use checkpoint config)
     * @return restored Simulator ready to run
     */
    public static Simulator restore(Path path, SimulatorConfig configOverride) throws IOException {
        CheckpointData data = load(path);
        
        // Build config - use override if provided, otherwise use checkpoint values
        SimulatorConfig config;
        if (configOverride != null) {
            config = configOverride;
        } else {
            config = SimulatorConfig.builder()
                    .soupSize(data.soupSize())
                    .mutationRate(data.mutationRate())
                    .maxErrors(data.maxErrors())
                    .maxOrganisms(data.maxOrganisms())
                    .randomSeed(data.seed())
                    .build();
        }
        // Restore RNG
        GameRng rng;
        if (data.hasDeterministicRng()) {
            rng = GameRng.restore(data.rngState());
            log.info("Restored RNG state from checkpoint (deterministic resume)");
        } else {
            // Old checkpoint without RNG state - create new RNG from seed
            rng = new GameRng(data.seed());
            log.warn("Checkpoint has no RNG state - resume will NOT be deterministic!");
        }
        
        // Create simulator with restored RNG
        Simulator sim = new Simulator(config, rng);
        
        // Restore soup
        var soup = sim.getSoup();
        for (int i = 0; i < data.soup().length && i < soup.length; i++) {
            soup[i] = data.soup()[i];
        }
        
        // Restore memory manager state with exact allocIds
        if (sim.getMemoryManager() instanceof BitmapMemoryManager bmm) {
            // Mark used regions with original allocIds - organisms
            for (OrganismData od : data.organisms()) {
                bmm.markUsedWithAllocId(od.startAddr, od.size, od.allocId);
            }
            
            // CRITICAL: Mark pending allocations as used too!
            // Without this, pending memory is considered free and can be reallocated
            for (OrganismData od : data.organisms()) {
                if (od.hasPending && od.pendingAllocId >= 0) {
                    bmm.markUsedWithAllocId(od.pendingAddr, od.pendingSize, od.pendingAllocId);
                    log.debug("Restored pending allocation [{},{}) allocId={} for org #{}",
                            od.pendingAddr, od.pendingAddr + od.pendingSize, od.pendingAllocId, od.id);
                }
            }
            
            // Restore next allocation ID counter
            bmm.setNextAllocationId(data.nextAllocId());
        } else {
            // Fallback for other memory managers
            for (OrganismData od : data.organisms()) {
                sim.getMemoryManager().markUsed(od.startAddr, od.size);
            }
        }
        
        // Restore organisms
        for (OrganismData od : data.organisms()) {
            Organism org = restoreOrganism(od, sim);
            sim.addRestoredOrganism(org);
        }
        
        // Restore counters
        sim.restoreState(data.totalCycles(), data.totalSpawns(), data.deathsByErrors(), data.nextOrgId());
        
        log.info("Simulator restored: {} cycles, {} organisms", 
            data.totalCycles(), data.organisms().size());
        
        return sim;
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
    
    /**
     * Get checkpoint info without full load.
     */
    public static String getInfo(Path path) {
        try (MVStore store = MVStore.open(path.toString())) {
            MVMap<String, Long> meta = store.openMap("meta");
            
            int version = meta.getOrDefault(KEY_VERSION, 0L).intValue();
            long cycles = meta.getOrDefault(KEY_CYCLES, 0L);
            long seed = meta.getOrDefault(KEY_SEED, 0L);
            int soupSize = meta.getOrDefault(KEY_SOUP_SIZE, 0L).intValue();
            int orgCount = meta.getOrDefault(KEY_ORG_COUNT, 0L).intValue();
            
            MVMap<String, byte[]> rngMap = store.openMap("rng");
            boolean hasRng = rngMap.get("state") != null;
            
            return String.format(
                "Checkpoint v%d: %,d cycles, %d organisms, soup=%,d, seed=%d%s",
                version, cycles, orgCount, soupSize, seed,
                hasRng ? " (deterministic)" : " (non-deterministic)"
            );
        } catch (Exception e) {
            return "Invalid checkpoint: " + e.getMessage();
        }
    }
    
    // ========== Private helpers ==========
    
    private static void saveSoupRegions(Simulator sim, MVMap<Integer, int[]> soupMap) {
        int soupSize = sim.getMemoryManager().getTotalMemory();
        var soup = sim.getSoup();
        
        int regionStart = -1;
        List<Integer> regionData = new ArrayList<>();
        
        for (int i = 0; i < soupSize; i++) {
            int value = soup[i];
            if (value != 0) {
                if (regionStart == -1) {
                    regionStart = i;
                }
                regionData.add(value);
            } else if (regionStart != -1) {
                soupMap.put(regionStart, toIntArray(regionData));
                regionStart = -1;
                regionData.clear();
            }
        }
        
        if (regionStart != -1) {
            soupMap.put(regionStart, toIntArray(regionData));
        }
    }
    
    private static int[] loadSoupRegions(MVMap<Integer, int[]> soupMap, int soupSize) {
        int[] soup = new int[soupSize];
        
        for (Integer startAddr : soupMap.keySet()) {
            int[] region = soupMap.get(startAddr);
            if (region != null && startAddr + region.length <= soupSize) {
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
            
            // Name (may be null) - added in checkpoint v1.1
            String name = org.getName();
            out.writeBoolean(name != null);
            if (name != null) {
                out.writeUTF(name);
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
            
            // Name (may be null) - added in checkpoint v1.1
            // Check if there's more data (for backward compatibility)
            if (in.available() > 0) {
                boolean hasName = in.readBoolean();
                if (hasName) {
                    od.name = in.readUTF();
                }
            }
            
            return od;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
    
    private static Organism restoreOrganism(OrganismData od, Simulator sim) {
        // Create organism (this creates a new CpuState internally)
        Organism org = new Organism(od.id, od.startAddr, od.size, od.parentId, od.birthCycle, od.allocId);
        
        // Restore name
        if (od.name != null) {
            org.setName(od.name);
        }
        
        // Now restore the CpuState fields
        CpuState state = org.getState();
        state.setIp(od.ip);
        
        for (int i = 0; i < 8; i++) {
            state.setRegister(i, od.registers[i]);
        }
        
        // Restore error count
        for (int i = 0; i < od.errors; i++) {
            state.incrementErrors();
        }
        
        // Restore age - need setAge method in CpuState
        state.setAge(od.age);
        
        // Restore pending allocation
        if (od.hasPending) {
            state.setPendingAllocation(od.pendingAddr, od.pendingSize, od.pendingAllocId);
        }
        
        return org;
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
        int deathsByErrors,
        int nextOrgId,
        int nextAllocId,
        double mutationRate,
        int maxErrors,
        int maxOrganisms
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
        public String name;  // Display name (may be null)
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

package io.github.manjago.proteus.integration;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.persistence.CheckpointStore;
import io.github.manjago.proteus.sim.Simulator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Smoke tests for basic simulation and checkpoint functionality.
 * 
 * These tests verify that the core simulation loop works end-to-end,
 * including checkpoint save/load which involves RNG serialization.
 */
@DisplayName("Smoke Tests")
class SmokeTest {
    
    @TempDir
    Path tempDir;
    
    @Test
    @DisplayName("Run simulation for 10 cycles")
    void runSimulation10Cycles() {
        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(1000)
                .maxCycles(10)
                .randomSeed(12345)
                .build();
        
        Simulator sim = new Simulator(config);
        sim.seedAdam();
        
        sim.run(10);
        
        assertEquals(10, sim.getTotalCycles());
        assertFalse(sim.getAliveOrganisms().isEmpty(), "Should have at least one organism");
    }
    
    @Test
    @DisplayName("Run simulation and save checkpoint")
    void runAndSaveCheckpoint() throws Exception {
        Path checkpointFile = tempDir.resolve("test.mv");
        
        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(1000)
                .maxCycles(100)
                .randomSeed(42)
                .build();
        
        Simulator sim = new Simulator(config);
        sim.seedAdam();
        sim.run(100);
        
        // This should not throw - tests RNG serialization
        CheckpointStore.save(sim, checkpointFile);
        
        assertTrue(checkpointFile.toFile().exists(), "Checkpoint file should exist");
        assertTrue(checkpointFile.toFile().length() > 0, "Checkpoint file should not be empty");
        assertTrue(CheckpointStore.isValidCheckpoint(checkpointFile), "Should be valid checkpoint");
    }
    
    @Test
    @DisplayName("Save and load checkpoint")
    void saveAndLoadCheckpoint() throws Exception {
        Path checkpointFile = tempDir.resolve("test.mv");
        
        // Run and save
        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(1000)
                .maxCycles(50)
                .randomSeed(999)
                .build();
        
        Simulator sim1 = new Simulator(config);
        sim1.seedAdam();
        sim1.run(50);
        
        long cyclesBefore = sim1.getTotalCycles();
        int orgsBefore = sim1.getAliveOrganisms().size();
        
        CheckpointStore.save(sim1, checkpointFile);
        
        // Load
        var data = CheckpointStore.load(checkpointFile);
        
        assertEquals(cyclesBefore, data.totalCycles());
        assertEquals(orgsBefore, data.organisms().size());
        assertEquals(999, data.seed());
        assertTrue(data.hasDeterministicRng(), "Should have RNG state");
    }
    
    @Test
    @DisplayName("Save, load, and resume simulation")
    void saveLoadAndResume() throws Exception {
        Path checkpointFile = tempDir.resolve("test.mv");
        
        // Run first part
        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(1000)
                .randomSeed(777)
                .build();
        
        Simulator sim1 = new Simulator(config);
        sim1.seedAdam();
        sim1.run(50);
        
        CheckpointStore.save(sim1, checkpointFile);
        
        // Resume and run more
        Simulator sim2 = CheckpointStore.restore(checkpointFile, null);
        
        assertEquals(50, sim2.getTotalCycles(), "Should start at saved cycle");
        
        sim2.run(50);
        
        assertEquals(100, sim2.getTotalCycles(), "Should have run 50 more cycles");
    }
    
    @Test
    @DisplayName("Determinism: same seed produces same result")
    void determinismSameSeed() throws Exception {
        Path checkpoint1 = tempDir.resolve("state1.mv");
        Path checkpoint2 = tempDir.resolve("state2.mv");
        
        // Run 1
        SimulatorConfig config1 = SimulatorConfig.builder()
                .soupSize(1000)
                .randomSeed(12345)
                .build();
        
        Simulator sim1 = new Simulator(config1);
        sim1.seedAdam();
        sim1.run(100);
        CheckpointStore.save(sim1, checkpoint1);
        
        // Run 2 with same seed
        SimulatorConfig config2 = SimulatorConfig.builder()
                .soupSize(1000)
                .randomSeed(12345)
                .build();
        
        Simulator sim2 = new Simulator(config2);
        sim2.seedAdam();
        sim2.run(100);
        CheckpointStore.save(sim2, checkpoint2);
        
        // Compare
        var data1 = CheckpointStore.load(checkpoint1);
        var data2 = CheckpointStore.load(checkpoint2);
        
        assertEquals(data1.totalCycles(), data2.totalCycles(), "Cycles should match");
        assertEquals(data1.totalSpawns(), data2.totalSpawns(), "Spawns should match");
        assertEquals(data1.organisms().size(), data2.organisms().size(), "Organism count should match");
        
        // Compare RNG state
        assertArrayEquals(data1.rngState().toBytes(), data2.rngState().toBytes(), 
            "RNG state should be identical");
        
        // Compare soup
        assertArrayEquals(data1.soup(), data2.soup(), "Soup should be identical");
    }
    
    @Test
    @DisplayName("Checkpoint info is readable")
    void checkpointInfoReadable() throws Exception {
        Path checkpointFile = tempDir.resolve("test.mv");
        
        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(500)
                .randomSeed(111)
                .build();
        
        Simulator sim = new Simulator(config);
        sim.seedAdam();
        sim.run(25);
        CheckpointStore.save(sim, checkpointFile);
        
        String info = CheckpointStore.getInfo(checkpointFile);
        
        assertTrue(info.contains("25"), "Should contain cycle count");
        assertTrue(info.contains("111"), "Should contain seed");
        assertTrue(info.contains("deterministic"), "Should mention deterministic");
    }
    
    @Test
    @DisplayName("Determinism: two resumes from same checkpoint produce same result")
    void determinismSameCheckpoint() throws Exception {
        Path checkpoint = tempDir.resolve("base.mv");
        Path result1 = tempDir.resolve("result1.mv");
        Path result2 = tempDir.resolve("result2.mv");
        
        // Create initial checkpoint
        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(1000)
                .randomSeed(55555)
                .build();
        
        Simulator simBase = new Simulator(config);
        simBase.seedAdam();
        simBase.run(50);
        CheckpointStore.save(simBase, checkpoint);
        
        // Resume 1: load checkpoint, run 100 more cycles, save
        Simulator sim1 = CheckpointStore.restore(checkpoint, null);
        sim1.run(100);
        CheckpointStore.save(sim1, result1);
        
        // Resume 2: load SAME checkpoint, run 100 more cycles, save
        Simulator sim2 = CheckpointStore.restore(checkpoint, null);
        sim2.run(100);
        CheckpointStore.save(sim2, result2);
        
        // Compare results - should be identical
        var data1 = CheckpointStore.load(result1);
        var data2 = CheckpointStore.load(result2);
        
        assertEquals(data1.totalCycles(), data2.totalCycles(), "Cycles should match");
        assertEquals(data1.totalSpawns(), data2.totalSpawns(), "Spawns should match");
        assertEquals(data1.deathsByErrors(), data2.deathsByErrors(), "Deaths by errors should match");
        assertEquals(data1.deathsByReaper(), data2.deathsByReaper(), "Deaths by reaper should match");
        assertEquals(data1.organisms().size(), data2.organisms().size(), "Organism count should match");
        
        // Compare RNG state - critical for determinism
        assertArrayEquals(data1.rngState().toBytes(), data2.rngState().toBytes(), 
            "RNG state should be identical after same operations");
        
        // Compare soup
        assertArrayEquals(data1.soup(), data2.soup(), "Soup should be identical");
        
        // Compare each organism
        assertEquals(data1.organisms().size(), data2.organisms().size());
        for (int i = 0; i < data1.organisms().size(); i++) {
            var org1 = data1.organisms().get(i);
            var org2 = data2.organisms().get(i);
            assertEquals(org1.id, org2.id, "Organism ID mismatch at index " + i);
            assertEquals(org1.startAddr, org2.startAddr, "Organism startAddr mismatch for #" + org1.id);
            assertEquals(org1.ip, org2.ip, "Organism IP mismatch for #" + org1.id);
            assertArrayEquals(org1.registers, org2.registers, "Organism registers mismatch for #" + org1.id);
        }
    }
}

package io.github.manjago.proteus.integration;

import io.github.manjago.proteus.config.SimulatorConfig;
import io.github.manjago.proteus.core.Assembler;
import io.github.manjago.proteus.persistence.CheckpointStore;
import io.github.manjago.proteus.sim.Simulator;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.stream.Collectors;

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

    /** Cached ancestor genome (loaded from test resources) */
    private static int[] ancestorGenome;

    @BeforeAll
    static void loadAncestor() throws Exception {
        try (InputStream is = SmokeTest.class.getResourceAsStream("/test-replicator.asm")) {
            assertNotNull(is, "test-replicator.asm should be in test resources");
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(is, StandardCharsets.UTF_8))) {
                String source = reader.lines().collect(Collectors.joining("\n"));
                Assembler assembler = new Assembler();
                ancestorGenome = assembler.assemble(source);
            }
        }
    }

    /**
     * Helper: create simulator and inject ancestor organism.
     */
    private Simulator createSimWithAncestor(SimulatorConfig config) {
        Simulator sim = new Simulator(config);
        var org = sim.injectOrganism(ancestorGenome.clone(), "Ancestor");
        assertNotNull(org, "Should inject ancestor successfully");
        return sim;
    }

    @Test
    @DisplayName("Run simulation for 10 cycles")
    void runSimulation10Cycles() {
        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(1000)
                .maxCycles(10)
                .randomSeed(12345)
                .build();

        Simulator sim = createSimWithAncestor(config);

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

        Simulator sim = createSimWithAncestor(config);
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

        Simulator sim1 = createSimWithAncestor(config);
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

        Simulator sim1 = createSimWithAncestor(config);
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

        Simulator sim1 = createSimWithAncestor(config1);
        sim1.run(100);
        CheckpointStore.save(sim1, checkpoint1);

        // Run 2 with same seed
        SimulatorConfig config2 = SimulatorConfig.builder()
                .soupSize(1000)
                .randomSeed(12345)
                .build();

        Simulator sim2 = createSimWithAncestor(config2);
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

        Simulator sim = createSimWithAncestor(config);
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

        Simulator simBase = createSimWithAncestor(config);
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

    @Test
    @DisplayName("Injected organism name is preserved in checkpoint")
    void organismNamePreservedInCheckpoint() throws Exception {
        Path checkpointFile = tempDir.resolve("named.mv");

        SimulatorConfig config = SimulatorConfig.builder()
                .soupSize(1000)
                .randomSeed(123)
                .build();

        Simulator sim = new Simulator(config);
        var org = sim.injectOrganism(ancestorGenome.clone(), "TestName");
        assertNotNull(org);
        assertEquals("TestName", org.getName());

        sim.run(10);
        CheckpointStore.save(sim, checkpointFile);

        // Load and verify name preserved
        var data = CheckpointStore.load(checkpointFile);
        assertEquals(1, data.organisms().size());
        assertEquals("TestName", data.organisms().get(0).name, "Name should be preserved");
    }
}

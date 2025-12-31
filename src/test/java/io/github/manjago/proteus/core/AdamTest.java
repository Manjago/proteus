package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Adam v3 (ISA v1.2, Position-Independent Code).
 */
class AdamTest {
    
    private AtomicIntegerArray memory;
    private CpuState state;
    
    @BeforeEach
    void setUp() {
        memory = new AtomicIntegerArray(1000);
        // v1.2: CpuState now takes startAddr (absolute), IP starts at 0 (relative)
        state = new CpuState(0);
    }
    
    @Test
    @DisplayName("Adam v3 genome has expected size (14 instructions)")
    void adamHasExpectedSize() {
        int[] genome = Adam.genome();
        assertEquals(14, genome.length, "Adam v3 should be 14 instructions");
        assertEquals(Adam.SIZE, genome.length, "SIZE constant should match genome");
    }
    
    @Test
    @DisplayName("Adam genome can be disassembled")
    void adamCanBeDisassembled() {
        String asm = Adam.disassemble();
        
        assertNotNull(asm);
        assertFalse(asm.isEmpty());
        
        // Should contain key instructions
        assertTrue(asm.contains("GETADDR"), "Should have GETADDR (v1.2)");
        assertTrue(asm.contains("ALLOCATE"), "Should have ALLOCATE");
        assertTrue(asm.contains("COPY"), "Should have COPY");
        assertTrue(asm.contains("SPAWN"), "Should have SPAWN");
        assertTrue(asm.contains("JMP"), "Should have JMP");
        assertTrue(asm.contains("JMPN"), "Should have JMPN");
        
        System.out.println("=== Adam v3 Disassembly ===");
        System.out.println(asm);
    }
    
    @Test
    @DisplayName("Adam builds correct SIZE constant in R4")
    void adamBuildsSizeConstant() {
        int[] genome = Adam.genome();
        loadGenome(genome, 0);
        
        // Execute first instruction (MOVI R4, 14)
        VirtualCPU cpu = new VirtualCPU(0.0);
        cpu.execute(state, memory);
        
        assertEquals(14, state.getRegister(4), "R4 should contain SIZE=14");
    }
    
    @Test
    @DisplayName("Adam gets its start address via GETADDR (v1.2)")
    void adamGetsStartAddress() {
        int[] genome = Adam.genome();
        int startAddr = 100;  // Test with non-zero start address
        loadGenome(genome, startAddr);
        state = new CpuState(startAddr);
        
        // Execute MOVI R4, 14 and GETADDR R7
        VirtualCPU cpu = new VirtualCPU(0.0);
        cpu.execute(state, memory);  // MOVI
        cpu.execute(state, memory);  // GETADDR
        
        assertEquals(startAddr, state.getRegister(7), 
            "R7 should contain organism's start address");
    }
    
    @Test
    @DisplayName("Adam successfully copies itself when ALLOCATE succeeds")
    void adamCopiesItself() {
        int[] genome = Adam.genome();
        int size = genome.length;
        loadGenome(genome, 0);
        
        // Create handler that allocates at address 500
        final int childAddr = 500;
        AtomicBoolean spawnCalled = new AtomicBoolean(false);
        
        SystemCallHandler handler = new SystemCallHandler() {
            @Override
            public int allocate(int requestedSize) {
                assertEquals(14, requestedSize, "Should request 14 cells");
                return childAddr;
            }
            
            @Override
            public boolean spawn(int address, int spawnSize, CpuState parentState) {
                assertEquals(childAddr, address, "Should spawn at allocated address");
                assertEquals(14, spawnSize, "Should spawn with size 14");
                spawnCalled.set(true);
                return true;
            }
        };
        
        VirtualCPU cpu = new VirtualCPU(0.0, new Random(), handler);
        
        // Execute until SPAWN completes (instruction 11 in v3)
        int maxCycles = 200;
        while (state.getIp() <= 11 && maxCycles-- > 0) {
            cpu.execute(state, memory);
        }
        
        assertTrue(spawnCalled.get(), "SPAWN should have been called");
        
        // Verify copy is identical
        for (int i = 0; i < size; i++) {
            assertEquals(memory.get(i), memory.get(childAddr + i),
                "Child genome at offset " + i + " should match parent");
        }
        
        System.out.println("=== Adam v3 successfully replicated! ===");
        System.out.println("Original at address 0");
        System.out.println("Copy at address " + childAddr);
        System.out.println("Verified " + size + " instructions copied correctly");
    }
    
    @Test
    @DisplayName("Adam works from non-zero address (position-independent)")
    void adamWorksFromNonZeroAddress() {
        int[] genome = Adam.genome();
        int startAddr = 200;
        int childAddr = 600;
        loadGenome(genome, startAddr);
        
        state = new CpuState(startAddr);  // v1.2: startAddr sets absolute position
        
        AtomicBoolean spawnCalled = new AtomicBoolean(false);
        
        SystemCallHandler handler = new SystemCallHandler() {
            @Override
            public int allocate(int requestedSize) {
                return childAddr;
            }
            
            @Override
            public boolean spawn(int address, int size, CpuState parentState) {
                assertEquals(childAddr, address);
                spawnCalled.set(true);
                return true;
            }
        };
        
        VirtualCPU cpu = new VirtualCPU(0.0, new Random(), handler);
        
        // Execute until SPAWN
        int maxCycles = 200;
        while (!spawnCalled.get() && maxCycles-- > 0) {
            cpu.execute(state, memory);
        }
        
        assertTrue(spawnCalled.get(), "SPAWN should work from non-zero address");
        
        // Verify copy at childAddr matches original at startAddr
        for (int i = 0; i < genome.length; i++) {
            assertEquals(memory.get(startAddr + i), memory.get(childAddr + i),
                "Child should match parent at offset " + i);
        }
        
        System.out.println("=== Position-Independent Code verified! ===");
        System.out.println("Parent at " + startAddr + ", child at " + childAddr);
    }
    
    @Test
    @DisplayName("Adam loops back to ALLOCATE after SPAWN (v1.2 relative jump)")
    void adamLoopsAfterSpawn() {
        int[] genome = Adam.genome();
        loadGenome(genome, 0);
        
        int spawnCount = 0;
        final int[] spawns = {0};
        
        SystemCallHandler handler = new SystemCallHandler() {
            private int nextAddr = 500;
            
            @Override
            public int allocate(int requestedSize) {
                int addr = nextAddr;
                nextAddr += 100;
                return addr;
            }
            
            @Override
            public boolean spawn(int address, int size, CpuState parentState) {
                spawns[0]++;
                return true;
            }
        };
        
        VirtualCPU cpu = new VirtualCPU(0.0, new Random(), handler);
        
        // Execute until 2 spawns (proves looping works)
        int maxCycles = 500;
        while (spawns[0] < 2 && maxCycles-- > 0) {
            cpu.execute(state, memory);
        }
        
        assertEquals(2, spawns[0], "Should complete 2 replication cycles");
        System.out.println("Adam completed " + spawns[0] + " replication cycles");
    }
    
    @Test
    @DisplayName("Adam handles ALLOCATE failure gracefully")
    void adamHandlesAllocateFailure() {
        int[] genome = Adam.genome();
        loadGenome(genome, 0);
        
        // Use failing handler
        VirtualCPU cpu = new VirtualCPU(0.0);
        
        // Execute through ALLOCATE (instruction 2 in v3)
        for (int i = 0; i < 3; i++) {
            cpu.execute(state, memory);
        }
        
        // R3 should be -1 (allocation failed)
        assertEquals(-1, state.getRegister(3), "R3 should be -1 on failed allocate");
        
        // Program continues (doesn't crash)
        cpu.execute(state, memory);
        assertTrue(state.getIp() > 3, "Should continue after failed allocate");
    }
    
    @Test
    @DisplayName("Adam copy loop iterates correct number of times (14)")
    void adamCopyLoopIterations() {
        int[] genome = Adam.genome();
        loadGenome(genome, 0);
        
        SystemCallHandler handler = new SystemCallHandler() {
            @Override
            public int allocate(int requestedSize) {
                return 500;
            }
            
            @Override
            public boolean spawn(int address, int size, CpuState parentState) {
                return true;
            }
        };
        
        VirtualCPU cpu = new VirtualCPU(0.0, new Random(), handler);
        
        // Run until we reach SPAWN (addr 11 in v3)
        int copyIterations = 0;
        int maxCycles = 200;
        
        while (state.getIp() != 11 && maxCycles-- > 0) {
            int ipBefore = state.getIp();
            cpu.execute(state, memory);
            
            // Count how many times we execute the COPY instruction at addr 6 (v3)
            if (ipBefore == 6) {
                copyIterations++;
            }
        }
        
        assertEquals(14, copyIterations, "Should iterate 14 times (once per instruction)");
    }
    
    // ========== Helper Methods ==========
    
    private void loadGenome(int[] genome, int startAddr) {
        for (int i = 0; i < genome.length; i++) {
            memory.set(startAddr + i, genome[i]);
        }
    }
}

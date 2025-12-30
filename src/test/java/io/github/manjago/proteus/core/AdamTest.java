package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;

class AdamTest {
    
    private AtomicIntegerArray memory;
    private CpuState state;
    
    @BeforeEach
    void setUp() {
        memory = new AtomicIntegerArray(1000);
        state = new CpuState(0); // Start at address 0
    }
    
    @Test
    @DisplayName("Adam genome has expected size")
    void adamHasExpectedSize() {
        int[] genome = Adam.genome();
        assertEquals(13, genome.length, "Adam should be 13 instructions");
    }
    
    @Test
    @DisplayName("Adam genome can be disassembled")
    void adamCanBeDisassembled() {
        String asm = Adam.disassemble();
        
        assertNotNull(asm);
        assertFalse(asm.isEmpty());
        
        // Should contain key instructions
        assertTrue(asm.contains("ALLOCATE"), "Should have ALLOCATE");
        assertTrue(asm.contains("COPY"), "Should have COPY");
        assertTrue(asm.contains("SPAWN"), "Should have SPAWN");
        assertTrue(asm.contains("JMP"), "Should have JMP");
        
        System.out.println("=== Adam Disassembly ===");
        System.out.println(asm);
    }
    
    @Test
    @DisplayName("Adam builds correct SIZE constant in R4")
    void adamBuildsSizeConstant() {
        int[] genome = Adam.genome();
        loadGenome(genome, 0);
        
        // Execute first instruction (MOVI R4, 13)
        VirtualCPU cpu = new VirtualCPU(0.0);
        cpu.execute(state, memory);
        
        assertEquals(13, state.getRegister(4), "R4 should contain SIZE=13");
    }
    
    @Test
    @DisplayName("Adam builds correct COPY_LOOP address in R5")
    void adamBuildsCopyLoopAddress() {
        int[] genome = Adam.genome();
        loadGenome(genome, 0);
        
        // Execute first two instructions (MOVI R4, MOVI R5)
        VirtualCPU cpu = new VirtualCPU(0.0);
        cpu.execute(state, memory);
        cpu.execute(state, memory);
        
        assertEquals(5, state.getRegister(5), "R5 should contain COPY_LOOP=5");
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
                assertEquals(13, requestedSize, "Should request 13 cells");
                return childAddr;
            }
            
            @Override
            public boolean spawn(int address, int spawnSize, CpuState parentState) {
                assertEquals(childAddr, address, "Should spawn at allocated address");
                assertEquals(13, spawnSize, "Should spawn with size 13");
                spawnCalled.set(true);
                return true;
            }
        };
        
        VirtualCPU cpu = new VirtualCPU(0.0, new Random(), handler);
        
        // Execute until SPAWN completes (instruction 10)
        int maxCycles = 200;
        while (state.getIp() <= 10 && maxCycles-- > 0) {
            cpu.execute(state, memory);
        }
        
        assertTrue(spawnCalled.get(), "SPAWN should have been called");
        
        // Verify copy is identical
        for (int i = 0; i < size; i++) {
            assertEquals(memory.get(i), memory.get(childAddr + i),
                "Child genome at offset " + i + " should match parent");
        }
        
        System.out.println("=== Adam successfully replicated! ===");
        System.out.println("Original at address 0");
        System.out.println("Copy at address " + childAddr);
        System.out.println("Verified " + size + " instructions copied correctly");
    }
    
    @Test
    @DisplayName("Adam loops back to start after SPAWN")
    void adamLoopsAfterSpawn() {
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
        
        // Execute first full cycle
        int maxCycles = 500;
        while (state.getIp() != 0 || state.getAge() == 0) {
            if (maxCycles-- <= 0) {
                fail("Adam did not loop back to start within cycle limit");
            }
            cpu.execute(state, memory);
        }
        
        // Should be back at IP=0 after completing one replication cycle
        assertEquals(0, state.getIp(), "Should loop back to start");
        assertTrue(state.getAge() > 50, "Should have executed many instructions");
        
        System.out.println("Adam completed one full cycle in " + state.getAge() + " instructions");
    }
    
    @Test
    @DisplayName("Adam handles ALLOCATE failure gracefully")
    void adamHandlesAllocateFailure() {
        int[] genome = Adam.genome();
        loadGenome(genome, 0);
        
        // Use failing handler
        VirtualCPU cpu = new VirtualCPU(0.0);
        
        // Execute through ALLOCATE (instruction 2)
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
    @DisplayName("Adam copy loop iterates correct number of times")
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
        
        // Run until we reach SPAWN (addr 10)
        int copyIterations = 0;
        int maxCycles = 200;
        
        while (state.getIp() != 10 && maxCycles-- > 0) {
            int ipBefore = state.getIp();
            cpu.execute(state, memory);
            
            // Count how many times we execute the COPY instruction at addr 5
            if (ipBefore == 5) {
                copyIterations++;
            }
        }
        
        assertEquals(13, copyIterations, "Should iterate 13 times (once per instruction)");
    }
    
    // ========== Helper Methods ==========
    
    private void loadGenome(int[] genome, int startAddr) {
        for (int i = 0; i < genome.length; i++) {
            memory.set(startAddr + i, genome[i]);
        }
    }
}

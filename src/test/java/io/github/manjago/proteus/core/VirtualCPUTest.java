package io.github.manjago.proteus.core;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;

import java.util.Random;
import java.util.concurrent.atomic.AtomicIntegerArray;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.manjago.proteus.core.OpCode.*;
import static io.github.manjago.proteus.core.ExecutionResult.*;

class VirtualCPUTest {
    
    private VirtualCPU cpu;
    private CpuState state;
    private AtomicIntegerArray memory;
    
    @BeforeEach
    void setUp() {
        cpu = new VirtualCPU(0.0); // No mutations for predictable tests
        state = new CpuState();
        memory = new AtomicIntegerArray(1000);
    }
    
    // ========== Basic Instructions ==========
    
    @Nested
    @DisplayName("Basic Instructions")
    class BasicInstructions {
        
        @Test
        @DisplayName("NOP advances IP and does nothing else")
        void nopAdvancesIp() {
            memory.set(0, encode(NOP));
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(OK, result);
            assertEquals(1, state.getIp());
            assertEquals(1, state.getAge());
        }
        
        @Test
        @DisplayName("MOV copies register value")
        void movCopiesRegister() {
            state.setRegister(1, 42);
            memory.set(0, encode(MOV, 0, 1)); // R0 = R1
            
            cpu.execute(state, memory);
            
            assertEquals(42, state.getRegister(0));
            assertEquals(42, state.getRegister(1)); // Source unchanged
        }
    }
    
    // ========== Arithmetic Instructions ==========
    
    @Nested
    @DisplayName("Arithmetic Instructions")
    class ArithmeticInstructions {
        
        @Test
        @DisplayName("ADD adds two registers")
        void addRegisters() {
            state.setRegister(0, 10);
            state.setRegister(1, 32);
            memory.set(0, encode(ADD, 0, 1)); // R0 = R0 + R1
            
            cpu.execute(state, memory);
            
            assertEquals(42, state.getRegister(0));
        }
        
        @Test
        @DisplayName("SUB subtracts registers")
        void subRegisters() {
            state.setRegister(0, 50);
            state.setRegister(1, 8);
            memory.set(0, encode(SUB, 0, 1)); // R0 = R0 - R1
            
            cpu.execute(state, memory);
            
            assertEquals(42, state.getRegister(0));
        }
        
        @Test
        @DisplayName("INC increments register")
        void incRegister() {
            state.setRegister(0, 41);
            memory.set(0, encode(INC, 0));
            
            cpu.execute(state, memory);
            
            assertEquals(42, state.getRegister(0));
        }
        
        @Test
        @DisplayName("DEC decrements register")
        void decRegister() {
            state.setRegister(0, 43);
            memory.set(0, encode(DEC, 0));
            
            cpu.execute(state, memory);
            
            assertEquals(42, state.getRegister(0));
        }
        
        @Test
        @DisplayName("Arithmetic handles overflow")
        void arithmeticOverflow() {
            state.setRegister(0, Integer.MAX_VALUE);
            memory.set(0, encode(INC, 0));
            
            cpu.execute(state, memory);
            
            assertEquals(Integer.MIN_VALUE, state.getRegister(0));
        }
    }
    
    // ========== Memory Instructions ==========
    
    @Nested
    @DisplayName("Memory Instructions")
    class MemoryInstructions {
        
        @Test
        @DisplayName("LOAD reads from memory")
        void loadFromMemory() {
            memory.set(100, 42);
            state.setRegister(1, 100); // Address in R1
            memory.set(0, encode(LOAD, 0, 1)); // R0 = memory[R1]
            
            cpu.execute(state, memory);
            
            assertEquals(42, state.getRegister(0));
        }
        
        @Test
        @DisplayName("STORE writes to memory")
        void storeToMemory() {
            state.setRegister(0, 100); // Address in R0
            state.setRegister(1, 42);  // Value in R1
            memory.set(0, encode(STORE, 0, 1)); // memory[R0] = R1
            
            cpu.execute(state, memory);
            
            assertEquals(42, memory.get(100));
        }
        
        @Test
        @DisplayName("LOAD with out-of-bounds address reports error")
        void loadOutOfBounds() {
            state.setRegister(1, 9999); // Invalid address
            memory.set(0, encode(LOAD, 0, 1));
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(ERROR_MEMORY_OUT_OF_BOUNDS, result);
            assertEquals(1, state.getErrors());
            assertEquals(1, state.getIp()); // Still advances
        }
        
        @Test
        @DisplayName("STORE with out-of-bounds address reports error")
        void storeOutOfBounds() {
            state.setRegister(0, -1); // Invalid address
            memory.set(0, encode(STORE, 0, 1));
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(ERROR_MEMORY_OUT_OF_BOUNDS, result);
            assertEquals(1, state.getErrors());
        }
    }
    
    // ========== Control Flow Instructions ==========
    
    @Nested
    @DisplayName("Control Flow Instructions")
    class ControlFlowInstructions {
        
        @Test
        @DisplayName("JMP sets IP to register value")
        void jmpSetsIp() {
            state.setRegister(0, 100);
            memory.set(0, encode(JMP, 0));
            
            cpu.execute(state, memory);
            
            assertEquals(100, state.getIp());
        }
        
        @Test
        @DisplayName("JMPZ jumps when register is zero")
        void jmpzJumpsOnZero() {
            state.setRegister(0, 0);   // Condition = 0
            state.setRegister(1, 100); // Target address
            memory.set(0, encode(JMPZ, 0, 1));
            
            cpu.execute(state, memory);
            
            assertEquals(100, state.getIp());
        }
        
        @Test
        @DisplayName("JMPZ does not jump when register is non-zero")
        void jmpzNoJumpOnNonZero() {
            state.setRegister(0, 5);   // Condition != 0
            state.setRegister(1, 100); // Target address
            memory.set(0, encode(JMPZ, 0, 1));
            
            cpu.execute(state, memory);
            
            assertEquals(1, state.getIp()); // Just advanced
        }
        
        @Test
        @DisplayName("JMPN jumps when register is non-zero")
        void jmpnJumpsOnNonZero() {
            state.setRegister(0, 5);   // Condition != 0
            state.setRegister(1, 100); // Target address
            memory.set(0, encode(JMPN, 0, 1));
            
            cpu.execute(state, memory);
            
            assertEquals(100, state.getIp());
        }
        
        @Test
        @DisplayName("JMPN does not jump when register is zero")
        void jmpnNoJumpOnZero() {
            state.setRegister(0, 0);   // Condition = 0
            state.setRegister(1, 100); // Target address
            memory.set(0, encode(JMPN, 0, 1));
            
            cpu.execute(state, memory);
            
            assertEquals(1, state.getIp()); // Just advanced
        }
    }
    
    // ========== COPY Instruction ==========
    
    @Nested
    @DisplayName("COPY Instruction")
    class CopyInstruction {
        
        @Test
        @DisplayName("COPY copies memory cell without mutation when rate is 0")
        void copyWithoutMutation() {
            memory.set(100, 0xDEADBEEF);
            state.setRegister(0, 100); // Source
            state.setRegister(1, 200); // Destination
            memory.set(0, encode(COPY, 0, 1));
            
            cpu.execute(state, memory);
            
            assertEquals(0xDEADBEEF, memory.get(200));
        }
        
        @Test
        @DisplayName("COPY with 100% mutation rate always mutates")
        void copyWithMutation() {
            VirtualCPU mutatingCpu = new VirtualCPU(1.0, new Random(42), SystemCallHandler.FAILING);
            
            memory.set(100, 0xDEADBEEF);
            state.setRegister(0, 100); // Source
            state.setRegister(1, 200); // Destination
            memory.set(0, encode(COPY, 0, 1));
            
            mutatingCpu.execute(state, memory);
            
            // Value should be different (one bit flipped)
            int copied = memory.get(200);
            assertNotEquals(0xDEADBEEF, copied);
            
            // Should differ by exactly one bit
            int xor = copied ^ 0xDEADBEEF;
            assertEquals(1, Integer.bitCount(xor));
        }
        
        @Test
        @DisplayName("COPY with invalid source address reports error")
        void copyInvalidSource() {
            state.setRegister(0, -1);  // Invalid source
            state.setRegister(1, 200); // Valid destination
            memory.set(0, encode(COPY, 0, 1));
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(ERROR_MEMORY_OUT_OF_BOUNDS, result);
        }
    }
    
    // ========== SEARCH Instruction ==========
    
    @Nested
    @DisplayName("SEARCH Instruction")
    class SearchInstruction {
        
        @Test
        @DisplayName("SEARCH finds pattern in memory")
        void searchFindsPattern() {
            // Use unique non-zero values (NOP = 0, same as uninitialized memory!)
            int marker1 = encode(INC, 1);  // Some non-zero instruction
            int marker2 = encode(INC, 2);
            int marker3 = encode(INC, 3);
            
            // Place pattern at address 500
            memory.set(500, marker1);
            memory.set(501, marker2);
            memory.set(502, marker3);
            
            // Template at address 100
            memory.set(100, marker1);
            memory.set(101, marker2);
            memory.set(102, marker3);
            
            state.setRegister(0, 200); // Search from 200 (skip template at 100)
            state.setRegister(1, 100); // Template address
            state.setRegister(2, 3);   // Template length
            memory.set(0, encode(SEARCH, 0, 1, 2, 3)); // Result in R3
            
            cpu.execute(state, memory);
            
            // Should find at address 500
            assertEquals(500, state.getRegister(3));
        }
        
        @Test
        @DisplayName("SEARCH returns -1 when pattern not found")
        void searchNotFound() {
            // Template at address 100
            memory.set(100, 0x12345678);
            memory.set(101, 0x9ABCDEF0);
            
            state.setRegister(0, 200); // Search from 200
            state.setRegister(1, 100); // Template address
            state.setRegister(2, 2);   // Template length
            memory.set(0, encode(SEARCH, 0, 1, 2, 3));
            
            cpu.execute(state, memory);
            
            assertEquals(-1, state.getRegister(3));
        }
        
        @Test
        @DisplayName("SEARCH with invalid template returns -1")
        void searchInvalidTemplate() {
            state.setRegister(0, 0);
            state.setRegister(1, -1);  // Invalid template address
            state.setRegister(2, 3);
            memory.set(0, encode(SEARCH, 0, 1, 2, 3));
            
            cpu.execute(state, memory);
            
            assertEquals(-1, state.getRegister(3));
        }
    }
    
    // ========== System Calls ==========
    
    @Nested
    @DisplayName("System Calls")
    class SystemCalls {
        
        @Test
        @DisplayName("ALLOCATE with failing handler returns -1")
        void allocateFails() {
            state.setRegister(0, 50); // Request 50 cells
            memory.set(0, encode(ALLOCATE, 0, 1)); // Result in R1
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(SYSCALL_ALLOCATE_FAILED, result);
            assertEquals(-1, state.getRegister(1));
        }
        
        @Test
        @DisplayName("ALLOCATE with successful handler returns address")
        void allocateSucceeds() {
            SystemCallHandler handler = new SystemCallHandler() {
                @Override
                public int allocate(int requestedSize) {
                    return 500; // Allocated at address 500
                }
                
                @Override
                public boolean spawn(int address, int size, CpuState parentState) {
                    return false;
                }
            };
            
            VirtualCPU cpuWithHandler = new VirtualCPU(0.0, new Random(), handler);
            
            state.setRegister(0, 50);
            memory.set(0, encode(ALLOCATE, 0, 1));
            
            ExecutionResult result = cpuWithHandler.execute(state, memory);
            
            assertEquals(SYSCALL_ALLOCATE_OK, result);
            assertEquals(500, state.getRegister(1));
        }
        
        @Test
        @DisplayName("SPAWN with failing handler reports failure")
        void spawnFails() {
            state.setRegister(0, 100); // Address
            state.setRegister(1, 50);  // Size
            memory.set(0, encode(SPAWN, 0, 1));
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(SYSCALL_SPAWN_FAILED, result);
        }
    }
    
    // ========== Error Handling ==========
    
    @Nested
    @DisplayName("Error Handling")
    class ErrorHandling {
        
        @Test
        @DisplayName("IP out of bounds reports error")
        void ipOutOfBounds() {
            state.setIp(9999);
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(ERROR_IP_OUT_OF_BOUNDS, result);
            assertEquals(1, state.getErrors());
        }
        
        @Test
        @DisplayName("Unknown opcode reports error and advances IP")
        void unknownOpcode() {
            memory.set(0, 0xFF_000000); // Invalid opcode 0xFF
            
            ExecutionResult result = cpu.execute(state, memory);
            
            assertEquals(ERROR_UNKNOWN_OPCODE, result);
            assertEquals(1, state.getErrors());
            assertEquals(1, state.getIp());
        }
    }
    
    // ========== Integration Tests ==========
    
    @Nested
    @DisplayName("Integration Tests")
    class IntegrationTests {
        
        @Test
        @DisplayName("Simple loop executes correctly")
        void simpleLoop() {
            // Program: count down from 5 to 0
            // R0 = counter, R1 = loop address
            state.setRegister(0, 5);
            state.setRegister(1, 0); // Loop back to address 0
            
            memory.set(0, encode(DEC, 0));      // R0--
            memory.set(1, encode(JMPN, 0, 1));  // if R0 != 0, jump to R1
            memory.set(2, encode(NOP));         // End
            
            // Execute until program exits loop (IP reaches NOP at address 2)
            int maxCycles = 100;
            while (state.getIp() < 2 && maxCycles-- > 0) {
                cpu.execute(state, memory);
            }
            
            assertEquals(0, state.getRegister(0));
            assertEquals(2, state.getIp()); // Exited loop, pointing to NOP
            assertEquals(10, state.getAge()); // 5 iterations * 2 instructions
        }
        
        @Test
        @DisplayName("Memory copy loop works")
        void memoryCopyLoop() {
            // Source data at 100-104
            for (int i = 0; i < 5; i++) {
                memory.set(100 + i, 1000 + i);
            }
            
            // R0 = source pointer (100)
            // R1 = dest pointer (200)
            // R2 = counter (5)
            // R3 = loop address (0)
            // R4 = temp for loaded value
            state.setRegister(0, 100);
            state.setRegister(1, 200);
            state.setRegister(2, 5);
            state.setRegister(3, 0);
            
            // Copy loop using LOAD/STORE (not COPY, to avoid mutation concerns)
            memory.set(0, encode(LOAD, 4, 0));   // R4 = memory[R0]
            memory.set(1, encode(STORE, 1, 4));  // memory[R1] = R4
            memory.set(2, encode(INC, 0));       // R0++
            memory.set(3, encode(INC, 1));       // R1++
            memory.set(4, encode(DEC, 2));       // R2--
            memory.set(5, encode(JMPN, 2, 3));   // if R2 != 0, jump to R3
            
            // Execute loop
            int maxCycles = 100;
            while (state.getIp() < 6 && maxCycles-- > 0) {
                cpu.execute(state, memory);
            }
            
            // Verify copy
            for (int i = 0; i < 5; i++) {
                assertEquals(1000 + i, memory.get(200 + i));
            }
        }
    }
}

package io.github.manjago.proteus.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

class CpuStateTest {

    @Test
    @DisplayName("New state has all registers at zero")
    void newStateHasZeroRegisters() {
        CpuState state = new CpuState();
        
        for (int i = 0; i < CpuState.REGISTER_COUNT; i++) {
            assertEquals(0, state.getRegister(i));
        }
    }

    @Test
    @DisplayName("New state has IP at zero")
    void newStateHasZeroIp() {
        CpuState state = new CpuState();
        assertEquals(0, state.getIp());
    }

    @Test
    @DisplayName("Constructor with initial IP")
    void constructorWithInitialIp() {
        CpuState state = new CpuState(100);
        assertEquals(100, state.getIp());
    }

    @Test
    @DisplayName("Copy constructor creates independent copy")
    void copyConstructor() {
        CpuState original = new CpuState(50);
        original.setRegister(0, 42);
        original.incrementErrors();
        original.incrementAge();
        
        CpuState copy = new CpuState(original);
        
        // Verify copy has same values
        assertEquals(50, copy.getIp());
        assertEquals(42, copy.getRegister(0));
        assertEquals(1, copy.getErrors());
        assertEquals(1, copy.getAge());
        
        // Modify original, verify copy is independent
        original.setRegister(0, 100);
        original.setIp(200);
        
        assertEquals(42, copy.getRegister(0));
        assertEquals(50, copy.getIp());
    }

    @Test
    @DisplayName("Register index is masked to 0-7")
    void registerIndexMasked() {
        CpuState state = new CpuState();
        
        state.setRegister(8, 42);  // Should set R0 (8 & 7 = 0)
        assertEquals(42, state.getRegister(0));
        assertEquals(42, state.getRegister(8));
        
        state.setRegister(15, 99); // Should set R7 (15 & 7 = 7)
        assertEquals(99, state.getRegister(7));
    }

    @Test
    @DisplayName("advanceIp increments IP by 1")
    void advanceIp() {
        CpuState state = new CpuState(10);
        
        state.advanceIp();
        assertEquals(11, state.getIp());
        
        state.advanceIp();
        assertEquals(12, state.getIp());
    }

    @Test
    @DisplayName("Error counting works")
    void errorCounting() {
        CpuState state = new CpuState();
        
        assertEquals(0, state.getErrors());
        
        state.incrementErrors();
        assertEquals(1, state.getErrors());
        
        state.incrementErrors();
        assertEquals(2, state.getErrors());
        
        state.resetErrors();
        assertEquals(0, state.getErrors());
    }

    @Test
    @DisplayName("Age tracking works")
    void ageTracking() {
        CpuState state = new CpuState();
        
        assertEquals(0, state.getAge());
        
        state.incrementAge();
        assertEquals(1, state.getAge());
        
        for (int i = 0; i < 100; i++) {
            state.incrementAge();
        }
        assertEquals(101, state.getAge());
    }

    @Test
    @DisplayName("Reset clears everything")
    void resetClearsEverything() {
        CpuState state = new CpuState(100);
        state.setRegister(0, 42);
        state.setRegister(7, 99);
        state.incrementErrors();
        state.incrementAge();
        
        state.reset();
        
        assertEquals(0, state.getIp());
        assertEquals(0, state.getErrors());
        assertEquals(0, state.getAge());
        for (int i = 0; i < CpuState.REGISTER_COUNT; i++) {
            assertEquals(0, state.getRegister(i));
        }
    }

    @Test
    @DisplayName("clearRegisters only clears registers")
    void clearRegistersOnly() {
        CpuState state = new CpuState(100);
        state.setRegister(0, 42);
        state.incrementErrors();
        state.incrementAge();
        
        state.clearRegisters();
        
        // Registers cleared
        assertEquals(0, state.getRegister(0));
        
        // Other state preserved
        assertEquals(100, state.getIp());
        assertEquals(1, state.getErrors());
        assertEquals(1, state.getAge());
    }

    @Test
    @DisplayName("toString produces readable output")
    void toStringReadable() {
        CpuState state = new CpuState(0x1234);
        state.setRegister(0, 42);
        
        String str = state.toString();
        
        assertTrue(str.contains("1234")); // IP in hex
        assertTrue(str.contains("42"));   // R0 value
    }
}
